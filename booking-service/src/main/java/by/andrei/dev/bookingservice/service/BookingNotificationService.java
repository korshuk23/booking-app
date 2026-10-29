package by.andrei.dev.bookingservice.service;

import by.andrei.dev.bookingservice.config.RedisPubSubConfig;
import by.andrei.dev.bookingservice.dto.BookingNotificationDto;
import by.andrei.dev.bookingservice.entity.Booking;
import by.andrei.dev.bookingservice.repository.BookingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingNotificationService {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BookingRepository bookingRepository;

    @Value("${booking.sse.timeout-ms:60000}")
    private long sseTimeoutMs;

    @Value("${booking.sse.cache-ttl-minutes:10}")
    private long cacheTtlMinutes;

    @Value("${booking.sse.max-emitters:50000}")
    private long maxEmitters;

    private Cache<UUID, CopyOnWriteArraySet<SseEmitter>> emitterCache;

    @PostConstruct
    void initCache() {
        this.emitterCache = Caffeine.newBuilder()
                .maximumSize(maxEmitters)
                .expireAfterWrite(Duration.ofMinutes(cacheTtlMinutes))
                .removalListener((key, value, cause) -> {
                    if (cause.wasEvicted() && value != null) {
                        log.warn("SseEmitter for booking {} forcibly evicted from cache! Reason: {}", key, cause);
                        ((CopyOnWriteArraySet<SseEmitter>) value).forEach(SseEmitter::complete);
                    }
                })
                .build();
    }

    public SseEmitter subscribe(UUID bookingId, UUID requestingUserId) {
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        CopyOnWriteArraySet<SseEmitter> emitters = emitterCache.get(bookingId, k -> new CopyOnWriteArraySet<>());
        emitters.add(emitter);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> {
                    emitters.remove(emitter);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
                });

        Runnable removeCallback = () -> emitters.remove(emitter);
        emitter.onCompletion(removeCallback);
        emitter.onTimeout(removeCallback);
        emitter.onError(e -> removeCallback.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(Map.of(
                            "bookingId", bookingId,
                            "status", booking.getStatus().name(),
                            "message", "Subscribed to real-time updates"
                    )));

            if (booking.getStatus() == Booking.BookingStatus.CONFIRMED ||
                    booking.getStatus() == Booking.BookingStatus.CANCELLED ||
                    booking.getStatus() == Booking.BookingStatus.REJECTED) {
                emitter.complete();
            }
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void sendBookingUpdateAfterCommit(UUID bookingId, String status, String message) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcastToRedis(bookingId, status, message);
                }
            });
        } else {
            broadcastToRedis(bookingId, status, message);
        }
    }

    private void broadcastToRedis(UUID bookingId, String status, String message) {
        try {
            BookingNotificationDto payload = BookingNotificationDto.builder()
                    .bookingId(bookingId)
                    .status(status)
                    .message(message != null ? message : "")
                    .build();

            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.convertAndSend(RedisPubSubConfig.BOOKING_STATUS_TOPIC, json);
        } catch (Exception e) {
            log.error("Failed to broadcast SSE update to Redis for booking: {}", bookingId, e);
        }
    }

    public void handleClusterNotification(String messageJson) {
        try {
            BookingNotificationDto dto = objectMapper.readValue(messageJson, BookingNotificationDto.class);
            CopyOnWriteArraySet<SseEmitter> emitters = emitterCache.getIfPresent(dto.getBookingId());

            if (emitters != null) {
                for (SseEmitter emitter : emitters) {
                    try {
                        emitter.send(SseEmitter.event().name("STATUS_UPDATE").data(dto));
                        if (isTerminalStatus(dto.getStatus())) emitter.complete();
                    } catch (Exception ignored) {
                        emitters.remove(emitter);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing cluster notification from Redis", e);
        }
    }

    private boolean isTerminalStatus(String status) {
        return "CONFIRMED".equals(status) || "CANCELLED".equals(status) || "REJECTED".equals(status);
    }

    @Scheduled(fixedRateString = "${booking.sse.heartbeat-rate-ms:15000}")
    public void sendHeartbeat() {
        if (emitterCache == null || emitterCache.estimatedSize() == 0) {
            return;
        }

        if (emitterCache == null) return;
        emitterCache.asMap().forEach((bookingId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception ex) {
                    emitters.remove(emitter);
                }
            }
        });
    }

    public long getActiveConnectionsCount() {
        return emitterCache != null ? emitterCache.estimatedSize() : 0;
    }
}

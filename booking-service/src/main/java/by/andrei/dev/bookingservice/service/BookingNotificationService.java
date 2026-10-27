package by.andrei.dev.bookingservice.service;

import by.andrei.dev.bookingservice.config.RedisPubSubConfig;
import by.andrei.dev.bookingservice.dto.BookingNotificationDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingNotificationService {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${booking.sse.timeout-ms:60000}")
    private long sseTimeoutMs;

    @Value("${booking.sse.cache-ttl-minutes:10}")
    private long cacheTtlMinutes;

    @Value("${booking.sse.max-emitters:50000}")
    private long maxEmitters;

    private Cache<UUID, SseEmitter> emitterCache;

    @PostConstruct
    void initCache() {
        this.emitterCache = Caffeine.newBuilder()
                .maximumSize(maxEmitters)
                .expireAfterWrite(Duration.ofMinutes(cacheTtlMinutes))
                .removalListener((UUID key, SseEmitter emitter, RemovalCause cause) -> {
                    if (cause.wasEvicted() && emitter != null) {
                        log.warn("SseEmitter for booking {} forcibly evicted from cache! Reason: {}", key, cause);
                        try {
                            emitter.complete();
                        } catch (Exception ignored) {
                        }
                    }
                })
                .build();
    }

    public SseEmitter subscribe(UUID bookingId) {
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);

        emitter.onCompletion(() -> {
            log.debug("SSE connection completed for booking: {}", bookingId);
            emitterCache.invalidate(bookingId);
        });

        emitter.onTimeout(() -> {
            log.warn("SSE connection timed out for booking: {}", bookingId);
            emitter.complete();
            emitterCache.invalidate(bookingId);
        });

        emitter.onError(e -> {
            log.debug("SSE network connection terminated/error for booking {}: {}", bookingId, e.getMessage());
            emitterCache.invalidate(bookingId);
        });

        emitterCache.put(bookingId, emitter);

        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(Map.of(
                            "bookingId", bookingId,
                            "status", "PENDING",
                            "message", "Subscribed to booking updates"
                    )));
        } catch (IOException e) {
            log.error("Failed to send INIT SSE event for booking {}", bookingId, e);
            emitterCache.invalidate(bookingId);
        }

        return emitter;
    }

    public void sendBookingUpdate(UUID bookingId, String status, String message) {
        SseEmitter emitter = emitterCache.getIfPresent(bookingId);
        if (emitter == null) {
            log.debug("No active SSE subscriber for booking {}", bookingId);
            return;
        }

        try {
            BookingNotificationDto payload = BookingNotificationDto.builder()
                    .bookingId(bookingId)
                    .status(status)
                    .message(message != null ? message : "")
                    .build();

            String json = objectMapper.writeValueAsString(payload);
            log.info("Broadcasting booking update to Redis Pub/Sub for booking {}", bookingId);
            redisTemplate.convertAndSend(RedisPubSubConfig.BOOKING_STATUS_TOPIC, json);
        } catch (Exception e) {
            log.error("Failed to broadcast SSE update via Redis for booking: {}", bookingId, e);
        }
    }

    public void handleClusterNotification(String messageJson) {
        try {
            BookingNotificationDto dto = objectMapper.readValue(messageJson, BookingNotificationDto.class);

            SseEmitter emitter = emitterCache.getIfPresent(dto.getBookingId());
            if (emitter != null) {
                log.info("Match found! Pushing SSE to client on this instance for booking {}", dto.getBookingId());
                emitter.send(SseEmitter.event()
                        .name("STATUS_UPDATE")
                        .data(Map.of(
                                "bookingId", dto.getBookingId(),
                                "status", dto.getStatus(),
                                "message", dto.getMessage()
                        )));
                emitter.complete();
                emitterCache.invalidate(dto.getBookingId());
            }
        } catch (Exception e) {
            log.error("Error processing cluster notification from Redis", e);
        }
    }

    @Scheduled(fixedRateString = "${booking.sse.heartbeat-rate-ms:15000}")
    public void sendHeartbeat() {
        if (emitterCache == null || emitterCache.estimatedSize() == 0) {
            return;
        }

        log.trace("Broadcasting SSE heartbeat to {} active client(s)", emitterCache.estimatedSize());

        emitterCache.asMap().forEach((bookingId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception ex) {
                log.debug("Heartbeat detected closed connection for booking {}. Removing.", bookingId);
                emitterCache.invalidate(bookingId);
            }
        });
    }

    public long getActiveConnectionsCount() {
        return emitterCache != null ? emitterCache.estimatedSize() : 0;
    }
}

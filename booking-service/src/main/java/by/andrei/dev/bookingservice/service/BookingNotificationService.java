package by.andrei.dev.bookingservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class BookingNotificationService {
    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID bookingId) {
        SseEmitter emitter = new SseEmitter(60_000L);

        emitters.put(bookingId, emitter);

        emitter.onCompletion(() -> {
            log.info("SSE connection completed for booking: {}", bookingId);
            emitters.remove(bookingId);
        });

        emitter.onTimeout(() -> {
            log.warn("SSE connection timed out for booking: {}", bookingId);
            emitters.remove(bookingId);
        });

        emitter.onError(e -> {
            log.error("SSE connection error for booking {}: {}", bookingId, e.getMessage());
            emitters.remove(bookingId);
        });

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
            emitters.remove(bookingId);
        }

        return emitter;
    }

    public void sendBookingUpdate(UUID bookingId, String status, String message) {
        SseEmitter emitter = emitters.get(bookingId);
        if (emitter == null) {
            log.debug("No active SSE subscriber for booking {}", bookingId);
            return;
        }

        try {
            log.info("Pushing SSE update for booking {}: status={}", bookingId, status);
            emitter.send(SseEmitter.event()
                    .name("STATUS_UPDATE")
                    .data(Map.of(
                            "bookingId", bookingId,
                            "status", status,
                            "message", message != null ? message : ""
                    )));

            emitter.complete();
        } catch (Exception e) {
            log.error("Failed to push SSE to client for booking {}", bookingId, e);
            emitters.remove(bookingId);
        }
    }
}

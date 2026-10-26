package by.andrei.dev.bookingservice.producer;

import by.andrei.dev.bookingservice.entity.OutboxEvent;
import by.andrei.dev.bookingservice.repository.OutboxRepository;
import by.andrei.dev.core.dto.BookingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.beans.Transient;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final String TOPIC = "booking-events";
    private static final int KAFKA_ACK_TIMEOUT_SECONDS = 5;

    @Scheduled(fixedDelayString = "${outbox.publisher.delay:1000}")
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findPendingEventsForProcessing();
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events to publish", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                BookingEvent bookingEvent = objectMapper.readValue(event.getPayload(), BookingEvent.class);

                kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), bookingEvent)
                        .get(KAFKA_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                markEventAsSent(event.getId());
                log.info("Successfully published outbox event {} to Kafka topic {}", event.getId(), TOPIC);
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}. Will retry on next tick.", event.getId(), e);
            }
        }
    }

    private void markEventAsSent(UUID eventId) {
        outboxRepository.findById(eventId).ifPresent(ev -> {
            ev.setStatus("SENT");
            ev.setSentAt(LocalDateTime.now());
            outboxRepository.save(ev);
        });
    }
}

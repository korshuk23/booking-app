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
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;


    private static final String TOPIC = "booking-events";
    private static final int KAFKA_ACK_TIMEOUT_SECONDS = 5;

    @Scheduled(fixedDelayString = "${outbox.publisher.delay:1000}")
    public void publishPendingEvents() {
        List<OutboxEvent> eventsToPublish = transactionTemplate.execute(status -> {
            List<OutboxEvent> pending = outboxRepository.findPendingEventsForProcessing();
            for (OutboxEvent event : pending) {
                event.setStatus("PROCESSING");
            }
            return pending;
        });

        if (eventsToPublish == null || eventsToPublish.isEmpty()) {
            return;
        }

        log.debug("Locked and processing {} outbox events", eventsToPublish.size());

        for (OutboxEvent event : eventsToPublish) {
            try {
                BookingEvent bookingEvent = objectMapper.readValue(event.getPayload(), BookingEvent.class);

                kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), bookingEvent)
                        .get(KAFKA_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                markEventStatus(event.getId(), "SENT");
                log.info("Successfully published outbox event {} to Kafka topic {}", event.getId(), TOPIC);
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}. Rolling back to PENDING. Halting batch.", event.getId(), e);
                markEventStatus(event.getId(), "PENDING");
                break;
            }
        }
    }

    private void markEventStatus(UUID eventId, String status) {
        transactionTemplate.executeWithoutResult(txStatus -> {
            outboxRepository.findById(eventId).ifPresent(ev -> {
                ev.setStatus(status);
                if ("SENT".equals(status)) {
                    ev.setSentAt(LocalDateTime.now());
                }
                outboxRepository.save(ev);
            });
        });
    }
}

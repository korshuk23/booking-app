package by.andrei.dev.movieservice.producer;

import by.andrei.dev.core.dto.BookingEvent;
import by.andrei.dev.movieservice.entity.OutboxEvent;
import by.andrei.dev.movieservice.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final String TOPIC = "booking-events";

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findPendingEventsForProcessing();
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events to publish", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                BookingEvent bookingEvent = objectMapper.readValue(event.getPayload(), BookingEvent.class);
                kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), bookingEvent);
                event.setStatus("SENT");
                event.setSentAt(LocalDateTime.now());
                outboxRepository.save(event);
                log.info("Successfully published outbox event {} to Kafka topic {}", event.getId(), TOPIC);
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}. Will retry on next tick.", event.getId(), e);
            }
        }
    }
}

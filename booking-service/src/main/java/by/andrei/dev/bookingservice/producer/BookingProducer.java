package by.andrei.dev.bookingservice.producer;

import by.andrei.dev.bookingservice.dto.BookingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String TOPIC = "booking-events";

    public void sendBookingEvent(BookingEvent event) {
        log.info("==> Sending event to Kafka: {}", event);
        kafkaTemplate.send(TOPIC, event);
        log.info("[Sended...]");
    }
}

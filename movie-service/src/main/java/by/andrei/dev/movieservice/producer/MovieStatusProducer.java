package by.andrei.dev.movieservice.producer;

import by.andrei.dev.core.dto.BookingStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MovieStatusProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "booking-status-events";


    public void sendStatus(UUID bookingId, UUID userId, UUID sessionId, BigDecimal price, String status, String message) {
        BookingStatusEvent event = BookingStatusEvent.builder()
                .bookingId(bookingId)
                .userId(userId)
                .sessionId(sessionId)
                .price(price)
                .status(status)
                .message(message)
                .build();

        log.info(" ==> Sending status {} for booking {}", status, bookingId);
        kafkaTemplate.send(TOPIC, bookingId.toString(), event);
    }
}

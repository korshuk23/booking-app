package by.andrei.dev.paymentservice.consumer;

import by.andrei.dev.core.dto.BookingStatusEvent;
import by.andrei.dev.core.dto.PaymentEvent;
import by.andrei.dev.paymentservice.entity.OutboxEvent;
import by.andrei.dev.paymentservice.entity.Payment;
import by.andrei.dev.paymentservice.repository.OutboxRepository;
import by.andrei.dev.paymentservice.repository.PaymentRepository;
import by.andrei.dev.paymentservice.service.PaymentGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SeatReservedConsumer {

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final PaymentGateway paymentGateway;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "booking-status-events", groupId = "payment-group")
    @Transactional
    public void handleSeatReserved(BookingStatusEvent event) {
        if (!"SEATS_RESERVED".equals(event.getStatus())) {
            return;
        }

        log.info("Processing payment for bookingId: {}, amount: {}", event.getBookingId(), event.getPrice());

        if (paymentRepository.findByBookingId(event.getBookingId()).isPresent()) {
            log.warn("Payment for booking {} already processed", event.getBookingId());
            return;
        }

        boolean isSuccess = paymentGateway.processPayment(event.getPrice());

        Payment payment = Payment.builder()
                .bookingId(event.getBookingId())
                .userId(event.getUserId())
                .amount(event.getPrice())
                .status(isSuccess ? "SUCCESS" : "FAILED")
                .failureReason(isSuccess ? null : "Declined by bank or insufficient funds")
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        PaymentEvent paymentEvent = PaymentEvent.builder()
                .paymentId(savedPayment.getId())
                .bookingId(savedPayment.getBookingId())
                .userId(savedPayment.getUserId())
                .sessionId(event.getSessionId())
                .seatId(event.getSeatId())
                .amount(savedPayment.getAmount())
                .status(savedPayment.getStatus())
                .failureReason(savedPayment.getFailureReason())
                .build();
        try {
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("PAYMENT")
                    .aggregateId(savedPayment.getBookingId()) // Ключ для партиционирования Kafka
                    .eventType("PAYMENT_" + savedPayment.getStatus())
                    .payload(objectMapper.writeValueAsString(paymentEvent))
                    .build();

            outboxRepository.save(outboxEvent);
            log.info("Payment result {} saved to Outbox for booking {}", paymentEvent.getStatus(), event.getBookingId());
        } catch (Exception e) {
            log.error("Failed to serialize payment event for booking {}", event.getBookingId(), e);
            throw new RuntimeException(e);
        }
    }
}
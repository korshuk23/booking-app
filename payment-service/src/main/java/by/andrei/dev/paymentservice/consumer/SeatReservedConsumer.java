package by.andrei.dev.paymentservice.consumer;

import by.andrei.dev.core.dto.BookingStatusEvent;
import by.andrei.dev.core.dto.PaymentEvent;
import by.andrei.dev.paymentservice.entity.Payment;
import by.andrei.dev.paymentservice.repository.PaymentRepository;
import by.andrei.dev.paymentservice.service.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SeatReservedConsumer {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String PAYMENT_TOPIC = "payment-events";

    @KafkaListener(topics = "booking-status-events", groupId = "payment-group")
    @Transactional
    public void handleSeatReserved(BookingStatusEvent event) {
        // Реагируем только на успешную резервацию места
        if (!"SEATS_RESERVED".equals(event.getStatus())) {
            return;
        }

        log.info("Processing payment for bookingId: {}, amount: {}", event.getBookingId(), event.getPrice());

        // Идемпотентность
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
                .amount(savedPayment.getAmount())
                .status(savedPayment.getStatus())
                .failureReason(savedPayment.getFailureReason())
                .build();

        kafkaTemplate.send(PAYMENT_TOPIC, event.getBookingId().toString(), paymentEvent);
        log.info("Payment result {} sent to topic {}", paymentEvent.getStatus(), PAYMENT_TOPIC);
    }
}
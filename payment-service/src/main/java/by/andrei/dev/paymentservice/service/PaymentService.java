package by.andrei.dev.paymentservice.service;

import by.andrei.dev.core.dto.BookingStatusEvent;
import by.andrei.dev.core.dto.PaymentEvent;
import by.andrei.dev.paymentservice.entity.OutboxEvent;
import by.andrei.dev.paymentservice.entity.Payment;
import by.andrei.dev.paymentservice.repository.OutboxRepository;
import by.andrei.dev.paymentservice.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final PaymentGateway paymentGateway;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    public void processPaymentEvent(BookingStatusEvent event) {
        Payment payment = txTemplate.execute(status -> {
            var existingPaymentOpt = paymentRepository.findByBookingId(event.getBookingId());
            boolean isExpired = event.getExpiresAt() != null && event.getExpiresAt().isBefore(LocalDateTime.now().plusSeconds(30));

            if (existingPaymentOpt.isPresent()) {
                Payment existing = existingPaymentOpt.get();
                if ("PENDING".equals(existing.getStatus())) {
                    if (isExpired) {
                        log.warn("Pending payment for booking {} expired. Failing.", event.getBookingId());
                        existing.setStatus("FAILED");
                        existing.setFailureReason("Reservation expired during payment retry");
                        saveOutboxEvent(existing, event);
                        return null;
                    }
                    log.info("Resuming stuck PENDING payment for booking {}", event.getBookingId());
                    return existing;
                }
                log.warn("Payment for booking {} already final. Skipping.", event.getBookingId());
                return null;
            }

            Payment newPayment = Payment.builder()
                    .bookingId(event.getBookingId())
                    .userId(event.getUserId())
                    .amount(event.getPrice())
                    .status(isExpired ? "FAILED" : "PENDING")
                    .failureReason(isExpired ? "Reservation expired before payment started" : null)
                    .build();

            paymentRepository.save(newPayment);

            if (isExpired) {
                log.warn("Reservation {} expired. Failing payment immediately.", event.getBookingId());
                saveOutboxEvent(newPayment, event);
            }

            return newPayment;
        });

        if (payment == null || !"PENDING".equals(payment.getStatus())) {
            return;
        }

        log.info("Calling PSP for booking {}...", event.getBookingId());
        boolean isSuccess = paymentGateway.processPayment(payment.getAmount());

        String finalStatus = isSuccess ? "SUCCESS" : "FAILED";
        String failureReason = isSuccess ? null : "Declined by bank or insufficient funds";

        txTemplate.executeWithoutResult(status -> {
            Payment p = paymentRepository.findById(payment.getId())
                    .orElseThrow(() -> new IllegalStateException("Payment disappeared: " + payment.getId()));

            p.setStatus(finalStatus);
            p.setFailureReason(failureReason);

            saveOutboxEvent(p, event);
            log.info("Payment {} completed with status {}", p.getId(), finalStatus);
        });
    }

    private void saveOutboxEvent(Payment payment, BookingStatusEvent eventContext) {
        try {
            PaymentEvent paymentEvent = PaymentEvent.builder()
                    .paymentId(payment.getId())
                    .bookingId(payment.getBookingId())
                    .userId(payment.getUserId())
                    .sessionId(eventContext.getSessionId())
                    .seatId(eventContext.getSeatId())
                    .amount(payment.getAmount())
                    .status(payment.getStatus())
                    .failureReason(payment.getFailureReason())
                    .build();

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("PAYMENT")
                    .aggregateId(payment.getBookingId())
                    .eventType("PAYMENT_" + payment.getStatus())
                    .payload(objectMapper.writeValueAsString(paymentEvent))
                    .build();

            outboxRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to serialize outbox event for payment {}", payment.getId(), e);
            throw new RuntimeException("Serialization failed", e);
        }
    }
}
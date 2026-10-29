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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
                        saveOutboxEvent(existing);
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
                    .sessionId(event.getSessionId())
                    .seatId(event.getSeatId())
                    .amount(event.getPrice())
                    .status(isExpired ? "FAILED" : "PENDING")
                    .failureReason(isExpired ? "Reservation expired before payment started" : null)
                    .build();

            paymentRepository.save(newPayment);

            if (isExpired) {
                log.warn("Reservation {} expired. Failing payment immediately.", event.getBookingId());
                saveOutboxEvent(newPayment);
            }
            return newPayment;
        });

        if (payment == null || !"PENDING".equals(payment.getStatus())) return;

        log.info("Calling PSP for booking {}...", event.getBookingId());

        UUID chargeKey = UUID.nameUUIDFromBytes(("charge:" + payment.getId()).getBytes(StandardCharsets.UTF_8));
        boolean isSuccess = paymentGateway.charge(payment.getAmount(), chargeKey);

        String finalStatus = isSuccess ? "SUCCESS" : "FAILED";
        String failureReason = isSuccess ? null : "Declined by bank or insufficient funds";

        txTemplate.executeWithoutResult(status -> {
            Payment p = paymentRepository.findById(payment.getId())
                    .orElseThrow(() -> new IllegalStateException("Payment disappeared: " + payment.getId()));
            p.setStatus(finalStatus);
            p.setFailureReason(failureReason);
            saveOutboxEvent(p);
            log.info("Payment {} completed with status {}", p.getId(), finalStatus);
        });
    }

    public void refundPayment(UUID bookingId) {
        Payment payment = txTemplate.execute(status -> {
            Payment p = paymentRepository.findByBookingId(bookingId)
                    .orElseThrow(() -> new IllegalStateException("Payment not found for booking: " + bookingId));

            if (!"SUCCESS".equals(p.getStatus())) {
                log.warn("Cannot refund payment {} with status {}", p.getId(), p.getStatus());
                return null;
            }
            p.setStatus("REFUND_PENDING");
            return paymentRepository.save(p);
        });

        if (payment == null) return;

        log.info("Calling PSP for refund on payment {}", payment.getId());

        UUID refundKey = UUID.nameUUIDFromBytes(("refund:" + payment.getId()).getBytes(StandardCharsets.UTF_8));
        boolean refundSuccess = paymentGateway.refund(payment.getAmount(), refundKey);

        txTemplate.executeWithoutResult(status -> {
            Payment p = paymentRepository.findById(payment.getId()).orElseThrow();
            if (refundSuccess) {
                p.setStatus("REFUNDED");
                saveOutboxEvent(p);
                log.info("Refund successful for payment {}", p.getId());
            } else {
                p.setStatus("REFUND_FAILED");
                p.setFailureReason("PSP declined refund");
                log.error("CRITICAL: PSP refused refund for payment {}. Flagged as REFUND_FAILED", p.getId());
            }
            paymentRepository.save(p);
        });
    }

    @Scheduled(fixedDelay = 60000)
    public void reconcilePendingPayments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(2);

        txTemplate.executeWithoutResult(txStatus -> {
            List<Payment> stuckPayments = paymentRepository.findStuckPendingForUpdate(threshold);
            if (stuckPayments.isEmpty()) return;

            for (Payment p : stuckPayments) {
                log.warn("Reconciling stuck payment: {}", p.getId());
                UUID chargeKey = UUID.nameUUIDFromBytes(("charge:" + p.getId()).getBytes(StandardCharsets.UTF_8));
                String pspStatus = paymentGateway.checkStatus(chargeKey);

                if ("SUCCESS".equals(pspStatus) || "FAILED".equals(pspStatus)) {
                    p.setStatus(pspStatus);
                    if ("FAILED".equals(pspStatus)) {
                        p.setFailureReason("Failed on PSP side during reconciliation");
                    }
                    paymentRepository.save(p);
                    saveOutboxEvent(p);
                    log.info("Payment {} reconciled to {}", p.getId(), pspStatus);
                } else {
                    log.debug("PSP reports payment {} is still pending/processing. Will retry later.", p.getId());
                }
            }
        });
    }

    private void saveOutboxEvent(Payment payment) {
        try {
            PaymentEvent paymentEvent = PaymentEvent.builder()
                    .paymentId(payment.getId())
                    .bookingId(payment.getBookingId())
                    .userId(payment.getUserId())
                    .sessionId(payment.getSessionId())
                    .seatId(payment.getSeatId())
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
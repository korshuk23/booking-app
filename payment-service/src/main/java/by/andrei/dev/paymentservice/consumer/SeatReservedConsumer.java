package by.andrei.dev.paymentservice.consumer;

import by.andrei.dev.core.dto.BookingStatusEvent;
import by.andrei.dev.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SeatReservedConsumer {

    private final PaymentService paymentService;

    @KafkaListener(topics = "booking-status-events", groupId = "payment-group")
    public void handleSeatReserved(BookingStatusEvent event) {

        if ("ORPHANED_PAYMENT".equals(event.getStatus())) {
            log.warn("Initiating REFUND for orphaned payment on booking: {}", event.getBookingId());
            // TODO: paymentService.refundPayment(event.getBookingId());
            return;
        }

        if ("SEATS_RESERVED".equals(event.getStatus())) {
            paymentService.processPaymentEvent(event);
        }
    }
}
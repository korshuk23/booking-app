package by.andrei.dev.bookingservice.consumer;

import by.andrei.dev.bookingservice.repository.BookingRepository;
import by.andrei.dev.bookingservice.service.BookingNotificationService;
import by.andrei.dev.core.dto.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentStatusConsumer {

    private final BookingRepository bookingRepository;
    private final BookingNotificationService notificationService;

    @KafkaListener(topics = "payment-events", groupId = "booking-payment-group")
    @Transactional
    public void handlePaymentStatus(PaymentEvent event) {
        log.info("Received payment status for booking {}: {}", event.getBookingId(), event.getStatus());

        bookingRepository.findById(event.getBookingId()).ifPresent(booking -> {
            if ("SUCCESS".equals(event.getStatus())) {
                booking.setStatus("CONFIRMED");
            } else {
                booking.setStatus("CANCELLED");
            }
            bookingRepository.save(booking);
            log.info("Booking {} status finalized to {}", booking.getId(), booking.getStatus());

            notificationService.sendBookingUpdate(
                    booking.getId(),
                    booking.getStatus(),
                    "SUCCESS".equals(event.getStatus()) ? "Payment confirmed! Ticket issued." : event.getFailureReason()
            );
        });
    }
}
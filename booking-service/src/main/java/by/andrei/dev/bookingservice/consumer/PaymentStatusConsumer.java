package by.andrei.dev.bookingservice.consumer;

import by.andrei.dev.bookingservice.entity.Booking;
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
        if (!"FAILED".equals(event.getStatus())) {
            log.info("Payment {} for booking {}: waiting for movie-service confirmation", event.getStatus(), event.getBookingId());
            return;
        }
        bookingRepository.findById(event.getBookingId()).ifPresentOrElse(booking -> {
            try {
                booking.transitionTo(Booking.BookingStatus.CANCELLED);
                bookingRepository.save(booking);
                notificationService.sendBookingUpdateAfterCommit(
                        booking.getId(), "CANCELLED", event.getFailureReason());
            } catch (IllegalStateException ex) {
                log.warn("Ignored out-of-order payment event for booking {}: {}", event.getBookingId(), ex.getMessage());
            }
        }, () -> log.error("Booking not found for payment event: {}", event.getBookingId()));
    }
}
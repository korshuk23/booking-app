package by.andrei.dev.bookingservice.consumer;

import by.andrei.dev.bookingservice.entity.Booking;
import by.andrei.dev.bookingservice.repository.BookingRepository;
import by.andrei.dev.bookingservice.service.BookingNotificationService;
import by.andrei.dev.core.dto.BookingStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class MovieStatusConsumer {
    private final BookingRepository bookingRepository;
    private final BookingNotificationService notificationService;

    @KafkaListener(topics = "booking-status-events", groupId = "booking-group")
    @Transactional
    public void handleStatusUpdate(BookingStatusEvent event) {
        log.info("Received movie status update for booking {}: {}", event.getBookingId(), event.getStatus());

        bookingRepository.findById(event.getBookingId()).ifPresentOrElse(booking -> {
            try {
                Booking.BookingStatus targetStatus = Booking.BookingStatus.valueOf(event.getStatus());

                booking.transitionTo(targetStatus);
                bookingRepository.save(booking);

                log.info("Booking {} transitioned to {}", booking.getId(), booking.getStatus());

                if (targetStatus == Booking.BookingStatus.REJECTED || targetStatus == Booking.BookingStatus.CANCELLED) {
                    notificationService.sendBookingUpdate(
                            booking.getId(),
                            targetStatus.name(),
                            event.getMessage() != null ? event.getMessage() : "Booking rejected by cinema"
                    );
                }
            } catch (IllegalArgumentException ex) {
                log.error("Unknown booking status received from Kafka: {}", event.getStatus());
            } catch (IllegalStateException ex) {
                log.warn("Ignored conflicting movie status event for booking {}: {}", event.getBookingId(), ex.getMessage());
            }
        }, () -> log.error("Booking not found for movie status event: {}", event.getBookingId()));
    }
}

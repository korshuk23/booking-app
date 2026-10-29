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
                Booking.BookingStatus before = booking.getStatus();
                Booking.BookingStatus target;

                switch (event.getStatus()) {
                    case "ORPHANED_PAYMENT":
                    case "CANCELLED":
                    case "REJECTED":
                        target = Booking.BookingStatus.CANCELLED;
                        break;
                    case "SEAT_BOOKED":
                    case "CONFIRMED":
                        target = Booking.BookingStatus.CONFIRMED;
                        break;
                    default:
                        target = Booking.BookingStatus.valueOf(event.getStatus());
                }

                booking.transitionTo(target);
                bookingRepository.save(booking);

                if (booking.getStatus() != before) {
                    notificationService.sendBookingUpdateAfterCommit(
                            booking.getId(), target.name(), event.getMessage() != null ? event.getMessage() : "Status updated"
                    );
                }
            } catch (IllegalArgumentException ex) {
                log.error("Unknown booking status received from Kafka: {}", event.getStatus());
            } catch (IllegalStateException ex) {
                log.warn("Ignored conflicting movie status event for booking {}", event.getBookingId());
            }
        }, () -> log.error("Booking not found: {}", event.getBookingId()));
    }
}

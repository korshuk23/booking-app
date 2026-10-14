package by.andrei.dev.bookingservice.consumer;

import by.andrei.dev.bookingservice.repository.BookingRepository;
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

    @KafkaListener(topics = "booking-status-events", groupId = "booking-group")
    @Transactional
    public void handleStatusUpdate(BookingStatusEvent event) {
        log.info("==> Received status update for booking {}: {}", event.getBookingId(), event.getStatus());

        bookingRepository.findById(event.getBookingId()).ifPresent(booking -> {
            booking.setStatus(event.getStatus());
            bookingRepository.save(booking);
            log.info("Booking {} updated to {}", event.getBookingId(), event.getStatus());
        });
    }
}

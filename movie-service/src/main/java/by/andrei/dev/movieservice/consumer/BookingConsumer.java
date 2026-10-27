package by.andrei.dev.movieservice.consumer;

import by.andrei.dev.core.dto.BookingEvent;
import by.andrei.dev.core.dto.PaymentEvent;
import by.andrei.dev.movieservice.repository.MovieSessionRepository;
import by.andrei.dev.movieservice.repository.ProcessedBookingRepository;
import by.andrei.dev.movieservice.repository.SeatRepository;
import by.andrei.dev.movieservice.service.SeatReservationCoordinator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingConsumer {
    private final MovieSessionRepository movieSessionRepository;
    private final ProcessedBookingRepository processedBookingRepository;
    private final SeatReservationCoordinator seatReservationCoordinator;
    private final SeatRepository seatRepository;

    @KafkaListener(topics = "booking-events", groupId = "movie-group-v2")
    public void consume(BookingEvent event) {
        log.info("Processing booking event: bookingId={}, seatId={}", event.getBookingId(), event.getSeatId());

        if (processedBookingRepository.existsById(event.getBookingId())) {
            log.warn("Booking {} already processed, skipping", event.getBookingId());
            return;
        }

        seatReservationCoordinator.processReservationWithLock(event);
    }

    @KafkaListener(topics = "payment-events", groupId = "movie-compensation-group")
    @Transactional
    public void handlePaymentCompensation(PaymentEvent event) {
        if (event.getSeatId() == null) {
            log.warn("Received payment event without seatId for booking: {}", event.getBookingId());
            return;
        }

        if ("FAILED".equals(event.getStatus())) {
            log.warn("Payment failed for booking {}. Releasing seat {} and restoring capacity",
                    event.getBookingId(), event.getSeatId());

            int releasedRows = seatRepository.releaseSeatIfReservedByBooking(event.getSeatId(), event.getBookingId());

            if (releasedRows > 0) {
                movieSessionRepository.incrementAvailableSeats(event.getSessionId());
                log.info("Seat {} successfully released and session quota restored for booking {}",
                        event.getSeatId(), event.getBookingId());
            } else {
                log.warn("Ignored seat release: seat {} was not reserved by booking {}",
                        event.getSeatId(), event.getBookingId());
            }

        } else if ("SUCCESS".equals(event.getStatus())) {
            int finalizedRows = seatRepository.finalizeSeatToBooked(event.getSeatId(), event.getBookingId());
            log.info("Seat {} finalized to BOOKED for booking {}. Rows affected: {}",
                    event.getSeatId(), event.getBookingId(), finalizedRows);
        }
    }
}


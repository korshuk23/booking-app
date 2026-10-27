package by.andrei.dev.movieservice.consumer;

import by.andrei.dev.core.dto.BookingEvent;
import by.andrei.dev.core.dto.PaymentEvent;
import by.andrei.dev.movieservice.entity.Seat;
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

            seatRepository.findById(event.getSeatId()).ifPresent(seat -> {
                seat.setStatus(Seat.SeatStatus.FREE);
                seat.setBookingId(null);
                seat.setReservedAt(null);
                seatRepository.save(seat);

                movieSessionRepository.incrementAvailableSeats(seat.getSessionId());
                log.info("Capacity restored for session {} after payment failure", seat.getSessionId());
            });
        } else if ("SUCCESS".equals(event.getStatus())) {
            log.info("Payment confirmed for booking {}. Finalizing seat {} to BOOKED",
                    event.getBookingId(), event.getSeatId());
            seatRepository.findById(event.getSeatId()).ifPresent(seat -> {
                seat.setStatus(Seat.SeatStatus.BOOKED);
                seatRepository.save(seat);
            });
        }
    }
}


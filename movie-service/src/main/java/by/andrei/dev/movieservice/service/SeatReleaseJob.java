package by.andrei.dev.movieservice.service;

import by.andrei.dev.core.dto.BookingStatusEvent;
import by.andrei.dev.movieservice.entity.OutboxEvent;
import by.andrei.dev.movieservice.entity.Seat;
import by.andrei.dev.movieservice.repository.MovieSessionRepository;
import by.andrei.dev.movieservice.repository.OutboxRepository;
import by.andrei.dev.movieservice.repository.SeatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatReleaseJob {

    private final SeatRepository seatRepository;
    private final MovieSessionRepository movieSessionRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${seat.reservation.timeout-minutes:15}")
    private long timeoutMinutes;

    @Scheduled(fixedRateString = "${seat.release.job-rate-ms:60000}")
    @Transactional
    public void releaseExpiredReservations() {
        List<Seat> expiredSeats = seatRepository.releaseExpired(LocalDateTime.now());

        if (expiredSeats.isEmpty()) {
            return;
        }

        log.info("Found {} expired seat reservations. Releasing...", expiredSeats.size());

        for (Seat seat : expiredSeats) {
            movieSessionRepository.incrementAvailableSeats(seat.getSessionId());

            if (seat.getBookingId() != null) {
                emitCancellationOutboxEvent(seat.getBookingId(), seat);
            }
        }
    }

    private void emitCancellationOutboxEvent(UUID bookingId, Seat seat) {
        try {
            BookingStatusEvent event = BookingStatusEvent.builder()
                    .bookingId(bookingId)
                    .sessionId(seat.getSessionId())
                    .seatId(seat.getId())
                    .status("CANCELLED")
                    .message("Reservation expired due to payment timeout")
                    .build();

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("BOOKING_STATUS")
                    .aggregateId(bookingId)
                    .eventType("BOOKING_CANCELLED")
                    .payload(objectMapper.writeValueAsString(event))
                    .build();

            outboxRepository.save(outboxEvent);
            log.info("Outbox CANCELLED event registered for expired booking: {}", bookingId);
        } catch (Exception e) {
            log.error("Failed to serialize cancellation outbox event for booking {}", bookingId, e);
        }
    }
}

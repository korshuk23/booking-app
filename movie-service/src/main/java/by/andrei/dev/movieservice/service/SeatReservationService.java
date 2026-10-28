package by.andrei.dev.movieservice.service;

import by.andrei.dev.core.dto.BookingEvent;
import by.andrei.dev.core.dto.BookingStatusEvent;
import by.andrei.dev.movieservice.entity.OutboxEvent;
import by.andrei.dev.movieservice.repository.MovieSessionRepository;
import by.andrei.dev.movieservice.repository.OutboxRepository;
import by.andrei.dev.movieservice.repository.ProcessedBookingRepository;
import by.andrei.dev.movieservice.repository.SeatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatReservationService {

    private final SeatRepository seatRepository;
    private final ProcessedBookingRepository processedBookingRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final MovieSessionRepository movieSessionRepository;

    @Transactional
    public void reserve(BookingEvent event) {
        if (processedBookingRepository.markProcessed(event.getBookingId()) == 0) {
            log.warn("Booking {} already processed, skipping", event.getBookingId());
            return;
        }

        int reserved = seatRepository.tryReserve(
                event.getSeatId(), event.getSessionId(), event.getBookingId(), LocalDateTime.now());

        if (reserved == 0) {
            saveOutboxEvent(event, "REJECTED", "Seat is not available");
            return;
        }

        if (movieSessionRepository.decrementAvailableSeats(event.getSessionId()) == 0) {
            log.error("available_seats drift for session {}: seat reserved but counter is already 0",
                    event.getSessionId());
        }

        saveOutboxEvent(event, "SEATS_RESERVED", "Seat locked for payment");
    }

    private void saveOutboxEvent(BookingEvent event, String status, String message) {
        try {
            BookingStatusEvent statusEvent = BookingStatusEvent.builder()
                    .bookingId(event.getBookingId())
                    .userId(event.getUserId())
                    .sessionId(event.getSessionId())
                    .seatId(event.getSeatId())
                    .price(event.getPrice())
                    .status(status)
                    .message(message)
                    .build();

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("BOOKING_STATUS")
                    .aggregateId(event.getBookingId())
                    .eventType(status)
                    .payload(objectMapper.writeValueAsString(statusEvent))
                    .build();

            outboxRepository.save(outboxEvent);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to serialize outbox event", ex);
        }
    }
}

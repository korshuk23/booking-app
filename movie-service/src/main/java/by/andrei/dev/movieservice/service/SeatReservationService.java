package by.andrei.dev.movieservice.service;

import by.andrei.dev.core.dto.BookingEvent;
import by.andrei.dev.core.dto.BookingStatusEvent;
import by.andrei.dev.movieservice.entity.OutboxEvent;
import by.andrei.dev.movieservice.entity.ProcessedBooking;
import by.andrei.dev.movieservice.entity.Seat;
import by.andrei.dev.movieservice.repository.OutboxRepository;
import by.andrei.dev.movieservice.repository.ProcessedBookingRepository;
import by.andrei.dev.movieservice.repository.SeatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    @Transactional
    public void reserveSeatAndSaveOutbox(BookingEvent event) {
        Seat seat = seatRepository.findById(event.getSeatId())
                .orElseThrow(() -> new IllegalArgumentException("Seat not found with ID: " + event.getSeatId()));

        if (seat.getStatus() != Seat.SeatStatus.FREE) {
            throw new IllegalStateException("Seat is already taken");
        }

        seat.setStatus(Seat.SeatStatus.RESERVED);
        seat.setReservedAt(LocalDateTime.now());
        seatRepository.saveAndFlush(seat); // Вызовет OptimisticLock при гонке

        processedBookingRepository.save(new ProcessedBooking(event.getBookingId()));

        saveOutboxEvent(event, "SEATS_RESERVED", "Seat locked for payment");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRejectionOutbox(BookingEvent event, String message) {
        saveOutboxEvent(event, "REJECTED", message);
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

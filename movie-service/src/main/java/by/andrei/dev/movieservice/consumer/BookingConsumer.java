package by.andrei.dev.movieservice.consumer;

import by.andrei.dev.core.dto.BookingEvent;
import by.andrei.dev.core.dto.BookingStatusEvent;
import by.andrei.dev.core.dto.PaymentEvent;
import by.andrei.dev.movieservice.entity.OutboxEvent;
import by.andrei.dev.movieservice.entity.Seat;
import by.andrei.dev.movieservice.repository.MovieSessionRepository;
import by.andrei.dev.movieservice.repository.OutboxRepository;
import by.andrei.dev.movieservice.repository.SeatRepository;
import by.andrei.dev.movieservice.service.SeatReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final SeatReservationService seatReservationService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final SeatRepository seatRepository;

    @KafkaListener(topics = "booking-events", groupId = "movie-group-v2")
    public void consume(BookingEvent event) {
        log.info("Processing booking event: bookingId={}, seatId={}", event.getBookingId(), event.getSeatId());
        seatReservationService.reserve(event);
    }

    @KafkaListener(topics = "payment-events", groupId = "movie-compensation-group")
    @Transactional
    public void handlePaymentCompensation(PaymentEvent event) {
        if (event.getSeatId() == null) return;

        if ("SUCCESS".equals(event.getStatus())) {
            int finalizedRows = seatRepository.finalizeSeatToBooked(event.getSeatId(), event.getBookingId());

            if (finalizedRows == 1) {
                log.info("Seat {} finalized to BOOKED for booking {}", event.getSeatId(), event.getBookingId());
                emitStatusEvent(event, "CONFIRMED", "Ticket issued successfully");
            } else if (seatRepository.existsByIdAndBookingIdAndStatus(event.getSeatId(), event.getBookingId(), Seat.SeatStatus.BOOKED)) {
                log.info("Duplicate SUCCESS for booking {}: seat already BOOKED, ignoring", event.getBookingId());
            } else {
                log.error("CRITICAL RACE: Payment {} succeeded but seat {} is gone!", event.getPaymentId(), event.getSeatId());
                emitStatusEvent(event, "ORPHANED_PAYMENT", "Payment arrived late. Initiating refund.");
            }

        } else if ("FAILED".equals(event.getStatus())) {
            int releasedRows = seatRepository.releaseSeatIfReservedByBooking(event.getSeatId(), event.getBookingId());
            if (releasedRows > 0) {
                movieSessionRepository.incrementAvailableSeats(event.getSessionId());
                emitStatusEvent(event, "CANCELLED", "Payment failed");
            }
        }
    }

    private void emitStatusEvent(PaymentEvent event, String status, String message) {
        try {
            BookingStatusEvent statusEvent = BookingStatusEvent.builder()
                    .bookingId(event.getBookingId())
                    .userId(event.getUserId())
                    .sessionId(event.getSessionId())
                    .seatId(event.getSeatId())
                    .price(event.getAmount())
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
            log.error("Failed to serialize compensation event", ex);
            throw new RuntimeException("Serialization failed, rolling back", ex);
        }
    }
}


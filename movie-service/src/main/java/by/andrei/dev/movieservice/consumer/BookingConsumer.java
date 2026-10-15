package by.andrei.dev.movieservice.consumer;

import by.andrei.dev.core.dto.BookingEvent;
import by.andrei.dev.core.dto.BookingStatusEvent;
import by.andrei.dev.core.dto.PaymentEvent;
import by.andrei.dev.movieservice.entity.MovieSession;
import by.andrei.dev.movieservice.entity.OutboxEvent;
import by.andrei.dev.movieservice.entity.ProcessedBooking;
import by.andrei.dev.movieservice.entity.Seat;
import by.andrei.dev.movieservice.producer.MovieStatusProducer;
import by.andrei.dev.movieservice.repository.MovieSessionRepository;
import by.andrei.dev.movieservice.repository.OutboxRepository;
import by.andrei.dev.movieservice.repository.ProcessedBookingRepository;
import by.andrei.dev.movieservice.repository.SeatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingConsumer {
    private final ProcessedBookingRepository processedBookingRepository;
    private final MovieStatusProducer statusProducer;
    private final SeatRepository seatRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "booking-events", groupId = "movie-group")
    @Transactional
    public void consume(BookingEvent event) {
        log.info("Processing booking event: bookingId={}, seatId={}", event.getBookingId(), event.getSeatId());

        if (processedBookingRepository.existsById(event.getBookingId())) {
            log.warn("Booking {} already processed, skipping", event.getBookingId());
            return;
        }

        try {
            Seat seat = seatRepository.findById(event.getBookingId())
                    .orElseThrow(() -> new IllegalArgumentException("Seat not found"));

            if (seat.getStatus() != Seat.SeatStatus.FREE) {
                throw new IllegalStateException("Seat is already taken");
            }

            seat.setStatus(Seat.SeatStatus.RESERVED);
            seat.setReservedAt(java.time.LocalDateTime.now());
            seatRepository.saveAndFlush(seat);

            processedBookingRepository.save(new ProcessedBooking(event.getBookingId()));

            saveOutboxEvent(event, "SEATS_RESERVED", "Seat locked for payment");

        } catch (ObjectOptimisticLockingFailureException | IllegalStateException e) {
            log.warn("Failed to reserve seat for booking {}: someone else took it first!", event.getBookingId());
            saveOutboxEvent(event, "REJECTED", "Seat already taken or error occurred");
        }
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

    /**
     * Компенсирующая транзакция Саги: если оплата провалилась, возвращаем место!
     */
    @KafkaListener(topics = "payment-events", groupId = "movie-compensation-group")
    @Transactional
    public void handlePaymentCompensation(PaymentEvent event) {
        if ("FAILED".equals(event.getStatus()) && event.getSeatId() != null) {
            log.warn("Payment failed for booking {}. Releasing seat back for session {}", event.getBookingId(), event.getSeatId());
            seatRepository.findById(event.getSeatId()).ifPresent(seat -> {
                seat.setStatus(Seat.SeatStatus.FREE);
                seatRepository.save(seat);
            });
        } else if ("SUCCESS".equals(event.getStatus()) && event.getSeatId() != null) {
            seatRepository.findById(event.getSeatId()).ifPresent(seat -> {
                seat.setStatus(Seat.SeatStatus.BOOKED);
                seatRepository.save(seat);
            });
        }
    }
}


package by.andrei.dev.movieservice.consumer;

import by.andrei.dev.core.dto.BookingEvent;
import by.andrei.dev.core.dto.PaymentEvent;
import by.andrei.dev.movieservice.entity.MovieSession;
import by.andrei.dev.movieservice.entity.ProcessedBooking;
import by.andrei.dev.movieservice.producer.MovieStatusProducer;
import by.andrei.dev.movieservice.repository.MovieSessionRepository;
import by.andrei.dev.movieservice.repository.ProcessedBookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingConsumer {
    private final ProcessedBookingRepository processedBookingRepository;
    private final MovieSessionRepository sessionRepository;
    private final MovieStatusProducer statusProducer;

    @KafkaListener(topics = "booking-events", groupId = "movie-group")
    @Transactional
    public void consume(BookingEvent event) {
        log.info("Processing booking event: bookingId={}, sessionId={}", event.getBookingId(), event.getSessionId());

        if (processedBookingRepository.existsById(event.getBookingId())) {
            log.warn("Booking {} already processed, skipping", event.getBookingId());
            return;
        }

        if (event.getSessionId() == null) {
            log.error("SessionId is null for booking {}", event.getBookingId());
            statusProducer.sendStatus(event.getBookingId(), event.getUserId(), null, event.getPrice(), "REJECTED", "Session ID cannot be null");
            return;
        }
        int updatedRows = sessionRepository.reserveSeat(event.getSessionId());

        if (updatedRows > 0) {
            processedBookingRepository.save(new ProcessedBooking(event.getBookingId()));
            log.info("Seat reserved successfully for booking {}", event.getBookingId());

            statusProducer.sendStatus(event.getBookingId(), event.getUserId(), event.getSessionId(), event.getPrice(), "SEATS_RESERVED", "Seat reserved successfully");
        } else {
            log.warn("Failed to reserve seat for booking {}: sold out or session not found", event.getBookingId());
            statusProducer.sendStatus(event.getBookingId(), event.getUserId(), event.getSessionId(), event.getPrice(), "REJECTED", "No available seats or invalid session");
        }
    }

    /**
     * Компенсирующая транзакция Саги: если оплата провалилась, возвращаем место!
     */
    @KafkaListener(topics = "payment-events", groupId = "movie-compensation-group")
    @Transactional
    public void handlePaymentCompensation(PaymentEvent event) {
        if ("FAILED".equals(event.getStatus()) && event.getSessionId() != null) {
            log.warn("Payment failed for booking {}. Releasing seat back for session {}", event.getBookingId(), event.getSessionId());
            sessionRepository.releaseSeat(event.getSessionId());
        }
    }
}


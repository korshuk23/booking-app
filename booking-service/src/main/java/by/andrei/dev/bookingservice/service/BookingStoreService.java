package by.andrei.dev.bookingservice.service;

import by.andrei.dev.bookingservice.entity.Booking;
import by.andrei.dev.bookingservice.entity.OutboxEvent;
import by.andrei.dev.bookingservice.repository.BookingRepository;
import by.andrei.dev.bookingservice.repository.OutboxRepository;
import by.andrei.dev.core.dto.BookingEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingStoreService {

    private final BookingRepository bookingRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Booking saveBookingWithOutbox(UUID userId, UUID movieId, UUID sessionId, UUID seatId, BigDecimal price) {
        Booking booking = Booking.builder()
                .userId(userId)
                .movieId(movieId)
                .sessionId(sessionId)
                .seatId(seatId)
                .price(price)
                .status(Booking.BookingStatus.PENDING)
                .build();

        Booking saved = bookingRepository.save(booking);

        BookingEvent event = BookingEvent.builder()
                .bookingId(saved.getId())
                .movieId(saved.getMovieId())
                .sessionId(saved.getSessionId())
                .seatId(saved.getSeatId())
                .userId(saved.getUserId())
                .status(saved.getStatus().name())
                .price(saved.getPrice())
                .build();

        try {
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("BOOKING")
                    .aggregateId(saved.getId())
                    .eventType("BOOKING_CREATED")
                    .payload(objectMapper.writeValueAsString(event))
                    .build();

            outboxRepository.save(outboxEvent);
            log.info("Booking {} and Outbox event saved atomically", saved.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize booking outbox event for booking {}", saved.getId(), e);
            throw new IllegalStateException("Outbox serialization failed", e);
        }

        return saved;
    }
}

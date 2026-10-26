package by.andrei.dev.bookingservice.service;

import by.andrei.dev.bookingservice.entity.Booking;
import by.andrei.dev.bookingservice.entity.OutboxEvent;
import by.andrei.dev.bookingservice.repository.BookingRepository;
import by.andrei.dev.bookingservice.repository.OutboxRepository;
import by.andrei.dev.core.dto.BookingEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.korshuk.dev.core.grpc.MovieResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {
    private final BookingRepository bookingRepository;
    private final OutboxRepository outboxRepository;
    private final MovieClient movieClient;
    private final ObjectMapper objectMapper;

    public Booking createBooking(UUID userId, UUID movieId, UUID sessionId, UUID seatId) {
        MovieResponse movieInfo = movieClient.getMovieInfo(movieId);

        if (!movieInfo.getIsAvailable()) {
            throw new IllegalStateException("Movie not available");
        }

        return saveBookingAndOutbox(userId, movieId, sessionId, seatId, BigDecimal.valueOf(movieInfo.getPrice()));
    }

    @Transactional
    public Booking saveBookingAndOutbox(UUID userId, UUID movieId, UUID sessionId, UUID seatId, BigDecimal price) {
        Booking booking = Booking.builder()
                .userId(userId)
                .movieId(movieId)
                .sessionId(sessionId)
                .seatId(seatId)
                .price(price)
                .status("PENDING")
                .build();
        Booking saved = bookingRepository.save(booking);

        BookingEvent event = BookingEvent.builder()
                .bookingId(saved.getId())
                .movieId(saved.getMovieId())
                .sessionId(saved.getSessionId())
                .seatId(saved.getSeatId())
                .userId(saved.getUserId())
                .status(saved.getStatus())
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
            log.info("Booking created and saved to Outbox with ID {}", saved.getId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error serializing booking event", e);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Booking> findAllBookings(UUID userId) {
        log.info("Fetching booking history for user: {}", userId);
        return bookingRepository.findAllByUserId(userId);
    }
}

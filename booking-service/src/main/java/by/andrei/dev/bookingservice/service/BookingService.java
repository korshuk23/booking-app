package by.andrei.dev.bookingservice.service;

import by.andrei.dev.bookingservice.dto.BookingEvent;
import by.andrei.dev.bookingservice.entity.Booking;
import by.andrei.dev.bookingservice.producer.BookingProducer;
import by.andrei.dev.bookingservice.repository.BookingRepository;
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
    private final MovieClient movieClient;
    private final BookingProducer bookingProducer;

    @Transactional
    public Booking createBooking(UUID userId, UUID movieId, Long sessionId) {
        MovieResponse movieInfo = movieClient.getMovieInfo(movieId);

        if (!movieInfo.getIsAvailable()) {
            throw new IllegalStateException("Movie not available");
        }

        Booking booking = Booking.builder()
                .userId(userId)
                .movieId(movieId)
                .sessionId(sessionId)
                .price(BigDecimal.valueOf(movieInfo.getPrice()))
                .status("PENDING")
                .build();
        Booking saved = bookingRepository.save(booking);
        // 2. Создаем ивент
        BookingEvent event = BookingEvent.builder()
                .bookingId(booking.getId())
                .movieId(booking.getMovieId())
                .userId(booking.getUserId())
                .status(booking.getStatus())
                .price(booking.getPrice())
                .build();

        // 3. Отправляем в Кафку
        bookingProducer.sendBookingEvent(event);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Booking> findAllBookings(UUID userId) {
        log.info("Fetching booking history for user: {}", userId);

        List<Booking> bookings = bookingRepository.findAllByUserId(userId);

        log.debug("Found {} bookings for user: {}", bookings.size(), userId);
        return bookings;
    }
}

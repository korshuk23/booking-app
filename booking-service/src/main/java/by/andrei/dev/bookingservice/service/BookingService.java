package by.andrei.dev.bookingservice.service;

import by.andrei.dev.bookingservice.entity.Booking;
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
    private final BookingStoreService bookingStoreService;
    private final MovieClient movieClient;

    public Booking createBooking(UUID userId, UUID movieId, UUID sessionId, UUID seatId) {
        log.info("Calling Movie Service via gRPC for movie: {}", movieId);
        MovieResponse movieInfo = movieClient.getMovieInfo(movieId);

        if (!movieInfo.getIsAvailable()) {
            log.warn("Movie {} is not available for booking", movieId);
            throw new IllegalStateException("Movie not available");
        }

        log.info("Movie verified. Saving booking to database.");
        return bookingStoreService.saveBookingWithOutbox(
                userId,
                movieId,
                sessionId,
                seatId,
                BigDecimal.valueOf(movieInfo.getPrice())
        );
    }

    @Transactional(readOnly = true)
    public List<Booking> findAllBookings(UUID userId) {
        log.info("Fetching booking history for user: {}", userId);
        return bookingRepository.findAllByUserId(userId);
    }
}

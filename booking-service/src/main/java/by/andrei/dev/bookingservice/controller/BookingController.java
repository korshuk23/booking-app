package by.andrei.dev.bookingservice.controller;

import by.andrei.dev.bookingservice.entity.Booking;
import by.andrei.dev.bookingservice.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/bookings", "/api/v2/bookings"})
@RequiredArgsConstructor
@Slf4j
public class BookingController {
    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<Booking> createBooking(@RequestHeader("X-User-Id") String userId,
                                                 @RequestParam UUID movieId,
                                                 @RequestParam UUID sessionId) {
        log.info("User {} requested booking for movie {}", userId, movieId);

        Booking booking = bookingService.createBooking(
                UUID.fromString(userId),
                movieId,
                sessionId
        );
        return new ResponseEntity<>(booking, HttpStatus.CREATED);
    }

    @GetMapping("/my")
    public ResponseEntity<List<Booking>> getMyBookings(
            @RequestHeader("X-User-Id") String userId) {

        log.info("Fetching bookings for user {}", userId);
        return ResponseEntity.ok(bookingService.findAllBookings(UUID.fromString(userId)));
    }
}

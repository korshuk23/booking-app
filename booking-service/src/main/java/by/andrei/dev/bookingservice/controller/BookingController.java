package by.andrei.dev.bookingservice.controller;

import by.andrei.dev.bookingservice.dto.CreateBookingRequest;
import by.andrei.dev.bookingservice.entity.Booking;
import by.andrei.dev.bookingservice.service.BookingNotificationService;
import by.andrei.dev.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/bookings", "/api/v2/bookings"})
@RequiredArgsConstructor
@Slf4j
public class BookingController {
    private final BookingService bookingService;
    private final BookingNotificationService notificationService;

    @PostMapping
    public ResponseEntity<Booking> createBooking(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateBookingRequest request) {

        log.info("User {} requested booking: movie={}, session={}, seat={}",
                userId, request.getMovieId(), request.getSessionId(), request.getSeatId());

        Booking booking = bookingService.createBooking(
                UUID.fromString(userId),
                request.getMovieId(),
                request.getSessionId(),
                request.getSeatId()
        );
        return new ResponseEntity<>(booking, HttpStatus.CREATED);
    }

    @GetMapping(value = "/{id}/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToBookingStatus(@PathVariable UUID id,
                                               @RequestHeader("X-User-Id") String userId) {
        log.info("User {} subscribed to SSE updates for booking: {}", userId, id);
        return notificationService.subscribe(id, UUID.fromString(userId));
    }

    @GetMapping("/my")
    public ResponseEntity<List<Booking>> getMyBookings(
            @RequestHeader("X-User-Id") String userId) {

        log.info("Fetching bookings for user {}", userId);
        return ResponseEntity.ok(bookingService.findAllBookings(UUID.fromString(userId)));
    }
}

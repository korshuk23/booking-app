package by.andrei.dev.movieservice.consumer;

import by.andrei.dev.core.dto.BookingEvent;
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
        if (processedBookingRepository.existsById(event.getBookingId())) {
            return;
        }

        Optional<MovieSession> sessionOpt = sessionRepository.findFirstByMovieIdOrderByStartTimeAsc(event.getMovieId());

        if (sessionOpt.isPresent()) {
            MovieSession session = sessionOpt.get();

            if (session.getAvailableSeats() > 0) {
                session.setAvailableSeats(session.getAvailableSeats() - 1);
                sessionRepository.save(session);
                processedBookingRepository.save(new ProcessedBooking(event.getBookingId()));

                statusProducer.sendStatus(event.getBookingId(), "CONFIRMED", "Seat reserved successfully");
            } else {
                statusProducer.sendStatus(event.getBookingId(), "REJECTED", "No available seats");
            }
        } else {
            statusProducer.sendStatus(event.getBookingId(), "REJECTED", "Movie session not found");
        }
    }
}


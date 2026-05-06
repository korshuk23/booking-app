package by.andrei.dev.movieservice.consumer;

import by.andrei.dev.movieservice.dto.BookingEvent;
import by.andrei.dev.movieservice.entity.MovieSession;
import by.andrei.dev.movieservice.entity.ProcessedBooking;
import by.andrei.dev.movieservice.repository.MovieSessionRepository;
import by.andrei.dev.movieservice.repository.ProcessedBookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingConsumer {
    private final ProcessedBookingRepository processedBookingRepository;
    private final MovieSessionRepository sessionRepository;

    @KafkaListener(topics = "booking-events", groupId = "movie-group")
    @Transactional
    public void consume(BookingEvent event) {
        // 1. Проверка идемпотентности (Защита от дублей)
        if (processedBookingRepository.existsById(event.getBookingId())) {
            log.info("Booking {} already processed. Skipping...", event.getBookingId());
            return;
        }

        // 2. Поиск сеанса
        MovieSession session = sessionRepository.findFirstByMovieIdOrderByStartTimeAsc(event.getMovieId())
                .orElseThrow(() -> new RuntimeException("No session found for movie: " + event.getMovieId()));

        // 3. Проверка и списание мест
        if (session.getAvailableSeats() > 0) {
            session.setAvailableSeats(session.getAvailableSeats() - 1);
            sessionRepository.save(session);

            // 4. Фиксируем обработанный ID заказа
            processedBookingRepository.save(new ProcessedBooking(event.getBookingId()));

            log.info("Success! Seat reserved for booking {}", event.getBookingId());
        } else {
            log.warn("No seats left for booking {}", event.getBookingId());
            // Тут потом добавим отправку REJECTED ивента
        }
    }
}


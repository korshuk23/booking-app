package by.andrei.dev.movieservice.consumer;

import by.andrei.dev.core.dto.BookingEvent;
import by.andrei.dev.core.dto.PaymentEvent;
import by.andrei.dev.movieservice.entity.Seat;
import by.andrei.dev.movieservice.repository.ProcessedBookingRepository;
import by.andrei.dev.movieservice.repository.SeatRepository;
import by.andrei.dev.movieservice.service.SeatReservationService;
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
    private final SeatReservationService seatReservationService;
    private final SeatRepository seatRepository;

    @KafkaListener(topics = "booking-events", groupId = "movie-group-v2")
    public void consume(BookingEvent event) {
        log.info("Processing booking event: bookingId={}, seatId={}", event.getBookingId(), event.getSeatId());

        if (processedBookingRepository.existsById(event.getBookingId())) {
            log.warn("Booking {} already processed, skipping", event.getBookingId());
            return;
        }

        try {
            seatReservationService.reserveSeatAndSaveOutbox(event);
            log.info("Seat {} successfully RESERVED for booking {}", event.getSeatId(), event.getBookingId());
        } catch (Exception e) { // Перехватываем ВСЕ ошибки (включая если кресло занято или не найдено)
            log.warn("Failed to reserve seat for booking {}: {}", event.getBookingId(), e.getMessage());
            seatReservationService.saveRejectionOutbox(event, e.getMessage());
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


package by.andrei.dev.movieservice.service;

import by.andrei.dev.movieservice.entity.Seat;
import by.andrei.dev.movieservice.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatReleaseJob {

    private final SeatRepository seatRepository;

    @Value("${seat.reservation.timeout-minutes:15}")
    private long timeoutMinutes;

    @Scheduled(fixedRateString = "${seat.release.job-rate-ms:60000}")
    @Transactional
    public void releaseExpiredReservations() {
        LocalDateTime expirationTime = LocalDateTime.now().minusMinutes(timeoutMinutes);

        List<Seat> expiredSeats = seatRepository.findByStatusAndReservedAtBefore(
                Seat.SeatStatus.RESERVED, expirationTime
        );

        if (!expiredSeats.isEmpty()) {
            log.info("Found {} expired seat reservations. Releasing them...", expiredSeats.size());

            for (Seat seat : expiredSeats) {
                seat.setStatus(Seat.SeatStatus.FREE);
                seat.setReservedAt(null);

                // В идеале тут тоже нужно кинуть Outbox событие "BOOKING_CANCELLED",
                // чтобы booking-service обновил статус у себя в БД на CANCELLED.
            }

            seatRepository.saveAll(expiredSeats);
        }
    }
}

package by.andrei.dev.movieservice.service;

import by.andrei.dev.core.dto.BookingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatReservationCoordinator {
    private final RedissonClient redissonClient;
    private final SeatReservationService seatReservationService;

    private static final String LOCK_PREFIX = "lock:seat:";
    private static final long WAIT_TIME_MS = 1000;

    public void processReservationWithLock(BookingEvent event) {
        String lockKey = LOCK_PREFIX + event.getSeatId();
        RLock lock = redissonClient.getLock(lockKey);

        boolean isLocked = false;
        try {
            isLocked = lock.tryLock(WAIT_TIME_MS, TimeUnit.MILLISECONDS);

            if (!isLocked) {
                log.warn("Contention detected: Seat {} is currently locked by another request. Failing fast.", event.getSeatId());
                seatReservationService.saveRejectionOutbox(event, "Место в данный момент бронируется другим покупателем");
                return;
            }

            log.debug("Acquired Redis distributed lock for seat {}", event.getSeatId());

            seatReservationService.reserveSeatAndSaveOutbox(event);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted while waiting for seat lock: {}", event.getSeatId(), e);
            seatReservationService.saveRejectionOutbox(event, "Таймаут ожидания блокировки");
        } catch (Exception e) {
            log.warn("Business rejection for booking {}: {}", event.getBookingId(), e.getMessage());
            seatReservationService.saveRejectionOutbox(event, e.getMessage());
        } finally {
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released Redis distributed lock for seat {}", event.getSeatId());
            }
        }
    }
}

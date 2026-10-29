package by.andrei.dev.movieservice.repository;

import by.andrei.dev.movieservice.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    interface ExpiredSeat {
        UUID getSeatId();

        UUID getSessionId();

        UUID getBookingId();
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE seats
               SET status = 'RESERVED',
                   booking_id = :bookingId,
                   reserved_at = :now,
                   expired_at = :expiredAt,
                   version = version + 1
             WHERE id = :seatId
               AND session_id = :sessionId
               AND status = 'FREE'
            """, nativeQuery = true)
    int tryReserve(@Param("seatId") UUID seatId,
                   @Param("sessionId") UUID sessionId,
                   @Param("bookingId") UUID bookingId,
                   @Param("now") LocalDateTime now,
                   @Param("expiresAt") LocalDateTime expiresAt);

    @Modifying
    @Query("UPDATE Seat s SET s.status = 'BOOKED', s.expiresAt = null " +
            "WHERE s.id = :seatId AND s.bookingId = :bookingId AND s.status = 'RESERVED'")
    int finalizeSeatToBooked(@Param("seatId") UUID seatId, @Param("bookingId") UUID bookingId);

    @Modifying
    @Query("UPDATE Seat s SET s.status = 'FREE', s.bookingId = null, s.reservedAt = null, s.expiresAt = null " +
            "WHERE s.id = :seatId AND s.bookingId = :bookingId AND s.status = 'RESERVED'")
    int releaseSeatIfReservedByBooking(@Param("seatId") UUID seatId, @Param("bookingId") UUID bookingId);


    // Решение с CTE: берем старые данные, лочим их, обновляем и возвращаем то, что было
    @Query(value = """
            WITH expired AS (
                SELECT id, session_id, booking_id FROM seats
                 WHERE status = 'RESERVED' AND expires_at < :now
                   FOR UPDATE SKIP LOCKED
            )
            UPDATE seats s
               SET status = 'FREE', booking_id = NULL, reserved_at = NULL,
                   expires_at = NULL, version = s.version + 1
              FROM expired e
             WHERE s.id = e.id
            RETURNING e.id AS "seatId", e.session_id AS "sessionId", e.booking_id AS "bookingId"
            """, nativeQuery = true)
    List<ExpiredSeat> releaseExpired(@Param("now") LocalDateTime now);
}

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

    List<Seat> findByStatusAndReservedAtBefore(Seat.SeatStatus status, LocalDateTime time);

    @Modifying
    @Query("UPDATE Seat s SET s.status = 'FREE', s.bookingId = null, s.reservedAt = null " +
            "WHERE s.id = :seatId AND s.bookingId = :bookingId AND s.status = 'RESERVED'")
    int releaseSeatIfReservedByBooking(@Param("seatId") UUID seatId, @Param("bookingId") UUID bookingId);

    @Modifying
    @Query("UPDATE Seat s SET s.status = 'BOOKED' " +
            "WHERE s.id = :seatId AND s.bookingId = :bookingId AND s.status = 'RESERVED'")
    int finalizeSeatToBooked(@Param("seatId") UUID seatId, @Param("bookingId") UUID bookingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE seats
           SET status = 'RESERVED',
               booking_id = :bookingId,
               reserved_at = :now,
               version = version + 1
         WHERE id = :seatId
           AND session_id = :sessionId
           AND status = 'FREE'
        """, nativeQuery = true)
    int tryReserve(@Param("seatId") UUID seatId,
                   @Param("sessionId") UUID sessionId,
                   @Param("bookingId") UUID bookingId,
                   @Param("now") LocalDateTime now);
}

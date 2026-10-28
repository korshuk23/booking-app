package by.andrei.dev.movieservice.repository;

import by.andrei.dev.movieservice.entity.ProcessedBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedBookingRepository extends JpaRepository<ProcessedBooking, UUID> {

    @Modifying
    @Query(value = "INSERT INTO processed_bookings (booking_id) VALUES (:id) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int markProcessed(@Param("id") UUID id);
}

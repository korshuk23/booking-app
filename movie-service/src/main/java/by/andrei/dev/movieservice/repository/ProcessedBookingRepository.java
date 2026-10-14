package by.andrei.dev.movieservice.repository;

import by.andrei.dev.movieservice.entity.ProcessedBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedBookingRepository extends JpaRepository<ProcessedBooking, UUID> {
}

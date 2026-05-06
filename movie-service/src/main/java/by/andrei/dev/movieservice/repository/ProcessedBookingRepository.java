package by.andrei.dev.movieservice.repository;

import by.andrei.dev.movieservice.entity.ProcessedBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedBookingRepository extends JpaRepository<ProcessedBooking, UUID> {
}

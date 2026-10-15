package by.andrei.dev.movieservice.repository;

import by.andrei.dev.movieservice.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByStatusAndReservedAtBefore(Seat.SeatStatus status, LocalDateTime time);
}

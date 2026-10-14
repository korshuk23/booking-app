package by.andrei.dev.movieservice.repository;

import by.andrei.dev.movieservice.entity.MovieSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MovieSessionRepository extends JpaRepository<MovieSession, UUID> {
    List<MovieSession> findAllByMovieId(UUID movieId);

    @Modifying
    @Query("UPDATE MovieSession  s SET s.availableSeats = s.availableSeats - 1" +
            "WHERE s.id = :sessionId AND s.availableSeats > 0")
    int reserveSeat(@Param("sessionid") UUID sessionId);

    @Modifying
    @Query("UPDATE MovieSession  s SET s.availableSeats = s.availableSeats + 1" +
            "WHERE s.id = :sessionId AND s.availableSeats <s.totalSeats")
    int releaseSeat(@Param("sesionId") UUID sessionId);
}

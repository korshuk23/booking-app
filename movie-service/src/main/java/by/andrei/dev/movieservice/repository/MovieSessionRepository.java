package by.andrei.dev.movieservice.repository;

import by.andrei.dev.movieservice.entity.MovieSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MovieSessionRepository extends JpaRepository<MovieSession, UUID> {

    // Ищем сеанс по фильму (для простоты берем первый попавшийся или актуальный)
    Optional<MovieSession> findFirstByMovieIdOrderByStartTimeAsc(UUID movieId);
}

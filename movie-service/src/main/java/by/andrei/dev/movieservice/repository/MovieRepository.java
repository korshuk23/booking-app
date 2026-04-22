package by.andrei.dev.movieservice.repository;

import by.andrei.dev.movieservice.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MovieRepository extends JpaRepository<Movie, UUID> {

    List<Movie> findAllByIsAvailableTrue();

    List<Movie> findAllByGenreIgnoreCase(String genre);
}
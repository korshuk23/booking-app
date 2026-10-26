package by.andrei.dev.movieservice.repository;

import by.andrei.dev.movieservice.entity.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MovieRepository extends JpaRepository<Movie, UUID> {

    Page<Movie> findAllByIsAvailableTrue(Pageable pageable);

    Page<Movie> findAllByGenreIgnoreCase(String genre, Pageable pageable);
}
package by.andrei.dev.movieservice.service;

import by.andrei.dev.movieservice.entity.Movie;
import by.andrei.dev.movieservice.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MovieService {

    private final MovieRepository movieRepository;

    // Чтение: используем readOnly транзакцию для оптимизации (админский лайфхак)
    @Transactional(readOnly = true)
    public List<Movie> findAllAvailable() {
        log.info("Fetching all available movies from DB");
        return movieRepository.findAllByIsAvailableTrue();
    }

    @Transactional(readOnly = true)
    public Movie findById(UUID id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Movie with ID " + id + " not found"));
    }

    @Transactional
    public Movie save(Movie movie) {
        log.info("Saving new movie: {}", movie.getTitle());
        return movieRepository.save(movie);
    }

    @Transactional
    public void setAvailability(UUID id, boolean status) {
        Movie movie = findById(id);
        movie.setAvailable(status);
        // Не обязательно вызывать save(), Hibernate сам увидит изменения в транзакции (Dirty Checking)
    }
}

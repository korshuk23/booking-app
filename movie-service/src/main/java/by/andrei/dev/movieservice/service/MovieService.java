package by.andrei.dev.movieservice.service;

import by.andrei.dev.movieservice.entity.Movie;
import by.andrei.dev.movieservice.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
    @Cacheable(value = "movies", key = "'all'")
    @Transactional(readOnly = true)
    public List<Movie> findAllAvailable() {
        log.info("CACHE MISS: Fetching all available movies from Postgres DB");
        return movieRepository.findAllByIsAvailableTrue();
    }

    @Cacheable(value = "movie", key = "#id")
    @Transactional(readOnly = true)
    public Movie findById(UUID id) {
        log.info("CACHE MISS: Fetching movie {} from Postgres DB", id);
        return movieRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Movie with ID " + id + " not found"));
    }

    @Caching(evict = {
            @CacheEvict(value = "movies", allEntries = true)
    })
    @Transactional
    public Movie save(Movie movie) {
        log.info("Saving new movie and invalidating 'movies' cache: {}", movie.getTitle());
        return movieRepository.save(movie);
    }

    @Caching(evict = {
            @CacheEvict(value = "movies", allEntries = true),
            @CacheEvict(value = "movie", key = "#id")
    })
    @Transactional
    public void setAvailability(UUID id, boolean status) {
        log.info("Updating availability for movie {} and invalidating cache", id);
        Movie movie = findById(id);
        movie.setAvailable(status);
    }
}

package by.andrei.dev.movieservice.controller;

import by.andrei.dev.movieservice.entity.Movie;
import by.andrei.dev.movieservice.service.MovieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
@Slf4j
public class MovieController {

    private final MovieService movieService;

    // 1. Получить все доступные фильмы
    @GetMapping
    public ResponseEntity<List<Movie>> getAllAvailable(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("Request for all movies. User-Id: {}", userId);
        return ResponseEntity.ok(movieService.findAllAvailable());
    }

    // 2. Получить конкретный фильм по ID
    @GetMapping("/{id}")
    public ResponseEntity<Movie> getById(@PathVariable UUID id) {
        log.debug("Fetching movie by id: {}", id);
        return ResponseEntity.ok(movieService.findById(id));
    }

    // 3. Создать новый фильм (только для админов в будущем)
    @PostMapping
    public ResponseEntity<Movie> createMovie(@RequestBody Movie movie) {
        log.info("Creating new movie: {}", movie.getTitle());
        Movie savedMovie = movieService.save(movie);
        return new ResponseEntity<>(savedMovie, HttpStatus.CREATED);
    }

    // 4. Переключить доступность (быстрый админский метод)
    @PatchMapping("/{id}/availability")
    public ResponseEntity<Void> toggleAvailability(
            @PathVariable UUID id,
            @RequestParam boolean status) {

        log.info("Changing availability for movie {} to {}", id, status);
        movieService.setAvailability(id, status);
        return ResponseEntity.noContent().build();
    }
}
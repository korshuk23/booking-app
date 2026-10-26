package by.andrei.dev.movieservice.controller;

import by.andrei.dev.movieservice.entity.Movie;
import by.andrei.dev.movieservice.service.MovieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

    @GetMapping
    public ResponseEntity<Page<Movie>> getAllAvailable(
            @PageableDefault(page = 0, size = 20, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {

        log.info("Request for movies page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(movieService.findAllAvailable(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Movie> getById(@PathVariable UUID id) {
        log.debug("Fetching movie by id: {}", id);
        return ResponseEntity.ok(movieService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Movie> createMovie(@RequestBody Movie movie) {
        log.info("Creating new movie: {}", movie.getTitle());
        Movie savedMovie = movieService.save(movie);
        return new ResponseEntity<>(savedMovie, HttpStatus.CREATED);
    }

    @PatchMapping("/{id}/availability")
    public ResponseEntity<Void> toggleAvailability(
            @PathVariable UUID id,
            @RequestParam boolean status) {

        log.info("Changing availability for movie {} to {}", id, status);
        movieService.setAvailability(id, status);
        return ResponseEntity.noContent().build();
    }
}
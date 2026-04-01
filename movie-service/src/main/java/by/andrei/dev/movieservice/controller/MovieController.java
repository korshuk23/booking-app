package by.andrei.dev.movieservice.controller;

import by.andrei.dev.movieservice.entity.Movie;
import by.andrei.dev.movieservice.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieRepository movieRepository;

    // Получить все фильмы
    @GetMapping
    public List<Movie> getAllMovies(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        System.out.println("Запрос списка фильмов. UserID из заголовка: " + userId);
        return movieRepository.findAll();
    }

    // Добавить новый фильм (для теста через Postman)
    @PostMapping
    public ResponseEntity<?> createMovie(@RequestBody Movie movie,
                                         @RequestHeader("X-User-Role") String role) {
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Только админ может добавлять фильмы!");
        }
        return ResponseEntity.ok().body(movieRepository.save(movie));
    }
}
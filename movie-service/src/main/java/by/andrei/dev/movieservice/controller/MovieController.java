package by.andrei.dev.movieservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/movies")
public class MovieController {

    @GetMapping("/test")
    public String testSecurity(@RequestHeader("X-User-Id") String userId) {
        // userId — это то, что наш gRPC-клиент в Гейтвее достал из токена
        return "Movie Service приветствует тебя, пользователь " + userId + "! Система видит твой ID и доверяет тебе.";
    }
}
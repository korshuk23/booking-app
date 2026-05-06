package by.andrei.dev.movieservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "movie_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovieSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "movie_id", nullable = false)
    private UUID movieId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    @Column(name = "available_seats", nullable = false)
    private Integer availableSeats;
}

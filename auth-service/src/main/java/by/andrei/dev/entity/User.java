package by.andrei.dev.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // Добавь эту строку!
    private UUID id;

    @Column(unique = true, nullable = false)
    private String identifier;

    private String role;

    private boolean isBlocked;

    private String telegramId;
}

package by.andrei.dev.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "movie_id", nullable = false)
    private UUID movieId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    private BigDecimal price;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum BookingStatus {
        PENDING,
        SEATS_RESERVED,
        CONFIRMED,
        CANCELLED,
        REJECTED;

        public boolean canTransitionTo(BookingStatus next) {
            return switch (this) {
                case PENDING -> next == SEATS_RESERVED || next == REJECTED || next == CANCELLED;
                case SEATS_RESERVED -> next == CONFIRMED || next == CANCELLED;
                case CONFIRMED, CANCELLED, REJECTED -> false;
            };
        }
    }

    public void transitionTo(BookingStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    String.format("Недопустимый переход статуса заказа %s: из [%s] в [%s]",
                            this.id, this.status, newStatus)
            );
        }
        this.status = newStatus;
    }
}
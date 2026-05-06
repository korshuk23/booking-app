package by.andrei.dev.movieservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedBooking {

    @Id
    private UUID bookingId;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public ProcessedBooking(UUID bookingId) {
        this.bookingId = bookingId;
        this.processedAt = LocalDateTime.now();
    }
}
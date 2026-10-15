package by.andrei.dev.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BookingEvent {
    private UUID bookingId;
    private UUID movieId;
    private UUID sessionId;
    private UUID seatId;
    private UUID userId;
    private String status;
    private BigDecimal price;
}
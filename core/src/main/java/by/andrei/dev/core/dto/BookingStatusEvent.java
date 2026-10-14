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
public class BookingStatusEvent {
    private UUID bookingId;
    private UUID userId;
    private UUID sessionId;
    private BigDecimal price;
    private String status;
    private String message;
}


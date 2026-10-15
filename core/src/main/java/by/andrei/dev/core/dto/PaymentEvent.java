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
public class PaymentEvent {
    private UUID paymentId;
    private UUID bookingId;
    private UUID userId;
    private UUID sessionId;
    private UUID seatId;
    private BigDecimal amount;
    private String status; // SUCCESS, FAILED
    private String failureReason;
}

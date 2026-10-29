package by.andrei.dev.paymentservice.repository;

import by.andrei.dev.paymentservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByBookingId(UUID bookingId);

    @Query(value = "SELECT * FROM payments WHERE status = 'PENDING' AND created_at < :threshold " +
            "ORDER BY created_at ASC LIMIT 50 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Payment> findStuckPendingForUpdate(@Param("threshold") LocalDateTime threshold);
}
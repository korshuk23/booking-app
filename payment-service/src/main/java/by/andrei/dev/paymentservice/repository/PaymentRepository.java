package by.andrei.dev.paymentservice.repository;

import by.andrei.dev.paymentservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByBookingId(UUID bookingId);

    List<Payment> findByStatusAndCreatedAtBefore(String status, LocalDateTime time);

}
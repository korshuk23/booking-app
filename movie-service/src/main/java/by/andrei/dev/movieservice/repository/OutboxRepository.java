package by.andrei.dev.movieservice.repository;

import by.andrei.dev.movieservice.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value = "SELECT * FROM outbox_events WHERE status = 'PENDING'" +
            " ORDER BY created_at ASC LIMIT 50 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findPendingEventsForProcessing();

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.status = :status AND o.sentAt <= :threshold")
    int deleteByStatusAndSentAtBefore(@Param("status") String status, @Param("threshold") LocalDateTime threshold);
}

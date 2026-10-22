package by.andrei.dev.bookingservice.producer;

import by.andrei.dev.bookingservice.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxCleanupJob {
    private final OutboxRepository outboxRepository;

    @Value("${outbox.retention.days:3}")
    private long retentionDays;


    @Scheduled(cron = "${outbox.cleanup.cron:0 0 * * * *}")
    @Transactional
    public void cleanupOutbox() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);

        log.info("Starting cleanup for outbox events sent before: {}", threshold);
        int deletedRows = outboxRepository.deleteByStatusAndSentAtBefore("SENT", threshold);

        if (deletedRows > 0) {
            log.info("Successfully deleted {} old Outbox events.", deletedRows);
        } else {
            log.debug("No old Outbox events found for deletion.");
        }
    }
}

package com.example.contractpayments.outbox.adapter.out.persistence;

import com.example.contractpayments.outbox.application.OutboxMessage;
import com.example.contractpayments.outbox.application.OutboxStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresOutboxStore implements OutboxStore {
    private final JdbcTemplate jdbc;

    public PostgresOutboxStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxMessage> claimBatch(Instant now, int size, Duration lease) {
        return jdbc.query("""
            WITH candidates AS (
              SELECT id FROM outbox_events
              WHERE (status = 'PENDING' AND available_at <= ?)
                 OR (status = 'PROCESSING' AND locked_until <= ?)
              ORDER BY available_at, created_at
              FOR UPDATE SKIP LOCKED
              LIMIT ?
            )
            UPDATE outbox_events event
               SET status = 'PROCESSING', attempt_count = attempt_count + 1,
                   locked_until = ?, last_error = NULL
              FROM candidates
             WHERE event.id = candidates.id
            RETURNING event.id, event.aggregate_id, event.payload::text, event.attempt_count
            """, (result, row) -> new OutboxMessage(
                result.getObject("id", UUID.class), result.getObject("aggregate_id", UUID.class),
                result.getString("payload"), result.getInt("attempt_count")),
            java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), size,
            java.sql.Timestamp.from(now.plus(lease)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID eventId, Instant now) {
        jdbc.update("""
            UPDATE outbox_events SET status = 'PUBLISHED', published_at = ?, locked_until = NULL,
                last_error = NULL WHERE id = ? AND status = 'PROCESSING'
            """, java.sql.Timestamp.from(now), eventId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reschedule(UUID eventId, Instant availableAt, String error) {
        jdbc.update("""
            UPDATE outbox_events SET status = 'PENDING', available_at = ?, locked_until = NULL,
                last_error = ? WHERE id = ? AND status = 'PROCESSING'
            """, java.sql.Timestamp.from(availableAt), error, eventId);
    }
}

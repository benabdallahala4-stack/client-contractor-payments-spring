package com.example.contractpayments.outbox.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEntity {
    @Id public UUID id;
    @Column(name = "aggregate_type", nullable = false) public String aggregateType;
    @Column(name = "aggregate_id", nullable = false) public UUID aggregateId;
    @Column(name = "event_type", nullable = false) public String eventType;
    @Column(name = "event_version", nullable = false) public int eventVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") public String payload;
    @Column(nullable = false) public String status;
    @Column(name = "attempt_count", nullable = false) public int attemptCount;
    @Column(name = "available_at", nullable = false) public Instant availableAt;
    @Column(name = "locked_until") public Instant lockedUntil;
    @Column(name = "last_error") public String lastError;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "published_at") public Instant publishedAt;

    protected OutboxEntity() { }

    public OutboxEntity(UUID id, UUID jobId, String payload, Instant now) {
        this.id = id;
        this.aggregateType = "Job";
        this.aggregateId = jobId;
        this.eventType = "JobPaid";
        this.eventVersion = 1;
        this.payload = payload;
        this.status = "PENDING";
        this.attemptCount = 0;
        this.availableAt = now;
        this.createdAt = now;
    }
}

package com.example.contractpayments.payment.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity @Table(name = "payments")
public class PaymentEntity {
    @Id public UUID id;
    @Column(name = "job_id", nullable = false) public UUID jobId;
    @Column(name = "amount_cents", nullable = false) public int amountCents;
    @Column(name = "idempotency_key", nullable = false, length = 128) public String idempotencyKey;
    @Column(nullable = false, length = 16) public String status;

    protected PaymentEntity() { }
    public PaymentEntity(UUID id, UUID jobId, int amountCents, String key) {
        this.id = id; this.jobId = jobId; this.amountCents = amountCents;
        this.idempotencyKey = key; this.status = "COMPLETED";
    }
}

package com.example.contractpayments.job.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity @Table(name = "jobs")
public class JobEntity {
    @Id public UUID id;
    @Column(name = "contract_id", nullable = false) public UUID contractId;
    @Column(nullable = false, length = 120) public String title;
    @Column(name = "amount_cents", nullable = false) public int amountCents;
    @Column(nullable = false, length = 16) public String status;

    protected JobEntity() { }
    public JobEntity(UUID id, UUID contractId, String title, int amountCents) {
        this.id = id; this.contractId = contractId; this.title = title;
        this.amountCents = amountCents; this.status = "OPEN";
    }
}

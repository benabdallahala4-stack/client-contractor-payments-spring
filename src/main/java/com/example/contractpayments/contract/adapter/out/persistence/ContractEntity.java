package com.example.contractpayments.contract.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity @Table(name = "contracts")
public class ContractEntity {
    @Id public UUID id;
    @Column(name = "client_id", nullable = false) public UUID clientId;
    @Column(name = "contractor_id", nullable = false) public UUID contractorId;
    @Column(nullable = false, length = 120) public String title;
    @Column(nullable = false, length = 16) public String status;

    protected ContractEntity() { }
    public ContractEntity(UUID id, UUID clientId, UUID contractorId, String title) {
        this.id = id; this.clientId = clientId; this.contractorId = contractorId;
        this.title = title; this.status = "ACTIVE";
    }
}

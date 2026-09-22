package com.example.contractpayments.profile.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity @Table(name = "profiles")
public class ProfileEntity {
    @Id public UUID id;
    @Column(nullable = false, length = 100) public String name;
    @Column(nullable = false, length = 16) public String role;
    @Column(name = "balance_cents", nullable = false) public int balanceCents;

    protected ProfileEntity() { }
    public ProfileEntity(UUID id, String name, String role, int balanceCents) {
        this.id = id; this.name = name; this.role = role; this.balanceCents = balanceCents;
    }
}

package com.example.contractpayments.profile.domain;

import java.util.UUID;

public record Profile(UUID id, String name, Role role, int balanceCents) {
    public enum Role { CLIENT, CONTRACTOR }
}

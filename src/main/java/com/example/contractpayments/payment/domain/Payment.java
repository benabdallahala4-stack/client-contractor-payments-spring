package com.example.contractpayments.payment.domain;

import java.time.Instant;
import java.util.UUID;

public record Payment(UUID id, UUID jobId, int amountCents, String status, Instant createdAt) { }

package com.example.contractpayments.job.domain;

import java.util.UUID;

public record Job(UUID id, UUID contractId, String title, int amountCents, String status) { }

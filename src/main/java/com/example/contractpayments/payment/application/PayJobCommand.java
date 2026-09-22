package com.example.contractpayments.payment.application;

import com.example.contractpayments.payment.domain.IdempotencyKey;
import java.util.UUID;

public record PayJobCommand(UUID jobId, IdempotencyKey idempotencyKey) { }

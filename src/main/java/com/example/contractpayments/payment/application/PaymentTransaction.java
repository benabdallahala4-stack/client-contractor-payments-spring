package com.example.contractpayments.payment.application;

import com.example.contractpayments.payment.domain.IdempotencyKey;
import java.util.UUID;

public interface PaymentTransaction {
    PaymentResult pay(UUID jobId, IdempotencyKey idempotencyKey);
}

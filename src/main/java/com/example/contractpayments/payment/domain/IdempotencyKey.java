package com.example.contractpayments.payment.domain;

public record IdempotencyKey(String value) {
    private static final String PATTERN = "[A-Za-z0-9._:-]{1,128}";
    public IdempotencyKey {
        if (value == null || !value.matches(PATTERN)) {
            throw new IllegalArgumentException("Idempotency key must contain 1-128 allowed characters");
        }
    }
}

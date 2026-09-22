package com.example.contractpayments.payment.application;

public final class PayJobService implements PayJobUseCase {
    private final PaymentTransaction transaction;
    public PayJobService(PaymentTransaction transaction) { this.transaction = transaction; }
    @Override public PaymentResult pay(PayJobCommand command) {
        return transaction.pay(command.jobId(), command.idempotencyKey());
    }
}

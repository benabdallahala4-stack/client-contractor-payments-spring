package com.example.contractpayments.payment.application;

public interface PayJobUseCase {
    PaymentResult pay(PayJobCommand command);
}

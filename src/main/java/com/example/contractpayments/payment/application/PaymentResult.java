package com.example.contractpayments.payment.application;

import com.example.contractpayments.payment.domain.Payment;

public record PaymentResult(Payment payment, boolean replayed) { }

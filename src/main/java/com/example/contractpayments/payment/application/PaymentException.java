package com.example.contractpayments.payment.application;

public final class PaymentException extends RuntimeException {
    public enum Kind { BAD_REQUEST, NOT_FOUND, CONFLICT, UNPROCESSABLE, RETRYABLE }
    private final Kind kind;
    private final String code;

    public PaymentException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public Kind kind() { return kind; }
    public String code() { return code; }
}

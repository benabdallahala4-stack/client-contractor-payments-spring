package com.example.contractpayments.shared.web;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    final HttpStatus status;
    final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}

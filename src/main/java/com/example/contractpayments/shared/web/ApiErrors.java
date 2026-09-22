package com.example.contractpayments.shared.web;

import com.example.contractpayments.payment.application.PaymentException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(PaymentException.class)
    ResponseEntity<Map<String, Object>> payment(PaymentException error) {
        HttpStatus status = switch (error.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case RETRYABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status)
            .body(Map.of("error", Map.of("code", error.code(), "message", error.getMessage())));
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException error) {
        return ResponseEntity.status(error.status).body(Map.of("error", Map.of("code", error.code, "message", error.getMessage())));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Map<String, Object>> input(Exception error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", Map.of("code", "INVALID_INPUT", "message", "Check the request fields.")));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> invalidKey(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", Map.of("code", "INVALID_KEY", "message", error.getMessage())));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> internal(Exception error) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", Map.of("code", "INTERNAL_ERROR", "message", "The request could not be completed.")));
    }
}

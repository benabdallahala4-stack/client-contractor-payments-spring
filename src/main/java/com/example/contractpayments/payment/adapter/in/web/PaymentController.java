package com.example.contractpayments.payment.adapter.in.web;

import com.example.contractpayments.payment.adapter.out.persistence.PaymentEntity;
import com.example.contractpayments.payment.adapter.out.persistence.PaymentJpaRepository;
import com.example.contractpayments.payment.application.PayJobCommand;
import com.example.contractpayments.payment.application.PayJobUseCase;
import com.example.contractpayments.payment.domain.IdempotencyKey;
import com.example.contractpayments.shared.web.ApiException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentController {
    private final PayJobUseCase payJob;
    private final PaymentJpaRepository payments;
    public PaymentController(PayJobUseCase payJob, PaymentJpaRepository payments) {
        this.payJob = payJob; this.payments = payments;
    }
    public record View(UUID id, UUID jobId, int amountCents, String status) {
        static View of(PaymentEntity p) { return new View(p.id, p.jobId, p.amountCents, p.status); }
        static View of(com.example.contractpayments.payment.domain.Payment p) {
            return new View(p.id(), p.jobId(), p.amountCents(), p.status());
        }
    }
    @PostMapping({"/jobs/{jobId}/payments", "/jobs/{jobId}/pay"})
    ResponseEntity<View> pay(@PathVariable UUID jobId,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        var result = payJob.pay(new PayJobCommand(jobId, new IdempotencyKey(key)));
        return ResponseEntity.status(result.replayed() ? 200 : 201)
            .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
            .location(URI.create("/payments/" + result.payment().id()))
            .body(View.of(result.payment()));
    }
    @GetMapping("/payments/{id}") View get(@PathVariable UUID id) {
        return View.of(payments.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
            "PAYMENT_NOT_FOUND", "Payment not found.")));
    }
}

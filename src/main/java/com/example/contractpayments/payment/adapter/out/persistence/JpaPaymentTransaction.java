package com.example.contractpayments.payment.adapter.out.persistence;

import com.example.contractpayments.contract.adapter.out.persistence.ContractEntity;
import com.example.contractpayments.contract.adapter.out.persistence.ContractJpaRepository;
import com.example.contractpayments.job.adapter.out.persistence.JobEntity;
import com.example.contractpayments.job.adapter.out.persistence.JobJpaRepository;
import com.example.contractpayments.outbox.adapter.out.persistence.OutboxEntity;
import com.example.contractpayments.outbox.adapter.out.persistence.OutboxJpaRepository;
import com.example.contractpayments.payment.application.PaymentException;
import com.example.contractpayments.payment.application.PaymentException.Kind;
import com.example.contractpayments.payment.application.PaymentResult;
import com.example.contractpayments.payment.application.PaymentTransaction;
import com.example.contractpayments.payment.domain.IdempotencyKey;
import com.example.contractpayments.profile.adapter.out.persistence.ProfileEntity;
import com.example.contractpayments.profile.adapter.out.persistence.ProfileJpaRepository;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public final class JpaPaymentTransaction implements PaymentTransaction {
    private final JobJpaRepository jobs;
    private final ContractJpaRepository contracts;
    private final ProfileJpaRepository profiles;
    private final PaymentJpaRepository payments;
    private final OutboxJpaRepository outbox;
    private final TransactionTemplate transaction;

    public JpaPaymentTransaction(JobJpaRepository jobs, ContractJpaRepository contracts,
            ProfileJpaRepository profiles, PaymentJpaRepository payments,
            OutboxJpaRepository outbox, PlatformTransactionManager manager) {
        this.jobs = jobs; this.contracts = contracts; this.profiles = profiles;
        this.payments = payments; this.outbox = outbox;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transaction.setTimeout(10);
    }

    @Override public PaymentResult pay(UUID jobId, IdempotencyKey idempotencyKey) {
        String key = idempotencyKey.value();
        try {
            return transaction.execute(status -> payLocked(jobId, key));
        } catch (DataIntegrityViolationException error) {
            if (isIdempotencyConflict(error)) {
                PaymentEntity winner = payments.findByIdempotencyKey(key).orElseThrow(() -> error);
                return replay(winner, jobId);
            }
            throw error;
        }
    }

    private PaymentResult payLocked(UUID jobId, String key) {
        var prior = payments.findByIdempotencyKey(key);
        if (prior.isPresent()) return replay(prior.get(), jobId);
        JobEntity job = jobs.findLocked(jobId).orElseThrow(() ->
            failure(Kind.NOT_FOUND, "JOB_NOT_FOUND", "Job not found."));
        var committed = payments.findByIdempotencyKey(key);
        if (committed.isPresent()) return replay(committed.get(), jobId);
        if (!"OPEN".equals(job.status)) throw failure(Kind.CONFLICT, "JOB_ALREADY_PAID", "This job has already been paid.");

        ContractEntity contract = contracts.findLocked(job.contractId).orElseThrow(() ->
            failure(Kind.NOT_FOUND, "CONTRACT_NOT_FOUND", "Contract not found."));
        if (!"ACTIVE".equals(contract.status)) throw failure(Kind.CONFLICT, "CONTRACT_INACTIVE", "Contract is not active.");

        List<UUID> ids = List.of(contract.clientId, contract.contractorId).stream().sorted().toList();
        ProfileEntity first = profiles.findLocked(ids.get(0)).orElseThrow();
        ProfileEntity second = profiles.findLocked(ids.get(1)).orElseThrow();
        ProfileEntity client = first.id.equals(contract.clientId) ? first : second;
        ProfileEntity contractor = first.id.equals(contract.contractorId) ? first : second;
        if (client.balanceCents < job.amountCents) throw failure(Kind.UNPROCESSABLE, "INSUFFICIENT_FUNDS", "Client has insufficient funds.");
        if (contractor.balanceCents > Integer.MAX_VALUE - job.amountCents) throw failure(Kind.UNPROCESSABLE, "BALANCE_LIMIT", "Contractor balance would overflow.");

        client.balanceCents -= job.amountCents;
        contractor.balanceCents += job.amountCents;
        job.status = "PAID";
        Instant now = Instant.now();
        PaymentEntity payment = payments.saveAndFlush(new PaymentEntity(UUID.randomUUID(), jobId, job.amountCents, key));
        UUID eventId = UUID.randomUUID();
        outbox.save(new OutboxEntity(eventId, jobId, eventPayload(eventId, payment, job, contract, now), now));
        return result(payment, false, now);
    }

    private PaymentResult replay(PaymentEntity payment, UUID requestedJob) {
        if (!payment.jobId.equals(requestedJob)) throw failure(Kind.CONFLICT, "IDEMPOTENCY_CONFLICT", "Key belongs to a different job.");
        return result(payment, true, Instant.now());
    }

    private PaymentResult result(PaymentEntity entity, boolean replayed, Instant createdAt) {
        var value = new com.example.contractpayments.payment.domain.Payment(entity.id, entity.jobId,
            entity.amountCents, entity.status, createdAt);
        return new PaymentResult(value, replayed);
    }

    private String eventPayload(UUID eventId, PaymentEntity payment, JobEntity job, ContractEntity contract, Instant occurredAt) {
        return "{\"eventId\":\"%s\",\"eventType\":\"JobPaid\",\"eventVersion\":1,"
            .concat("\"occurredAt\":\"%s\",\"jobId\":\"%s\",\"contractId\":\"%s\",")
            .concat("\"paymentId\":\"%s\",\"clientId\":\"%s\",\"contractorId\":\"%s\",\"amountCents\":%d}")
            .formatted(eventId, occurredAt, job.id, contract.id, payment.id, contract.clientId,
                contract.contractorId, job.amountCents);
    }

    private PaymentException failure(Kind kind, String code, String message) {
        return new PaymentException(kind, code, message);
    }

    private boolean isIdempotencyConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())
                    && sql.getMessage().contains("payments_idempotency_key_unique")) return true;
        }
        return false;
    }
}

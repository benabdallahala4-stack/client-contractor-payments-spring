package com.example.contractpayments.job.adapter.in.web;

import com.example.contractpayments.contract.adapter.out.persistence.ContractJpaRepository;
import com.example.contractpayments.job.adapter.out.persistence.JobEntity;
import com.example.contractpayments.job.adapter.out.persistence.JobJpaRepository;
import com.example.contractpayments.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/jobs")
public class JobController {
    private final JobJpaRepository jobs;
    private final ContractJpaRepository contracts;
    public JobController(JobJpaRepository jobs, ContractJpaRepository contracts) {
        this.jobs = jobs; this.contracts = contracts;
    }
    public record Request(@NotNull UUID contractId, @NotBlank @Size(max=120) String title,
                          @Min(1) @Max(Integer.MAX_VALUE) int amountCents) { }
    public record View(UUID id, UUID contractId, String title, int amountCents, String status) {
        static View of(JobEntity j) { return new View(j.id, j.contractId, j.title, j.amountCents, j.status); }
    }
    @PostMapping ResponseEntity<View> create(@Valid @RequestBody Request request) {
        var contract = contracts.findById(request.contractId()).orElseThrow(() -> missing("CONTRACT_NOT_FOUND"));
        if (!"ACTIVE".equals(contract.status)) throw new ApiException(HttpStatus.CONFLICT, "CONTRACT_INACTIVE", "Contract is not active.");
        var job = jobs.save(new JobEntity(UUID.randomUUID(), contract.id, request.title().trim(), request.amountCents()));
        return ResponseEntity.created(URI.create("/jobs/" + job.id)).body(View.of(job));
    }
    @GetMapping("/{id}") View get(@PathVariable UUID id) {
        return View.of(jobs.findById(id).orElseThrow(() -> missing("JOB_NOT_FOUND")));
    }
    private ApiException missing(String code) { return new ApiException(HttpStatus.NOT_FOUND, code, "Resource not found."); }
}

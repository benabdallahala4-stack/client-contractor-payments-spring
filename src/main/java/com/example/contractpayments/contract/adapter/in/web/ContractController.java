package com.example.contractpayments.contract.adapter.in.web;

import com.example.contractpayments.contract.adapter.out.persistence.ContractEntity;
import com.example.contractpayments.contract.adapter.out.persistence.ContractJpaRepository;
import com.example.contractpayments.profile.adapter.out.persistence.ProfileJpaRepository;
import com.example.contractpayments.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contracts")
public class ContractController {
    private final ContractJpaRepository contracts;
    private final ProfileJpaRepository profiles;
    public ContractController(ContractJpaRepository contracts, ProfileJpaRepository profiles) {
        this.contracts = contracts; this.profiles = profiles;
    }
    public record Request(@NotNull UUID clientId, @NotNull UUID contractorId,
                          @NotBlank @Size(max=120) String title) { }
    public record View(UUID id, UUID clientId, UUID contractorId, String title, String status) {
        static View of(ContractEntity c) { return new View(c.id, c.clientId, c.contractorId, c.title, c.status); }
    }
    @PostMapping ResponseEntity<View> create(@Valid @RequestBody Request request) {
        var client = profiles.findById(request.clientId()).orElseThrow(() -> missing("PROFILE_NOT_FOUND"));
        var contractor = profiles.findById(request.contractorId()).orElseThrow(() -> missing("PROFILE_NOT_FOUND"));
        if (client.id.equals(contractor.id) || !"CLIENT".equals(client.role) || !"CONTRACTOR".equals(contractor.role)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PARTIES",
                "A contract needs distinct client and contractor profiles.");
        }
        var contract = contracts.save(new ContractEntity(UUID.randomUUID(), client.id, contractor.id, request.title().trim()));
        return ResponseEntity.created(URI.create("/contracts/" + contract.id)).body(View.of(contract));
    }
    @GetMapping("/{id}") View get(@PathVariable UUID id) {
        return View.of(contracts.findById(id).orElseThrow(() -> missing("CONTRACT_NOT_FOUND")));
    }
    private ApiException missing(String code) { return new ApiException(HttpStatus.NOT_FOUND, code, "Resource not found."); }
}

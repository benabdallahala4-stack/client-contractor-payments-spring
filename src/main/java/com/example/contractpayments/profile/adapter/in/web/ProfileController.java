package com.example.contractpayments.profile.adapter.in.web;

import com.example.contractpayments.profile.adapter.out.persistence.ProfileEntity;
import com.example.contractpayments.profile.adapter.out.persistence.ProfileJpaRepository;
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
@RequestMapping("/profiles")
public class ProfileController {
    private final ProfileJpaRepository profiles;
    public ProfileController(ProfileJpaRepository profiles) { this.profiles = profiles; }
    public enum Role { CLIENT, CONTRACTOR }
    public record Request(@NotBlank @Size(max=100) String name, @NotNull Role role,
                          @Min(0) @Max(Integer.MAX_VALUE) int balanceCents) { }
    public record View(UUID id, String name, String role, int balanceCents) {
        static View of(ProfileEntity p) { return new View(p.id, p.name, p.role, p.balanceCents); }
    }
    @PostMapping ResponseEntity<View> create(@Valid @RequestBody Request request) {
        var profile = profiles.save(new ProfileEntity(UUID.randomUUID(), request.name().trim(),
            request.role().name(), request.balanceCents()));
        return ResponseEntity.created(URI.create("/profiles/" + profile.id)).body(View.of(profile));
    }
    @GetMapping("/{id}") View get(@PathVariable UUID id) {
        return View.of(profiles.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
            "PROFILE_NOT_FOUND", "Profile not found.")));
    }
}

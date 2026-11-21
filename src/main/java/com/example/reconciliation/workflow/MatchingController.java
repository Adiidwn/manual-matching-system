package com.example.reconciliation.workflow;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static com.example.reconciliation.workflow.MatchDtos.*;

@Tag(name = "Matching", description = "Manual matching: maker, checker, signer")
@SecurityRequirements({
        @SecurityRequirement(name = "X-User"),
        @SecurityRequirement(name = "X-Role")
})
@RestController
@RequestMapping("/api/matches")
public class MatchingController {
    private final MatchingService service;
    public MatchingController(MatchingService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchView create(@RequestHeader("X-User") String user, @RequestHeader("X-Role") String role,
                            @Valid @RequestBody CreateRequest request) {
        requireRole(role, "MAKER");
        return service.create(request, requireUser(user));
    }

    @PostMapping("/{id}/checker-decision")
    public MatchView checker(@PathVariable Long id, @RequestHeader("X-User") String user,
                             @RequestHeader("X-Role") String role, @Valid @RequestBody DecisionRequest request) {
        requireRole(role, "CHECKER");
        return service.checkerDecision(id, requireUser(user), request);
    }

    @PostMapping("/{id}/signer-decision")
    public MatchView signer(@PathVariable Long id, @RequestHeader("X-User") String user,
                            @RequestHeader("X-Role") String role, @Valid @RequestBody DecisionRequest request) {
        requireRole(role, "SIGNER");
        return service.signerDecision(id, requireUser(user), request);
    }

    @GetMapping
    public Page<MatchView> list(
            @RequestParam(required = false) MatchStatus status,
            @RequestParam(required = false) String maker,
            @RequestParam(required = false) String checker,
            @RequestParam(required = false) String signer,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(status, maker, checker, signer, page, size);
    }

    @GetMapping("/{id}")
    public MatchDetails get(@PathVariable Long id) { return service.get(id); }

    private void requireRole(String actual, String expected) {
        if (!expected.equalsIgnoreCase(actual)) throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.FORBIDDEN, "This action requires role " + expected);
    }
    private String requireUser(String user) {
        if (user == null || user.isBlank() || user.length() > 100)
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User is required");
        return user.trim();
    }
}

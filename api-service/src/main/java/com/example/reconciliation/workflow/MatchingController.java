package com.example.reconciliation.workflow;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.reconciliation.middleware.Actor;
import com.example.reconciliation.middleware.RequireRole;
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
    @RequireRole("MAKER")
    public MatchView create(@RequestAttribute(Actor.REQUEST_ATTRIBUTE) Actor actor,
                            @Valid @RequestBody CreateRequest request) {
        return service.create(request, actor.user());
    }

    @PostMapping("/{id}/checker-decision")
    @RequireRole("CHECKER")
    public MatchView checker(@PathVariable Long id, @RequestAttribute(Actor.REQUEST_ATTRIBUTE) Actor actor,
                             @Valid @RequestBody DecisionRequest request) {
        return service.checkerDecision(id, actor.user(), request);
    }

    @PostMapping("/{id}/signer-decision")
    @RequireRole("SIGNER")
    public MatchView signer(@PathVariable Long id, @RequestAttribute(Actor.REQUEST_ATTRIBUTE) Actor actor,
                            @Valid @RequestBody DecisionRequest request) {
        return service.signerDecision(id, actor.user(), request);
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
}

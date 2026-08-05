package dev.vedaaxis.api.plan;

import dev.vedaaxis.api.rule.AbilityCatalog;
import dev.vedaaxis.api.rule.AbilityDefinition;
import dev.vedaaxis.api.rule.RuleValidationResult;
import dev.vedaaxis.api.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PlanController {
    private final PlanService planService;
    private final AbilityCatalog abilityCatalog;

    public PlanController(PlanService planService, AbilityCatalog abilityCatalog) {
        this.planService = planService;
        this.abilityCatalog = abilityCatalog;
    }

    @PostMapping("/plans")
    PlanService.PlanDetails create(@Valid @RequestBody CreateRequest request) {
        return planService.create(CurrentUser.id(), new PlanService.CreatePlanRequest(
                request.name(), request.encounterId(), request.strategyTag(), request.trackMode()));
    }

    @GetMapping("/plans")
    List<PlanService.PlanSummary> list() {
        return planService.list(CurrentUser.id());
    }

    @PostMapping("/plans/{planId}/copy")
    PlanService.PlanDetails copy(@PathVariable UUID planId) {
        return planService.copy(CurrentUser.id(), planId);
    }

    @GetMapping("/plans/{planId}")
    PlanService.PlanDetails get(@PathVariable UUID planId) {
        return planService.get(CurrentUser.id(), planId);
    }

    @PutMapping("/plans/{planId}")
    PlanService.PlanDetails update(
            @PathVariable UUID planId,
            @Valid @RequestBody UpdateRequest request) {
        return planService.updateDraft(CurrentUser.id(), planId,
                new PlanService.UpdatePlanRequest(request.name(), request.snapshot()));
    }

    @PostMapping("/plans/{planId}/validate")
    RuleValidationResult validate(@PathVariable UUID planId) {
        return planService.validate(CurrentUser.id(), planId);
    }

    @PostMapping("/plans/{planId}/publish")
    PlanService.PublishedPlan publish(@PathVariable UUID planId) {
        return planService.publish(CurrentUser.id(), planId);
    }

    @GetMapping("/plans/{planId}/versions")
    List<PlanService.PlanVersionSummary> versions(@PathVariable UUID planId) {
        return planService.versions(CurrentUser.id(), planId);
    }

    @PostMapping("/plans/{planId}/versions/{version}/rollback")
    PlanService.PublishedPlan rollback(@PathVariable UUID planId, @PathVariable int version) {
        return planService.rollback(CurrentUser.id(), planId, version);
    }

    @GetMapping("/shares/{shareCode}")
    PlanService.SharedPlan shared(@PathVariable String shareCode) {
        return planService.shared(shareCode);
    }

    @GetMapping("/abilities")
    Collection<AbilityDefinition> abilities() {
        return abilityCatalog.all();
    }

    @GetMapping("/runtime/plans/match")
    PlanService.RuntimePlan matchRuntimePlan(
            @RequestParam UUID encounterId,
            @RequestParam @NotBlank String strategyTag,
            @RequestParam TrackMode trackMode) {
        return planService.matchRuntimePlan(CurrentUser.id(), encounterId, strategyTag, trackMode);
    }

    public record CreateRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull UUID encounterId,
            @NotBlank @Size(max = 80) String strategyTag,
            @NotNull TrackMode trackMode) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull @Valid PlanSnapshot snapshot) {
    }
}

package dev.vedaaxis.api.ai;

import dev.vedaaxis.api.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans/{planId}/ai-candidates")
public class AiCandidateController {
    private final AiCandidateService service;

    public AiCandidateController(AiCandidateService service) {
        this.service = service;
    }

    @PostMapping
    AiCandidateService.AiCandidate generate(
            @PathVariable UUID planId,
            @Valid @RequestBody GenerateRequest request) {
        return service.generate(
                CurrentUser.id(),
                planId,
                request.instruction(),
                request.mode(),
                request.focusTrackId(),
                request.preserveExistingAssignments(),
                request.allowGcdActions(),
                request.locale());
    }

    public record GenerateRequest(
            @Size(max = 2000) String instruction,
            AiCandidateService.OptimizationMode mode,
            UUID focusTrackId,
            Boolean preserveExistingAssignments,
            Boolean allowGcdActions,
            @Size(max = 12) String locale) {
    }
}

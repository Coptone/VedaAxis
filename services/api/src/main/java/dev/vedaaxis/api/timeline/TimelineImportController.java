package dev.vedaaxis.api.timeline;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/timeline-imports")
public class TimelineImportController {
    private final MSpecImportService service;

    public TimelineImportController(MSpecImportService service) {
        this.service = service;
    }

    @PostMapping("/m-spec")
    MSpecImportCandidate importMSpec(@Valid @RequestBody ImportRequest request) {
        return service.importCandidate(request.sourceUrl(), request.includeRecommendations());
    }

    public record ImportRequest(
            @NotBlank @Size(max = 500) String sourceUrl,
            boolean includeRecommendations) {
    }
}

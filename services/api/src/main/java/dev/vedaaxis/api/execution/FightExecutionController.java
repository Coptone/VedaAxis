package dev.vedaaxis.api.execution;

import dev.vedaaxis.api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fight-executions")
public class FightExecutionController {
    private final FightExecutionService service;

    public FightExecutionController(FightExecutionService service) {
        this.service = service;
    }

    @PostMapping
    FightExecutionService.UploadResult upload(@Valid @RequestBody ExecutionBatch batch) {
        return service.upload(CurrentUser.id(), batch);
    }

    @GetMapping
    List<FightExecutionService.ExecutionSummary> recent(
            @RequestParam(defaultValue = "20") int limit) {
        return service.recent(CurrentUser.id(), limit);
    }

    @GetMapping("/stats")
    FightExecutionService.ExecutionStats stats(
            @RequestParam(defaultValue = "100") int limit) {
        return service.stats(CurrentUser.id(), limit);
    }
}

package dev.vedaaxis.api.execution;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import dev.vedaaxis.api.common.ApiException;
import dev.vedaaxis.api.plan.PlanMapper;
import dev.vedaaxis.api.plan.PlanService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Service
public class FightExecutionService {
    private final FightExecutionMapper mapper;
    private final PlanMapper planMapper;
    private final PlanService planService;
    private final ObjectMapper objectMapper;

    public FightExecutionService(
            FightExecutionMapper mapper,
            PlanMapper planMapper,
            PlanService planService,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.planMapper = planMapper;
        this.planService = planService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UploadResult upload(UUID userId, ExecutionBatch batch) {
        FightExecutionRow existing = mapper.find(userId.toString(), batch.fightExecutionId().toString()).orElse(null);
        if (existing != null) {
            return new UploadResult(UUID.fromString(existing.id()), true, existing.uploadedAt());
        }

        planService.get(userId, batch.planId());
        if (batch.planVersion() < 1
                || planMapper.findVersion(batch.planId().toString(), batch.planVersion()).isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PLAN_VERSION_NOT_PUBLISHED", "执行记录引用的计划版本不存在");
        }

        Instant uploadedAt = Instant.now();
        FightExecutionRow row = new FightExecutionRow(
                batch.fightExecutionId().toString(), userId.toString(), batch.planId().toString(),
                batch.planVersion(), batch.result().name(), write(batch), batch.startedAt(), batch.endedAt(), uploadedAt);
        mapper.insert(row);
        return new UploadResult(batch.fightExecutionId(), false, uploadedAt);
    }

    public List<ExecutionSummary> recent(UUID userId, int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), 100);
        return mapper.listRecent(userId.toString(), limit).stream()
                .map(row -> new ExecutionSummary(
                        UUID.fromString(row.id()), UUID.fromString(row.planId()), row.planVersion(),
                        ExecutionBatch.Result.valueOf(row.result()), row.startedAt(), row.endedAt(), row.uploadedAt()))
                .toList();
    }

    public ExecutionStats stats(UUID userId, int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), 500);
        List<FightExecutionRow> rows = mapper.listRecent(userId.toString(), limit);
        Map<ExecutionBatch.AssignmentState, Long> stateCounts = new EnumMap<>(ExecutionBatch.AssignmentState.class);
        long observedCount = 0;
        long observedOffsetTotal = 0;
        long assignmentCount = 0;
        for (FightExecutionRow row : rows) {
            ExecutionBatch batch = read(row.payloadJson());
            assignmentCount += batch.assignments().size();
            for (ExecutionBatch.AssignmentExecution assignment : batch.assignments()) {
                stateCounts.merge(assignment.state(), 1L, Long::sum);
                if (assignment.observedOffsetMs() != null) {
                    observedCount++;
                    observedOffsetTotal += assignment.observedOffsetMs();
                }
            }
        }
        return new ExecutionStats(
                rows.size(),
                rows.stream().filter(row -> "CLEAR".equals(row.result())).count(),
                rows.stream().filter(row -> "WIPE".equals(row.result())).count(),
                assignmentCount,
                stateCounts,
                observedCount == 0 ? null : Math.round((double) observedOffsetTotal / observedCount));
    }

    private String write(ExecutionBatch batch) {
        try {
            return objectMapper.writeValueAsString(batch);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize fight execution", exception);
        }
    }

    private ExecutionBatch read(String json) {
        try {
            return objectMapper.readValue(json, ExecutionBatch.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored fight execution is invalid", exception);
        }
    }

    public record UploadResult(UUID fightExecutionId, boolean duplicate, Instant uploadedAt) {
    }

    public record ExecutionSummary(
            UUID fightExecutionId,
            UUID planId,
            int planVersion,
            ExecutionBatch.Result result,
            Instant startedAt,
            Instant endedAt,
            Instant uploadedAt) {
    }

    public record ExecutionStats(
            long fights,
            long clears,
            long wipes,
            long assignments,
            Map<ExecutionBatch.AssignmentState, Long> stateCounts,
            Long averageObservedOffsetMs) {
    }
}

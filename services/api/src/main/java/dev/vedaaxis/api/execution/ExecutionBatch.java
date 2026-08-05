package dev.vedaaxis.api.execution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExecutionBatch(
        @NotBlank String schemaVersion,
        @NotNull UUID fightExecutionId,
        @NotNull UUID planId,
        int planVersion,
        @NotNull Instant startedAt,
        @NotNull Instant endedAt,
        @NotNull Result result,
        @NotNull @Valid List<AssignmentExecution> assignments) {

    @AssertTrue(message = "endedAt 必须晚于 startedAt")
    public boolean isTimeRangeValid() {
        return startedAt == null || endedAt == null || !endedAt.isBefore(startedAt);
    }

    @AssertTrue(message = "schemaVersion 必须为 1.0")
    public boolean isSchemaVersionSupported() {
        return "1.0".equals(schemaVersion);
    }

    public enum Result {
        CLEAR, WIPE, ABANDONED
    }

    public enum AssignmentState {
        SUCCESS, EARLY, MISSED, LATE, INVALID, CANCELLED
    }

    public enum Confirmation {
        ACTION_EFFECT, STATUS_APPLY, COOLDOWN_CHANGE, NONE
    }

    public record AssignmentExecution(
            @NotNull UUID assignmentId,
            @NotNull AssignmentState state,
            Long observedOffsetMs,
            @NotNull Confirmation confirmation,
            boolean availableAtHighlight,
            @Size(max = 500) String reason) {
    }
}

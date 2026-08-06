package dev.vedaaxis.api.plan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PlanSnapshot(
        @NotBlank String schemaVersion,
        @NotBlank String minimumPluginVersion,
        @NotNull UUID planId,
        @Min(1) int planVersion,
        @NotNull UUID timelineId,
        @Min(1) int timelineVersion,
        @NotNull UUID encounterId,
        @Min(1) long territoryId,
        @NotBlank @Size(max = 80) String strategyTag,
        @NotNull TrackMode trackMode,
        @NotNull @Valid Source source,
        @NotNull @Valid List<TimelinePhase> phases,
        @NotNull @Valid List<TimelineMechanic> mechanics,
        @NotNull @Valid List<TimelineAnchor> anchors,
        @NotNull @Valid List<ExecutionTrack> tracks,
        @NotNull @Valid List<Assignment> assignments) {

    public PlanSnapshot {
        phases = phases == null ? List.of() : List.copyOf(phases);
        mechanics = mechanics == null ? List.of() : List.copyOf(mechanics);
        anchors = anchors == null ? List.of() : List.copyOf(anchors);
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
    }

    public record Source(SourceKind kind, @Size(max = 500) String reference, Confidence confidence) {
    }

    public enum SourceKind {
        PERSONAL,
        PUBLIC_TEMPLATE,
        AI_CANDIDATE,
        IMPORTED
    }

    public enum Confidence {
        POC_PENDING,
        UNVERIFIED,
        REVIEWED,
        VERIFIED
    }

    public record TimelinePhase(
            @NotNull UUID phaseId,
            @Size(max = 120) String externalId,
            @NotBlank @Size(max = 120) String name,
            @Min(0) long plannedAtMs,
            @NotNull Confidence confidence,
            @Min(0) long durationMs,
            TimingMode timingMode) {
        public TimelinePhase(UUID phaseId, String externalId, String name, long plannedAtMs, Confidence confidence) {
            this(phaseId, externalId, name, plannedAtMs, confidence, 0, TimingMode.ABSOLUTE);
        }
    }

    public enum TimingMode {
        ABSOLUTE,
        RELATIVE
    }

    public record TimelineMechanic(
            @NotNull UUID mechanicId,
            @Size(max = 120) String externalId,
            @NotBlank @Size(max = 40) String phase,
            @NotBlank @Size(max = 160) String name,
            @Min(0) long plannedAtMs,
            @Min(0) long durationMs,
            @NotNull MechanicType type,
            @NotNull DamageType damageType,
            @NotBlank @Size(max = 80) String target,
            @Min(1) Long actionId,
            @NotNull Confidence confidence) {
    }

    public enum MechanicType {
        MECHANIC,
        TANK_BUSTER,
        RAIDWIDE
    }

    public enum DamageType {
        UNKNOWN,
        MAGICAL,
        PHYSICAL,
        SPECIAL
    }

    public record TimelineAnchor(
            @NotNull UUID anchorId,
            @Min(1) long actionId,
            @Min(1) int occurrence,
            @Min(0) long plannedAtMs,
            long offsetMs,
            @NotBlank @Size(max = 40) String phase,
            @NotNull AnchorKind kind) {
    }

    public enum AnchorKind {
        CAST_START,
        ACTION_EFFECT,
        STATUS_GAIN
    }

    public record ExecutionTrack(
            @NotNull UUID trackId,
            @NotNull TrackSlot slot,
            @NotNull Set<Integer> allowedJobIds,
            @Size(max = 80) String displayName) {
    }

    public record Assignment(
            @NotNull UUID assignmentId,
            @NotNull UUID mechanicId,
            @NotNull UUID trackId,
            @Min(1) long actionId,
            UUID anchorId,
            long highlightAtMs,
            long earliestUseAtMs,
            long latestUseAtMs,
            long impactAtMs,
            boolean locked,
            @NotNull ConfirmationStrategy confirmationStrategy,
            @NotNull List<Fallback> fallbacks,
            UUID targetTrackId) {
        public Assignment(
                UUID assignmentId, UUID mechanicId, UUID trackId, long actionId, UUID anchorId,
                long highlightAtMs, long earliestUseAtMs, long latestUseAtMs, long impactAtMs,
                boolean locked, ConfirmationStrategy confirmationStrategy, List<Fallback> fallbacks) {
            this(assignmentId, mechanicId, trackId, actionId, anchorId, highlightAtMs, earliestUseAtMs,
                    latestUseAtMs, impactAtMs, locked, confirmationStrategy, fallbacks, null);
        }
    }

    public record Fallback(@NotNull UUID trackId, @Min(1) long actionId) {
    }
}

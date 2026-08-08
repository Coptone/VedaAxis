package dev.vedaaxis.api.plan;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import dev.vedaaxis.api.common.ApiException;
import dev.vedaaxis.api.rule.PlanRuleEngine;
import dev.vedaaxis.api.rule.RuleValidationResult;
import dev.vedaaxis.api.security.SecureTokens;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

@Service
public class PlanService {
    private final PlanMapper mapper;
    private final ObjectMapper objectMapper;
    private final PlanRuleEngine ruleEngine;
    private final DefaultPlanProvider defaultPlanProvider;

    public PlanService(
            PlanMapper mapper,
            ObjectMapper objectMapper,
            PlanRuleEngine ruleEngine,
            DefaultPlanProvider defaultPlanProvider) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.ruleEngine = ruleEngine;
        this.defaultPlanProvider = defaultPlanProvider;
    }

    @Transactional
    public PlanDetails create(UUID ownerId, CreatePlanRequest request) {
        UUID planId = UUID.randomUUID();
        Instant now = Instant.now();
        PlanSnapshot snapshot = emptySnapshot(planId, request);
        String snapshotJson = write(snapshot);
        PlanRow row = new PlanRow(
                planId.toString(), ownerId.toString(), request.name().trim(), request.encounterId().toString(),
                request.territoryId(), request.strategyTag().trim(), request.trackMode().name(), snapshotJson, 0, now, now);
        mapper.insertPlan(row);
        return new PlanDetails(row, snapshot);
    }

    public List<PlanSummary> list(UUID ownerId) {
        return mapper.listByOwner(ownerId.toString()).stream().map(PlanSummary::from).toList();
    }

    @Transactional
    public PlanDetails copy(UUID ownerId, UUID sourcePlanId) {
        PlanRow sourcePlan = ownedPlan(ownerId, sourcePlanId);
        PlanSnapshot source = read(sourcePlan.draftJson());
        UUID newPlanId = UUID.randomUUID();
        Map<UUID, UUID> trackIds = new HashMap<>();
        List<PlanSnapshot.ExecutionTrack> tracks = source.tracks().stream()
                .map(track -> {
                    UUID newTrackId = UUID.randomUUID();
                    trackIds.put(track.trackId(), newTrackId);
                    return new PlanSnapshot.ExecutionTrack(
                            newTrackId, track.slot(), track.allowedJobIds(), track.displayName());
                })
                .toList();
        List<PlanSnapshot.Assignment> assignments = source.assignments().stream()
                .map(assignment -> new PlanSnapshot.Assignment(
                        UUID.randomUUID(), assignment.mechanicId(), trackIds.get(assignment.trackId()),
                        assignment.actionId(), assignment.anchorId(), assignment.highlightAtMs(),
                        assignment.earliestUseAtMs(), assignment.latestUseAtMs(), assignment.impactAtMs(),
                        assignment.locked(), assignment.confirmationStrategy(),
                        assignment.fallbacks().stream()
                                .map(fallback -> new PlanSnapshot.Fallback(
                                        trackIds.get(fallback.trackId()), fallback.actionId()))
                                .toList(),
                        assignment.targetTrackId() == null ? null : trackIds.get(assignment.targetTrackId())))
                .toList();
        PlanSnapshot copy = new PlanSnapshot(
                source.schemaVersion(), source.minimumPluginVersion(), newPlanId, 1,
                source.timelineId(), source.timelineVersion(), source.encounterId(), source.territoryId(), source.strategyTag(),
                source.trackMode(),
                new PlanSnapshot.Source(
                        PlanSnapshot.SourceKind.PERSONAL,
                        "copied-from:" + sourcePlanId,
                        PlanSnapshot.Confidence.UNVERIFIED),
                source.phases(), source.mechanics(), source.anchors(), tracks, assignments);
        Instant now = Instant.now();
        PlanRow row = new PlanRow(
                newPlanId.toString(), ownerId.toString(), sourcePlan.name() + " 副本", sourcePlan.encounterId(),
                sourcePlan.territoryId(), sourcePlan.strategyTag(), sourcePlan.trackMode(), write(copy), 0, now, now);
        mapper.insertPlan(row);
        return new PlanDetails(row, copy);
    }

    public PlanDetails get(UUID ownerId, UUID planId) {
        PlanRow row = ownedPlan(ownerId, planId);
        return new PlanDetails(row, read(row.draftJson()));
    }

    @Transactional
    public void delete(UUID ownerId, UUID planId) {
        ownedPlan(ownerId, planId);
        mapper.deleteVersions(planId.toString());
        if (mapper.deletePlan(planId.toString(), ownerId.toString()) != 1) {
            throw conflict();
        }
    }

    @Transactional
    public PlanDetails updateDraft(UUID ownerId, UUID planId, UpdatePlanRequest request) {
        PlanRow current = ownedPlan(ownerId, planId);
        PlanSnapshot authoritative = authoritativeSnapshot(current, request.snapshot(), current.latestVersion() + 1);
        Instant now = Instant.now();
        if (mapper.updateDraft(
                planId.toString(), ownerId.toString(), request.name().trim(), authoritative.strategyTag(),
                write(authoritative), now) != 1) {
            throw conflict();
        }
        PlanRow updated = new PlanRow(
                current.id(), current.ownerId(), request.name().trim(), current.encounterId(), current.territoryId(), authoritative.strategyTag(),
                current.trackMode(), write(authoritative), current.latestVersion(), current.createdAt(), now);
        return new PlanDetails(updated, authoritative);
    }

    public RuleValidationResult validate(UUID ownerId, UUID planId) {
        return ruleEngine.validate(get(ownerId, planId).snapshot());
    }

    @Transactional
    public PublishedPlan publish(UUID ownerId, UUID planId) {
        PlanRow current = ownedPlan(ownerId, planId);
        int nextVersion = current.latestVersion() + 1;
        PlanSnapshot snapshot = authoritativeSnapshot(current, read(current.draftJson()), nextVersion);
        RuleValidationResult validation = ruleEngine.validate(snapshot);
        if (!validation.valid()) {
            throw new RuleValidationException(validation);
        }
        String snapshotJson = write(snapshot);
        Instant now = Instant.now();
        if (mapper.advanceVersion(
                planId.toString(), ownerId.toString(), current.latestVersion(), nextVersion, snapshotJson, now) != 1) {
            throw conflict();
        }
        mapper.supersedeActive(planId.toString());
        String shareCode = SecureTokens.opaqueToken(12);
        PlanVersionRow version = new PlanVersionRow(
                UUID.randomUUID().toString(), planId.toString(), nextVersion, "ACTIVE", snapshotJson, shareCode, now);
        mapper.insertVersion(version);
        return new PublishedPlan(snapshot, shareCode, validation);
    }

    public List<PlanVersionSummary> versions(UUID ownerId, UUID planId) {
        ownedPlan(ownerId, planId);
        return mapper.listVersions(planId.toString()).stream()
                .map(row -> new PlanVersionSummary(row.versionNumber(), row.status(), row.shareCode(), row.createdAt()))
                .toList();
    }

    @Transactional
    public PublishedPlan rollback(UUID ownerId, UUID planId, int sourceVersion) {
        PlanRow current = ownedPlan(ownerId, planId);
        PlanVersionRow source = mapper.findVersion(planId.toString(), sourceVersion)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAN_VERSION_NOT_FOUND", "计划版本不存在"));
        int nextVersion = current.latestVersion() + 1;
        PlanSnapshot snapshot = authoritativeSnapshot(current, read(source.snapshotJson()), nextVersion);
        String snapshotJson = write(snapshot);
        Instant now = Instant.now();
        if (mapper.advanceVersion(planId.toString(), ownerId.toString(), current.latestVersion(), nextVersion, snapshotJson, now) != 1) {
            throw conflict();
        }
        mapper.supersedeActive(planId.toString());
        String shareCode = SecureTokens.opaqueToken(12);
        mapper.insertVersion(new PlanVersionRow(
                UUID.randomUUID().toString(), planId.toString(), nextVersion, "ACTIVE", snapshotJson, shareCode, now));
        return new PublishedPlan(snapshot, shareCode, RuleValidationResult.from(List.of()));
    }

    public SharedPlan shared(String shareCode) {
        PlanVersionRow version = mapper.findByShareCode(shareCode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SHARE_NOT_FOUND", "分享计划不存在"));
        PlanRow plan = mapper.findPlan(version.planId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "计划不存在"));
        return new SharedPlan(plan.name(), version.status(), read(version.snapshotJson()), version.createdAt());
    }

    public RuntimePlan matchRuntimePlan(
            UUID ownerId, long territoryId, String strategyTag, TrackMode trackMode) {
        return mapper.findLatestActiveMatchByTerritory(
                        ownerId.toString(), territoryId, strategyTag.trim(), trackMode.name())
                .map(version -> new RuntimePlan(read(version.snapshotJson()), version.createdAt()))
                .orElseGet(() -> defaultPlanProvider.match(territoryId, strategyTag, trackMode)
                        .map(snapshot -> new RuntimePlan(snapshot, Instant.EPOCH))
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.NOT_FOUND, "RUNTIME_PLAN_NOT_FOUND", "没有匹配的已发布个人计划")));
    }

    public RuntimePlan matchRuntimePlanByEncounter(
            UUID ownerId, UUID encounterId, String strategyTag, TrackMode trackMode) {
        PlanVersionRow version = mapper.findLatestActiveMatchByEncounter(
                        ownerId.toString(), encounterId.toString(), strategyTag.trim(), trackMode.name())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "RUNTIME_PLAN_NOT_FOUND", "没有匹配的已发布个人计划"));
        return new RuntimePlan(read(version.snapshotJson()), version.createdAt());
    }

    private PlanSnapshot emptySnapshot(UUID planId, CreatePlanRequest request) {
        boolean useDefaultTemplate = request.useDefaultTemplate() == null || request.useDefaultTemplate();
        PlanSnapshot snapshot;
        if (useDefaultTemplate && defaultPlanProvider.supports(request.territoryId(), request.strategyTag(), request.trackMode())) {
            snapshot = defaultPlanProvider.create(
                    planId, request.territoryId(), request.strategyTag(), request.trackMode());
        } else {
            List<PlanSnapshot.ExecutionTrack> tracks = request.trackMode().orderedSlots().stream()
                    .map(slot -> new PlanSnapshot.ExecutionTrack(UUID.randomUUID(), slot, Set.of(), slot.name()))
                    .toList();
            snapshot = new PlanSnapshot(
                    "1.3", "0.1.7", planId, 1, UUID.randomUUID(), 1, request.encounterId(), request.territoryId(),
                    request.strategyTag().trim(), request.trackMode(),
                    new PlanSnapshot.Source(PlanSnapshot.SourceKind.PERSONAL, null, PlanSnapshot.Confidence.UNVERIFIED),
                    List.of(), List.of(), List.of(), tracks, List.of());
        }
        return applyPartyJobs(snapshot, request);
    }

    private PlanSnapshot applyPartyJobs(PlanSnapshot snapshot, CreatePlanRequest request) {
        Map<TrackSlot, Integer> partyJobIds = request.partyJobIds() == null ? Map.of() : request.partyJobIds();
        if (partyJobIds.isEmpty()) {
            return snapshot;
        }
        validatePartyJobs(request.trackMode(), partyJobIds);
        List<PlanSnapshot.ExecutionTrack> tracks = snapshot.tracks().stream()
                .map(track -> {
                    Integer jobId = partyJobIds.get(track.slot());
                    if (jobId == null) {
                        return track;
                    }
                    return new PlanSnapshot.ExecutionTrack(
                            track.trackId(), track.slot(), Set.of(jobId), track.slot().name() + " · " + jobName(jobId));
                })
                .toList();
        return new PlanSnapshot(
                snapshot.schemaVersion(), snapshot.minimumPluginVersion(), snapshot.planId(), snapshot.planVersion(),
                snapshot.timelineId(), snapshot.timelineVersion(), snapshot.encounterId(), snapshot.territoryId(),
                snapshot.strategyTag(), snapshot.trackMode(), snapshot.source(), snapshot.phases(), snapshot.mechanics(),
                snapshot.anchors(), tracks, snapshot.assignments());
    }

    private void validatePartyJobs(TrackMode trackMode, Map<TrackSlot, Integer> partyJobIds) {
        Set<TrackSlot> expectedSlots = trackMode.slots();
        if (!partyJobIds.keySet().equals(expectedSlots)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PARTY_JOBS_INCOMPLETE", "需要为当前轨道模式的每个轨道选择职业");
        }
        for (Map.Entry<TrackSlot, Integer> entry : partyJobIds.entrySet()) {
            TrackSlot slot = entry.getKey();
            Integer jobId = entry.getValue();
            if (!jobNames().containsKey(jobId)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PARTY_JOB_UNKNOWN", "存在未知职业");
            }
            if (!jobFitsSlot(slot, jobId)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PARTY_JOB_SLOT_MISMATCH", "职业与轨道角色不匹配");
            }
        }
    }

    private boolean jobFitsSlot(TrackSlot slot, int jobId) {
        return switch (slot) {
            case T1, MT, ST -> tankJobs().contains(jobId);
            case H1, H2 -> healerJobs().contains(jobId);
            case D1, D2, D3, D4 -> dpsJobs().contains(jobId);
        };
    }

    private String jobName(int jobId) {
        return jobNames().get(jobId);
    }

    private Set<Integer> tankJobs() {
        return Set.of(19, 21, 32, 37);
    }

    private Set<Integer> healerJobs() {
        return Set.of(24, 28, 33, 40);
    }

    private Set<Integer> dpsJobs() {
        return Set.of(20, 22, 23, 25, 27, 30, 31, 34, 35, 38, 39, 41, 42);
    }

    private Map<Integer, String> jobNames() {
        return Map.ofEntries(
                Map.entry(19, "骑士"),
                Map.entry(21, "战士"),
                Map.entry(32, "暗黑骑士"),
                Map.entry(37, "绝枪战士"),
                Map.entry(24, "白魔法师"),
                Map.entry(28, "学者"),
                Map.entry(33, "占星术士"),
                Map.entry(40, "贤者"),
                Map.entry(20, "武僧"),
                Map.entry(22, "龙骑士"),
                Map.entry(30, "忍者"),
                Map.entry(34, "武士"),
                Map.entry(39, "钐镰客"),
                Map.entry(41, "蝰蛇剑士"),
                Map.entry(23, "吟游诗人"),
                Map.entry(31, "机工士"),
                Map.entry(38, "舞者"),
                Map.entry(25, "黑魔法师"),
                Map.entry(27, "召唤师"),
                Map.entry(35, "赤魔法师"),
                Map.entry(42, "绘灵法师"));
    }

    private PlanSnapshot authoritativeSnapshot(PlanRow plan, PlanSnapshot submitted, int version) {
        TrackMode trackMode = TrackMode.valueOf(plan.trackMode());
        String minimumPluginVersion = defaultPlanProvider.minimumPluginVersion(
                plan.territoryId(), plan.strategyTag(), trackMode, "0.1.7");
        List<PlanSnapshot.TimelinePhase> phases = submitted.phases().stream()
                .map(phase -> new PlanSnapshot.TimelinePhase(
                        phase.phaseId(), phase.externalId(), phase.name(), phase.plannedAtMs(), phase.confidence(),
                        phase.durationMs(), phase.timingMode() == null ? PlanSnapshot.TimingMode.ABSOLUTE : phase.timingMode()))
                .toList();
        return new PlanSnapshot(
                "1.3", minimumPluginVersion, UUID.fromString(plan.id()), version,
                submitted.timelineId(), submitted.timelineVersion(), UUID.fromString(plan.encounterId()),
                plan.territoryId(), submitted.strategyTag(), trackMode, submitted.source(),
                phases, submitted.mechanics(), submitted.anchors(), submitted.tracks(), submitted.assignments());
    }

    private PlanRow ownedPlan(UUID ownerId, UUID planId) {
        PlanRow plan = mapper.findPlan(planId.toString())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "计划不存在"));
        if (!plan.ownerId().equals(ownerId.toString())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "计划不存在");
        }
        return plan;
    }

    private String write(PlanSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize plan snapshot", exception);
        }
    }

    private PlanSnapshot read(String json) {
        try {
            return objectMapper.readValue(json, PlanSnapshot.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored plan snapshot is invalid", exception);
        }
    }

    private ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, "PLAN_CONCURRENT_UPDATE", "计划已被其他操作更新，请刷新后重试");
    }

    public record CreatePlanRequest(
            String name, UUID encounterId, long territoryId, String strategyTag, TrackMode trackMode,
            Boolean useDefaultTemplate, Map<TrackSlot, Integer> partyJobIds) {
    }

    public record UpdatePlanRequest(String name, PlanSnapshot snapshot) {
    }

    public record PlanDetails(PlanRow plan, PlanSnapshot snapshot) {
    }

    public record PlanSummary(
            UUID id, String name, UUID encounterId, long territoryId, String strategyTag, TrackMode trackMode,
            int latestVersion, Instant updatedAt) {
        static PlanSummary from(PlanRow row) {
            return new PlanSummary(
                    UUID.fromString(row.id()), row.name(), UUID.fromString(row.encounterId()), row.territoryId(), row.strategyTag(),
                    TrackMode.valueOf(row.trackMode()), row.latestVersion(), row.updatedAt());
        }
    }

    public record PublishedPlan(PlanSnapshot snapshot, String shareCode, RuleValidationResult validation) {
    }

    public record PlanVersionSummary(int version, String status, String shareCode, Instant createdAt) {
    }

    public record SharedPlan(String name, String status, PlanSnapshot snapshot, Instant publishedAt) {
    }

    public record RuntimePlan(PlanSnapshot snapshot, Instant publishedAt) {
    }
}

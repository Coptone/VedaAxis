package dev.vedaaxis.api.rule;

import dev.vedaaxis.api.plan.PlanSnapshot;
import dev.vedaaxis.api.plan.TrackSlot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PlanRuleEngine {
    private final AbilityCatalog abilityCatalog;

    public PlanRuleEngine(AbilityCatalog abilityCatalog) {
        this.abilityCatalog = abilityCatalog;
    }

    public RuleValidationResult validate(PlanSnapshot snapshot) {
        return validate(snapshot, abilityCatalog.load());
    }

    RuleValidationResult validate(PlanSnapshot snapshot, Map<Long, AbilityDefinition> abilities) {
        List<RuleIssue> issues = new ArrayList<>();
        validateTracks(snapshot, issues);
        Set<UUID> mechanicIds = validateMechanics(snapshot, issues);
        Set<UUID> anchorIds = validateAnchors(snapshot, issues);
        Map<UUID, PlanSnapshot.ExecutionTrack> tracks = snapshot.tracks().stream()
                .collect(HashMap::new, (map, track) -> map.put(track.trackId(), track), HashMap::putAll);
        Set<UUID> assignmentIds = new HashSet<>();

        for (PlanSnapshot.Assignment assignment : snapshot.assignments()) {
            String reference = assignment.assignmentId().toString();
            if (!assignmentIds.add(assignment.assignmentId())) {
                error(issues, "DUPLICATE_ASSIGNMENT", "任务 ID 重复", reference);
            }
            if (!mechanicIds.isEmpty() && !mechanicIds.contains(assignment.mechanicId())) {
                error(issues, "UNKNOWN_MECHANIC", "任务引用了不存在的时间轴机制", reference);
            }
            PlanSnapshot.ExecutionTrack track = tracks.get(assignment.trackId());
            if (track == null) {
                error(issues, "UNKNOWN_TRACK", "任务引用了不存在的执行轨道", reference);
                continue;
            }
            if (assignment.highlightAtMs() > assignment.earliestUseAtMs()) {
                error(issues, "HIGHLIGHT_AFTER_WINDOW", "高亮时间不得晚于允许窗口起点", reference);
            }
            if (assignment.anchorId() != null && !anchorIds.contains(assignment.anchorId())) {
                error(issues, "UNKNOWN_ANCHOR", "任务引用了不存在的时间轴锚点", reference);
            }
            if (assignment.earliestUseAtMs() > assignment.latestUseAtMs()) {
                error(issues, "INVALID_USE_WINDOW", "允许窗口起点不得晚于终点", reference);
            }
            if (assignment.latestUseAtMs() > assignment.impactAtMs()) {
                error(issues, "WINDOW_AFTER_IMPACT", "允许窗口终点不得晚于机制命中时间", reference);
            }
            AbilityDefinition ability = abilities.get(assignment.actionId());
            if (ability == null) {
                error(issues, "ABILITY_NOT_FOUND", "技能目录中不存在 Action ID " + assignment.actionId(), reference);
                continue;
            }
            if (!track.allowedJobIds().isEmpty() && track.allowedJobIds().stream().noneMatch(ability.jobIds()::contains)) {
                error(issues, "JOB_NOT_COMPATIBLE", ability.name() + " 与轨道职业不兼容", reference);
            }
            if (ability.durationMs() > 0 && assignment.earliestUseAtMs() + ability.durationMs() < assignment.impactAtMs()) {
                error(issues, "COVERAGE_GAP", ability.name() + " 在允许窗口最早使用时无法覆盖机制", reference);
            }
            if (assignment.confirmationStrategy() != null
                    && assignment.confirmationStrategy() != ability.confirmationStrategy()) {
                warning(issues, "CONFIRMATION_OVERRIDE", ability.name() + " 覆盖了目录中的默认生效判定方式", reference);
            }
        }

        validateCooldowns(snapshot.assignments(), abilities, issues);
        return RuleValidationResult.from(issues);
    }

    private Set<UUID> validateMechanics(PlanSnapshot snapshot, List<RuleIssue> issues) {
        Set<UUID> mechanicIds = new HashSet<>();
        for (PlanSnapshot.TimelineMechanic mechanic : snapshot.mechanics()) {
            if (!mechanicIds.add(mechanic.mechanicId())) {
                error(issues, "DUPLICATE_MECHANIC_ID", "时间轴机制 ID 重复", mechanic.mechanicId().toString());
            }
        }
        return mechanicIds;
    }

    private Set<UUID> validateAnchors(PlanSnapshot snapshot, List<RuleIssue> issues) {
        Set<UUID> anchorIds = new HashSet<>();
        Set<String> occurrences = new HashSet<>();
        for (PlanSnapshot.TimelineAnchor anchor : snapshot.anchors()) {
            String reference = anchor.anchorId().toString();
            if (!anchorIds.add(anchor.anchorId())) {
                error(issues, "DUPLICATE_ANCHOR_ID", "时间轴锚点 ID 重复", reference);
            }
            String occurrenceKey = anchor.kind() + ":" + anchor.actionId() + ":" + anchor.occurrence();
            if (!occurrences.add(occurrenceKey)) {
                error(issues, "DUPLICATE_ANCHOR_OCCURRENCE", "同一事件的发生序号重复", reference);
            }
        }
        return anchorIds;
    }

    private void validateTracks(PlanSnapshot snapshot, List<RuleIssue> issues) {
        Set<TrackSlot> actual = new HashSet<>();
        Set<UUID> trackIds = new HashSet<>();
        for (PlanSnapshot.ExecutionTrack track : snapshot.tracks()) {
            if (!actual.add(track.slot())) {
                error(issues, "DUPLICATE_TRACK_SLOT", "轨道位置重复：" + track.slot(), track.trackId().toString());
            }
            if (!trackIds.add(track.trackId())) {
                error(issues, "DUPLICATE_TRACK_ID", "轨道 ID 重复", track.trackId().toString());
            }
        }
        if (!actual.equals(snapshot.trackMode().slots())) {
            error(issues, "TRACK_MODE_MISMATCH",
                    snapshot.trackMode() + " 模式必须且只能包含 " + snapshot.trackMode().orderedSlots(),
                    snapshot.planId().toString());
        }
    }

    private void validateCooldowns(
            List<PlanSnapshot.Assignment> assignments,
            Map<Long, AbilityDefinition> abilities,
            List<RuleIssue> issues) {
        Map<String, List<PlanSnapshot.Assignment>> groups = new HashMap<>();
        for (PlanSnapshot.Assignment assignment : assignments) {
            groups.computeIfAbsent(assignment.trackId() + ":" + assignment.actionId(), ignored -> new ArrayList<>())
                    .add(assignment);
        }
        for (List<PlanSnapshot.Assignment> group : groups.values()) {
            group.sort(Comparator.comparingLong(PlanSnapshot.Assignment::earliestUseAtMs));
            for (int index = 1; index < group.size(); index++) {
                PlanSnapshot.Assignment previous = group.get(index - 1);
                PlanSnapshot.Assignment current = group.get(index);
                AbilityDefinition ability = abilities.get(current.actionId());
                if (ability == null) {
                    continue;
                }
                long recovery = Math.max(1, ability.maxCharges()) == 1
                        ? ability.cooldownMs()
                        : ability.cooldownMs() / ability.maxCharges();
                if (current.earliestUseAtMs() - previous.latestUseAtMs() < recovery) {
                    error(issues, "COOLDOWN_CONFLICT",
                            ability.name() + " 与上一任务的冷却窗口冲突", current.assignmentId().toString());
                }
            }
        }
    }

    private void error(List<RuleIssue> issues, String code, String message, String reference) {
        issues.add(new RuleIssue(RuleIssue.Severity.ERROR, code, message, reference));
    }

    private void warning(List<RuleIssue> issues, String code, String message, String reference) {
        issues.add(new RuleIssue(RuleIssue.Severity.WARNING, code, message, reference));
    }
}

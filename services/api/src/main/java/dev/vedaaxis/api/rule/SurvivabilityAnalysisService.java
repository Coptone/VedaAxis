package dev.vedaaxis.api.rule;

import dev.vedaaxis.api.plan.PlanSnapshot;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Builds a target-specific, conservative analysis from a plan snapshot. */
@Service
public class SurvivabilityAnalysisService {
    private final AbilityCatalog abilityCatalog;

    public SurvivabilityAnalysisService(AbilityCatalog abilityCatalog) {
        this.abilityCatalog = abilityCatalog;
    }

    public Analysis analyze(PlanSnapshot snapshot, UUID mechanicId, Request request) {
        return analyze(snapshot, mechanicId, request, abilityCatalog.load());
    }

    Analysis analyze(
            PlanSnapshot snapshot,
            UUID mechanicId,
            Request request,
            Map<Long, AbilityDefinition> abilitiesByActionId) {
        PlanSnapshot.TimelineMechanic mechanic = snapshot.mechanics().stream()
                .filter(candidate -> candidate.mechanicId().equals(mechanicId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown mechanic"));
        if (mechanic.damageProfile() == null) {
            return Analysis.calibrationRequired("该机制尚无可追溯的目标调整入伤校准数据。", List.of());
        }
        if (snapshot.tracks().stream().noneMatch(track -> track.trackId().equals(request.targetTrackId()))) {
            throw new IllegalArgumentException("unknown target track");
        }

        List<String> notices = new ArrayList<>();
        List<MitigationEffectProfile> effects = new ArrayList<>();
        Set<Long> includedActionIds = new HashSet<>();
        boolean conditionsConfirmed = true;
        for (PlanSnapshot.Assignment assignment : snapshot.assignments()) {
            AbilityDefinition ability = abilitiesByActionId.get(assignment.actionId());
            if (!isRelevantToImpact(assignment, ability, mechanic)) {
                continue;
            }
            MitigationEffectProfile effect = ability == null
                    ? MitigationEffectProfile.unknown(assignment.actionId())
                    : ability.effect();
            if (ability != null && !isUsableByAssignmentTrack(snapshot, assignment, ability, notices)) {
                conditionsConfirmed = false;
                continue;
            }
            if (ability != null && !coversImpact(ability, assignment, mechanic, notices)) {
                conditionsConfirmed = false;
                continue;
            }
            switch (effect.scope()) {
                case SELF -> {
                    if (assignment.trackId().equals(request.targetTrackId())) {
                        addEffectOnce(assignment.actionId(), effect, effects, includedActionIds, notices);
                    }
                }
                case TARGET -> {
                    if (request.targetTrackId().equals(assignment.targetTrackId())) {
                        addEffectOnce(assignment.actionId(), effect, effects, includedActionIds, notices);
                    } else if (assignment.targetTrackId() == null) {
                        conditionsConfirmed = false;
                        notices.add("单体减伤 " + assignment.actionId() + " 未指定目标轨道，未计入。");
                    }
                }
                case PARTY, GROUND_AREA -> {
                    if (request.partyRangeConfirmed()) {
                        addEffectOnce(assignment.actionId(), effect, effects, includedActionIds, notices);
                    } else {
                        conditionsConfirmed = false;
                        notices.add("范围减伤/地面减伤尚未确认目标在范围内，未计入。");
                    }
                }
                case ENEMY_TARGET, ENEMY_AREA -> {
                    if (request.enemyEffectConfirmed()) {
                        addEffectOnce(assignment.actionId(), effect, effects, includedActionIds, notices);
                    } else {
                        conditionsConfirmed = false;
                        notices.add("敌方减伤尚未确认施加在本次造成伤害的敌人上，未计入。");
                    }
                }
                case UNKNOWN -> {
                    conditionsConfirmed = false;
                    notices.add("技能 " + assignment.actionId() + " 的作用范围未知，未计入。");
                }
            }
        }

        SurvivabilityCalculator.Result result = SurvivabilityCalculator.evaluate(new SurvivabilityCalculator.Input(
                mechanic.damageProfile().amount(), mechanic.damageType(), request.currentHp(), request.maximumHp(),
                effects, conditionsConfirmed));
        notices.addAll(result.blockers());
        if (mechanic.damageProfile().basis() == PlanSnapshot.DamageBasis.OBSERVED_TARGET_ADJUSTED) {
            notices.add("此数值来自目标调整后的实战观测，不能作为跨装备/跨队伍的固定原始伤害，也不输出“100% 必定存活”。");
        }
        if (mechanic.damageProfile().statistic() != PlanSnapshot.DamageStatistic.MAX_OBSERVED) {
            notices.add("当前校准不是最大实测值，结果仅适合作为风险参考。");
        }
        return new Analysis(result.status().name(), false, result.incomingDamage(), result.damageAfterMitigation(),
                result.effectiveHp(), result.remainingHp(), result.modeledReduction(), List.copyOf(notices));
    }

    private static boolean isRelevantToImpact(
            PlanSnapshot.Assignment assignment,
            AbilityDefinition ability,
            PlanSnapshot.TimelineMechanic mechanic) {
        if (assignment.mechanicId().equals(mechanic.mechanicId())
                || assignment.impactAtMs() == mechanic.plannedAtMs()) {
            return true;
        }
        if (ability == null || ability.durationMs() <= 0) {
            return false;
        }
        long impactAtMs = assignmentImpactAtMs(assignment, mechanic);
        return assignment.earliestUseAtMs() <= impactAtMs
                && assignment.latestUseAtMs() <= impactAtMs
                && assignment.earliestUseAtMs() + ability.durationMs() >= impactAtMs;
    }

    private static boolean isUsableByAssignmentTrack(
            PlanSnapshot snapshot,
            PlanSnapshot.Assignment assignment,
            AbilityDefinition ability,
            List<String> notices) {
        PlanSnapshot.ExecutionTrack track = snapshot.tracks().stream()
                .filter(candidate -> candidate.trackId().equals(assignment.trackId()))
                .findFirst()
                .orElse(null);
        if (track == null) {
            notices.add(ability.name() + " 的执行轨道不存在，未计入。");
            return false;
        }
        if (!ability.jobIds().isEmpty() && !track.allowedJobIds().isEmpty()
                && ability.jobIds().stream().noneMatch(track.allowedJobIds()::contains)) {
            notices.add(ability.name() + " 与执行轨道允许职业不匹配，未计入。");
            return false;
        }
        return true;
    }

    private static void addEffectOnce(
            long actionId,
            MitigationEffectProfile effect,
            List<MitigationEffectProfile> effects,
            Set<Long> includedActionIds,
            List<String> notices) {
        if (includedActionIds.add(actionId)) {
            effects.add(effect);
        } else {
            notices.add("同一技能 " + actionId + " 安排了多次；按同名效果不叠加，仅计入一次。");
        }
    }

    private static boolean coversImpact(
            AbilityDefinition ability,
            PlanSnapshot.Assignment assignment,
            PlanSnapshot.TimelineMechanic mechanic,
            List<String> notices) {
        long impactAtMs = assignmentImpactAtMs(assignment, mechanic);
        if (assignment.earliestUseAtMs() > impactAtMs || assignment.latestUseAtMs() > impactAtMs) {
            notices.add(ability.name() + " 的允许施放窗口晚于机制命中，未计入。");
            return false;
        }
        if (ability.durationMs() <= 0) {
            notices.add(ability.name() + " 尚无可靠持续时间，未计入。");
            return false;
        }
        if (assignment.earliestUseAtMs() + ability.durationMs() < impactAtMs) {
            notices.add(ability.name() + " 在允许窗口最早施放时无法覆盖机制命中，未计入保守结果。");
            return false;
        }
        return true;
    }

    private static long assignmentImpactAtMs(
            PlanSnapshot.Assignment assignment,
            PlanSnapshot.TimelineMechanic mechanic) {
        return assignment.mechanicId().equals(mechanic.mechanicId())
                ? assignment.impactAtMs()
                : mechanic.plannedAtMs();
    }

    public record Request(
            @NotNull UUID targetTrackId,
            @Min(0) long currentHp,
            @Min(1) long maximumHp,
            boolean partyRangeConfirmed,
            boolean enemyEffectConfirmed) {
        @AssertTrue(message = "currentHp 不能大于 maximumHp")
        public boolean isCurrentHpWithinMaximum() {
            return currentHp <= maximumHp;
        }
    }

    public record Analysis(
            String status,
            boolean hardGuarantee,
            Long incomingDamage,
            Long damageAfterMitigation,
            Long effectiveHp,
            Long remainingHp,
            Double modeledReduction,
            List<String> notices) {
        static Analysis calibrationRequired(String notice, List<String> notices) {
            List<String> all = new ArrayList<>(notices);
            all.add(notice);
            return new Analysis("CALIBRATION_REQUIRED", false, null, null, null, null, null, List.copyOf(all));
        }
    }
}

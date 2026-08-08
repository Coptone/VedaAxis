package dev.vedaaxis.api.rule;

import dev.vedaaxis.api.plan.PlanSnapshot;
import dev.vedaaxis.api.plan.TrackSlot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Calculates the post-mitigation damage of every calibrated plan mechanic.
 *
 * <p>This is a planning preview, so arranged party/ground/enemy effects are
 * assumed to land. Raidwides use the worst result across every track;
 * tankbusters use the worst tank-track result. Barriers and HP do not change
 * the displayed hit value and therefore are not subtracted here.</p>
 */
@Service
public class DamageEstimateAnalysisService {
    private final SurvivabilityAnalysisService survivabilityAnalysisService;
    private final AbilityCatalog abilityCatalog;

    public DamageEstimateAnalysisService(
            SurvivabilityAnalysisService survivabilityAnalysisService,
            AbilityCatalog abilityCatalog) {
        this.survivabilityAnalysisService = survivabilityAnalysisService;
        this.abilityCatalog = abilityCatalog;
    }

    public List<MechanicEstimate> preview(PlanSnapshot snapshot) {
        Map<Long, AbilityDefinition> abilitiesByActionId = abilityCatalog.load();
        List<MechanicEstimate> estimates = new ArrayList<>();
        for (PlanSnapshot.TimelineMechanic mechanic : snapshot.mechanics()) {
            if (mechanic.damageProfile() == null) {
                estimates.add(MechanicEstimate.calibrationRequired(mechanic.mechanicId()));
                continue;
            }
            List<PlanSnapshot.ExecutionTrack> targets = targetTracks(snapshot, mechanic);
            List<TargetEstimate> targetEstimates = targets.stream()
                    .map(track -> analyzeTarget(snapshot, mechanic, track, abilitiesByActionId))
                    .filter(estimate -> estimate.analysis().damageAfterMitigation() != null)
                    .toList();
            if (targetEstimates.isEmpty()) {
                estimates.add(MechanicEstimate.calibrationRequired(mechanic.mechanicId()));
                continue;
            }
            TargetEstimate worst = targetEstimates.stream()
                    .max(Comparator.comparingLong(estimate -> estimate.analysis().damageAfterMitigation()))
                    .orElseThrow();
            SurvivabilityAnalysisService.Analysis analysis = worst.analysis();
            Set<String> notices = new LinkedHashSet<>(analysis.notices());
            notices.add("按计划假设范围减伤、地面减伤和敌方减伤均成功覆盖；实战复盘需用执行事件再次确认。");
            notices.add("显示值是整段机制对单个目标的减伤后预计伤害；护盾吸收和命中前治疗不从该数字中扣除。");
            if (mechanic.damageType() == PlanSnapshot.DamageType.UNKNOWN) {
                notices.add("伤害属性未知，物理/魔法专用减伤未计入。");
            }
            String status = analysis.status().equals("SPECIAL_CASE_REVIEW_REQUIRED")
                    ? "SPECIAL_CASE_REVIEW_REQUIRED"
                    : "CALCULATED";
            estimates.add(new MechanicEstimate(
                    mechanic.mechanicId(), status,
                    mechanic.damageProfile().amount(), analysis.damageAfterMitigation(),
                    analysis.modeledReduction(), risk(mechanic.type(), analysis.damageAfterMitigation()),
                    worst.track().trackId(), worst.track().slot(),
                    mechanic.damageProfile().sampleCount(), mechanic.damageProfile().statistic(),
                    mechanic.damageProfile().source(), List.copyOf(notices)));
        }
        return List.copyOf(estimates);
    }

    private TargetEstimate analyzeTarget(
            PlanSnapshot snapshot,
            PlanSnapshot.TimelineMechanic mechanic,
            PlanSnapshot.ExecutionTrack track,
            Map<Long, AbilityDefinition> abilitiesByActionId) {
        SurvivabilityAnalysisService.Analysis analysis = survivabilityAnalysisService.analyze(
                snapshot, mechanic.mechanicId(),
                new SurvivabilityAnalysisService.Request(track.trackId(), 1, 1, true, true),
                abilitiesByActionId);
        return new TargetEstimate(track, analysis);
    }

    private static List<PlanSnapshot.ExecutionTrack> targetTracks(
            PlanSnapshot snapshot, PlanSnapshot.TimelineMechanic mechanic) {
        if (mechanic.type() != PlanSnapshot.MechanicType.TANK_BUSTER && !isAutoAttack(mechanic.name())) {
            return snapshot.tracks();
        }
        List<PlanSnapshot.ExecutionTrack> tanks = snapshot.tracks().stream()
                .filter(track -> track.slot() == TrackSlot.MT
                        || track.slot() == TrackSlot.ST
                        || track.slot() == TrackSlot.T1)
                .toList();
        if (tanks.isEmpty()) {
            return snapshot.tracks();
        }
        if (isDualTankTarget(mechanic.target())) {
            return tanks;
        }
        if (isOffTankTarget(mechanic.target())) {
            List<PlanSnapshot.ExecutionTrack> offTanks = tanks.stream()
                    .filter(track -> track.slot() == TrackSlot.ST)
                    .toList();
            return offTanks.isEmpty() ? List.of(tanks.getFirst()) : offTanks;
        }
        List<PlanSnapshot.ExecutionTrack> mainTanks = tanks.stream()
                .filter(track -> track.slot() == TrackSlot.MT || track.slot() == TrackSlot.T1)
                .toList();
        return mainTanks.isEmpty() ? List.of(tanks.getFirst()) : mainTanks;
    }

    private static boolean isAutoAttack(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase();
        return normalized.equals("攻击") || normalized.startsWith("攻击 ")
                || normalized.equals("attack") || normalized.startsWith("attack ");
    }

    private static boolean isDualTankTarget(String target) {
        String normalized = target == null ? "" : target.trim().toLowerCase();
        return normalized.contains("一二仇")
                || normalized.contains("双坦")
                || normalized.contains("两名坦克")
                || normalized.contains("2坦")
                || normalized.contains("two tanks")
                || normalized.contains("both tanks")
                || normalized.contains("mt/st");
    }

    private static boolean isOffTankTarget(String target) {
        String normalized = target == null ? "" : target.trim().toLowerCase();
        return normalized.equals("st")
                || normalized.contains("副坦")
                || normalized.contains("二仇")
                || normalized.contains("off tank")
                || normalized.contains("off-tank")
                || normalized.contains("offtank");
    }

    static RiskLevel risk(PlanSnapshot.MechanicType type, long damageAfterMitigation) {
        return switch (type) {
            case RAIDWIDE -> damageAfterMitigation <= 100_000
                    ? RiskLevel.GREEN
                    : damageAfterMitigation <= 190_000 ? RiskLevel.YELLOW : RiskLevel.RED;
            case TANK_BUSTER -> damageAfterMitigation <= 200_000
                    ? RiskLevel.GREEN
                    : damageAfterMitigation < 290_000 ? RiskLevel.YELLOW : RiskLevel.RED;
            case MECHANIC -> RiskLevel.UNCLASSIFIED;
        };
    }

    public record PreviewRequest(@NotNull @Valid PlanSnapshot snapshot) {
    }

    public record MechanicEstimate(
            UUID mechanicId,
            String status,
            Long baselineDamage,
            Long damageAfterMitigation,
            Double modeledReduction,
            RiskLevel riskLevel,
            UUID worstTrackId,
            TrackSlot worstTrackSlot,
            Integer sampleCount,
            PlanSnapshot.DamageStatistic statistic,
            String source,
            List<String> notices) {
        static MechanicEstimate calibrationRequired(UUID mechanicId) {
            return new MechanicEstimate(
                    mechanicId, "CALIBRATION_REQUIRED", null, null, null,
                    RiskLevel.CALIBRATION_REQUIRED, null, null, null, null, null,
                    List.of("该机制尚无可追溯的基准总伤害。"));
        }
    }

    public enum RiskLevel {
        GREEN,
        YELLOW,
        RED,
        UNCLASSIFIED,
        CALIBRATION_REQUIRED
    }

    private record TargetEstimate(
            PlanSnapshot.ExecutionTrack track,
            SurvivabilityAnalysisService.Analysis analysis) {
    }
}

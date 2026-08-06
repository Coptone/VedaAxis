package dev.vedaaxis.api.rule;

import dev.vedaaxis.api.plan.PlanSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, conservative arithmetic for a single already-targeted hit.
 *
 * <p>The caller must first resolve target, range and enemy scope. The input
 * damage is deliberately target-adjusted: a log-derived value already includes
 * the target's defence/role context, so this class never pretends to recreate
 * the inaccessible boss damage formula from one FFLogs sample.</p>
 */
public final class SurvivabilityCalculator {
    private SurvivabilityCalculator() {
    }

    public static Result evaluate(Input input) {
        if (input.incomingDamage() <= 0 || input.currentHp() < 0 || input.maximumHp() <= 0) {
            throw new IllegalArgumentException("damage and maximum HP must be positive, current HP must be non-negative");
        }

        List<String> blockers = new ArrayList<>();
        List<MitigationEffectProfile> effects = selectStackingGroups(input.effects(), input.damageType(), blockers);
        double multiplier = 1d;
        long additionalHp = 0;
        long maxHpBarrier = 0;
        boolean specialCase = false;

        for (MitigationEffectProfile effect : effects) {
            if (effect.invulnerability()) {
                specialCase = true;
                blockers.add("无敌技能必须按该机制是否属于“多数攻击”人工确认，未作为数值减伤计入。");
                continue;
            }
            int reduction = reductionFor(effect, input.damageType());
            if (reduction > 0) {
                multiplier *= (100d - reduction) / 100d;
            }
            additionalHp += percentOf(input.maximumHp(), effect.maximumHpIncreasePercent());
            maxHpBarrier += percentOf(input.maximumHp(), effect.maximumHpBarrierPercent());
            if (effect.barrierCurePotency() > 0) {
                blockers.add("存在以治疗威力计算的护盾，未计入数值：" + effect.barrierCurePotency() + " 威力。");
            }
            if (effect.calculationReadiness() == MitigationEffectProfile.CalculationReadiness.NO_DIRECT_MITIGATION) {
                blockers.add("存在治疗/增疗效果，未计入命中前实际治疗量。");
            }
            if (effect.calculationReadiness() == MitigationEffectProfile.CalculationReadiness.UNMODELED) {
                blockers.add("存在未建模的技能效果。");
            }
        }

        if (!input.conditionsConfirmed()) {
            blockers.add("范围、目标和施放生效条件尚未确认。");
        }

        long damageAfterMitigation = (long) Math.ceil(input.incomingDamage() * multiplier);
        long effectiveHp = input.currentHp() + additionalHp + maxHpBarrier;
        long remainingHp = effectiveHp - damageAfterMitigation;
        Status status = specialCase
                ? Status.SPECIAL_CASE_REVIEW_REQUIRED
                : remainingHp >= 0 ? Status.SURVIVES_WITH_MODELED_EFFECTS : Status.INSUFFICIENT_WITH_MODELED_EFFECTS;
        return new Result(status, input.incomingDamage(), damageAfterMitigation, effectiveHp, remainingHp,
                1d - multiplier, List.copyOf(blockers));
    }

    private static List<MitigationEffectProfile> selectStackingGroups(
            List<MitigationEffectProfile> effects, PlanSnapshot.DamageType damageType, List<String> blockers) {
        Map<String, List<MitigationEffectProfile>> grouped = new LinkedHashMap<>();
        List<MitigationEffectProfile> selected = new ArrayList<>();
        for (MitigationEffectProfile effect : effects == null ? List.<MitigationEffectProfile>of() : effects) {
            if (effect.stackingGroup().isBlank()) {
                selected.add(effect);
            } else {
                grouped.computeIfAbsent(effect.stackingGroup(), ignored -> new ArrayList<>()).add(effect);
            }
        }
        for (Map.Entry<String, List<MitigationEffectProfile>> entry : grouped.entrySet()) {
            List<MitigationEffectProfile> candidates = entry.getValue();
            candidates.sort(Comparator.comparingInt((MitigationEffectProfile effect) -> reductionFor(effect, damageType))
                    .reversed());
            selected.add(candidates.getFirst());
            if (candidates.size() > 1) {
                blockers.add("同一不可叠加组 " + entry.getKey() + " 安排了 " + candidates.size()
                        + " 个效果；仅按最强的一个计入。");
            }
        }
        return selected;
    }

    private static int reductionFor(MitigationEffectProfile effect, PlanSnapshot.DamageType damageType) {
        int specific = switch (damageType) {
            case PHYSICAL -> effect.physicalDamageReductionPercent();
            case MAGICAL -> effect.magicalDamageReductionPercent();
            case SPECIAL, UNKNOWN -> 0;
        };
        return Math.max(effect.allDamageReductionPercent(), specific);
    }

    private static long percentOf(long amount, int percentage) {
        return Math.round(amount * (percentage / 100d));
    }

    public record Input(
            long incomingDamage,
            PlanSnapshot.DamageType damageType,
            long currentHp,
            long maximumHp,
            List<MitigationEffectProfile> effects,
            boolean conditionsConfirmed) {
        public Input {
            effects = effects == null ? List.of() : List.copyOf(effects);
        }
    }

    public record Result(
            Status status,
            long incomingDamage,
            long damageAfterMitigation,
            long effectiveHp,
            long remainingHp,
            double modeledReduction,
            List<String> blockers) {
        /**
         * No result is a universal 100% guarantee. Only a future
         * FORMULA_VERIFIED damage profile plus live condition evidence may be
         * presented as a hard guarantee by a caller.
         */
        public boolean survivesWithModeledEffects() {
            return status == Status.SURVIVES_WITH_MODELED_EFFECTS;
        }
    }

    public enum Status {
        SURVIVES_WITH_MODELED_EFFECTS,
        INSUFFICIENT_WITH_MODELED_EFFECTS,
        SPECIAL_CASE_REVIEW_REQUIRED
    }
}

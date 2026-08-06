package dev.vedaaxis.api.rule;

import java.util.List;

/**
 * A reviewed description of the mitigation-relevant part of an Action.
 *
 * <p>Values are intentionally limited to effects that can be represented
 * without guessing a player's healing stat, critical heal, target position,
 * or boss target. The calculator can use direct reductions and max-HP based
 * barriers; potency barriers and invulnerability still remain visible, but
 * require their additional runtime inputs before contributing to a numerical
 * survival claim.</p>
 */
public record MitigationEffectProfile(
        Scope scope,
        int allDamageReductionPercent,
        int physicalDamageReductionPercent,
        int magicalDamageReductionPercent,
        int maximumHpIncreasePercent,
        int maximumHpBarrierPercent,
        int barrierCurePotency,
        boolean invulnerability,
        String stackingGroup,
        CalculationReadiness calculationReadiness,
        List<String> conditions,
        String source,
        String confidence) {

    public MitigationEffectProfile {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        stackingGroup = stackingGroup == null ? "" : stackingGroup;
    }

    public static MitigationEffectProfile unknown(long actionId) {
        return new MitigationEffectProfile(
                Scope.UNKNOWN, 0, 0, 0, 0, 0, 0, false, "",
                CalculationReadiness.UNMODELED,
                List.of("Action " + actionId + " 尚未录入减伤效果；不会用于生存结论。"),
                "VedaAxis effect catalog", "UNVERIFIED");
    }

    public enum Scope {
        SELF,
        TARGET,
        PARTY,
        GROUND_AREA,
        ENEMY_TARGET,
        ENEMY_AREA,
        UNKNOWN
    }

    public enum CalculationReadiness {
        DIRECT_REDUCTION,
        MAX_HP_BARRIER,
        REQUIRES_HEALING_STATS,
        INVULNERABILITY_SPECIAL_CASE,
        NO_DIRECT_MITIGATION,
        UNMODELED
    }
}

package dev.vedaaxis.api.rule;

import dev.vedaaxis.api.plan.PlanSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SurvivabilityCalculatorTest {
    private final AbilityEffectCatalog catalog = new AbilityEffectCatalog();

    @Test
    void stacksDamageReductionsMultiplicativelyUsingTheDamageAttribute() {
        SurvivabilityCalculator.Result result = SurvivabilityCalculator.evaluate(new SurvivabilityCalculator.Input(
                100_000, PlanSnapshot.DamageType.MAGICAL, 100_000, 100_000,
                List.of(catalog.profile(7549), catalog.profile(7560)), true));

        assertThat(result.damageAfterMitigation()).isEqualTo(85_500);
        assertThat(result.modeledReduction()).isCloseTo(0.145d, org.assertj.core.data.Offset.offset(0.000_001d));
        assertThat(result.survivesWithModeledEffects()).isTrue();
    }

    @Test
    void doesNotDoubleCountAnExplicitlyNonStackingGroup() {
        SurvivabilityCalculator.Result result = SurvivabilityCalculator.evaluate(new SurvivabilityCalculator.Input(
                100_000, PlanSnapshot.DamageType.MAGICAL, 100_000, 100_000,
                List.of(catalog.profile(24298), catalog.profile(24303)), true));

        assertThat(result.damageAfterMitigation()).isEqualTo(90_000);
        assertThat(result.blockers()).anyMatch(value -> value.contains("SGE_KERACHOLE_TAUROCHOLE"));
    }

    @Test
    void includesMaxHpBarriersButLeavesPotencyBarriersAsAnExplicitBlocker() {
        SurvivabilityCalculator.Result result = SurvivabilityCalculator.evaluate(new SurvivabilityCalculator.Input(
                108_000, PlanSnapshot.DamageType.MAGICAL, 100_000, 100_000,
                List.of(catalog.profile(3540), catalog.profile(7432)), true));

        assertThat(result.effectiveHp()).isEqualTo(110_000);
        assertThat(result.remainingHp()).isEqualTo(2_000);
        assertThat(result.blockers()).anyMatch(value -> value.contains("500 威力"));
    }
}

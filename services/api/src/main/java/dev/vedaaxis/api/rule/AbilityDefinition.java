package dev.vedaaxis.api.rule;

import dev.vedaaxis.api.plan.ConfirmationStrategy;

import java.util.Set;

public record AbilityDefinition(
        long actionId,
        String name,
        String iconPath,
        Set<Integer> jobIds,
        long cooldownMs,
        int maxCharges,
        long durationMs,
        ConfirmationStrategy confirmationStrategy,
        String source,
        String confidence,
        CastCategory castCategory,
        MitigationEffectProfile effect) {

    public AbilityDefinition {
        castCategory = castCategory == null ? CastCategory.OGCD : castCategory;
    }

    public AbilityDefinition(
            long actionId,
            String name,
            String iconPath,
            Set<Integer> jobIds,
            long cooldownMs,
            int maxCharges,
            long durationMs,
            ConfirmationStrategy confirmationStrategy,
            String source,
            String confidence,
            MitigationEffectProfile effect) {
        this(actionId, name, iconPath, jobIds, cooldownMs, maxCharges, durationMs,
                confirmationStrategy, source, confidence, CastCategory.OGCD, effect);
    }

    public enum CastCategory {
        GCD,
        OGCD,
        UNKNOWN
    }
}

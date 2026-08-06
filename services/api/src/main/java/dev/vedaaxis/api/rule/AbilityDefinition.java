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
        String confidence) {
}

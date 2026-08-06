package dev.vedaaxis.api.rule;

public record AbilityRow(
        long actionId,
        String name,
        String iconPath,
        String jobIds,
        long cooldownMs,
        int maxCharges,
        long durationMs,
        String confirmationStrategy,
        String source,
        String confidence) {
    AbilityDefinition toDefinition(MitigationEffectProfile effect) {
        java.util.Set<Integer> jobs = jobIds == null || jobIds.isBlank()
                ? java.util.Set.of()
                : java.util.Arrays.stream(jobIds.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(Integer::valueOf)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new AbilityDefinition(
                actionId, name, iconPath, jobs, cooldownMs, maxCharges, durationMs,
                dev.vedaaxis.api.plan.ConfirmationStrategy.valueOf(confirmationStrategy), source, confidence, effect);
    }
}

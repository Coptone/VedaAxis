package dev.vedaaxis.api.plan;

import java.time.Instant;

public record PlanRow(
        String id,
        String ownerId,
        String name,
        String encounterId,
        long territoryId,
        String strategyTag,
        String trackMode,
        String draftJson,
        int latestVersion,
        Instant createdAt,
        Instant updatedAt) {
}

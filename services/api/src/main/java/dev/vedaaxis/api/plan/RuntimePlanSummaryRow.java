package dev.vedaaxis.api.plan;

import java.time.Instant;

public record RuntimePlanSummaryRow(
        String planId,
        String name,
        String encounterId,
        long territoryId,
        String strategyTag,
        String trackMode,
        int versionNumber,
        Instant publishedAt) {
}

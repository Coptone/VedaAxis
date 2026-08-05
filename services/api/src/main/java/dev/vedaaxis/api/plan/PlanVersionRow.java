package dev.vedaaxis.api.plan;

import java.time.Instant;

public record PlanVersionRow(
        String id,
        String planId,
        int versionNumber,
        String status,
        String snapshotJson,
        String shareCode,
        Instant createdAt) {
}

package dev.vedaaxis.api.execution;

import java.time.Instant;

public record FightExecutionRow(
        String id,
        String userId,
        String planId,
        int planVersion,
        String result,
        String payloadJson,
        Instant startedAt,
        Instant endedAt,
        Instant uploadedAt) {
}

package dev.vedaaxis.api.identity;

import java.time.Instant;

public record RefreshTokenRow(
        String id,
        String userId,
        String tokenHash,
        String audience,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt) {
}

package dev.vedaaxis.api.identity;

import java.time.Instant;

public record AuthorizedDeviceRow(
        String id,
        String userId,
        String name,
        Instant lastSeenAt,
        Instant revokedAt,
        Instant createdAt) {
}

package dev.vedaaxis.api.identity;

import java.time.Instant;

public record DeviceAuthorizationRow(
        String id,
        String deviceCodeHash,
        String userCode,
        String deviceName,
        String status,
        String userId,
        Instant expiresAt,
        Instant consumedAt,
        Instant createdAt) {
}

package dev.vedaaxis.api.identity;

import java.time.Instant;

public record TokenPair(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessTokenExpiresAt) {
}

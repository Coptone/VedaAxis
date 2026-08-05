package dev.vedaaxis.api.identity;

import java.time.Instant;

public record UserRow(String id, String email, String passwordHash, Instant createdAt) {
}

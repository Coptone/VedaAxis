package dev.vedaaxis.api.common;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String code,
        String message,
        List<FieldViolation> violations,
        Instant timestamp) {

    public record FieldViolation(String field, String message) {
    }
}

package dev.vedaaxis.api.plan;

import dev.vedaaxis.api.rule.RuleValidationResult;

public class RuleValidationException extends RuntimeException {
    private final RuleValidationResult validation;

    public RuleValidationException(RuleValidationResult validation) {
        super("Plan validation failed");
        this.validation = validation;
    }

    public RuleValidationResult validation() {
        return validation;
    }
}

package dev.vedaaxis.api.rule;

public record RuleIssue(Severity severity, String code, String message, String reference) {
    public enum Severity {
        ERROR,
        WARNING
    }
}

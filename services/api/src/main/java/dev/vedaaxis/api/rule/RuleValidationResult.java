package dev.vedaaxis.api.rule;

import java.util.List;

public record RuleValidationResult(boolean valid, List<RuleIssue> issues) {
    public static RuleValidationResult from(List<RuleIssue> issues) {
        boolean valid = issues.stream().noneMatch(issue -> issue.severity() == RuleIssue.Severity.ERROR);
        return new RuleValidationResult(valid, List.copyOf(issues));
    }
}

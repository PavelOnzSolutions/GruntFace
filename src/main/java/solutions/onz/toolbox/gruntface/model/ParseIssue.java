package solutions.onz.toolbox.gruntface.model;

public record ParseIssue(Severity severity, String message) {
    public enum Severity { WARNING, ERROR }
}

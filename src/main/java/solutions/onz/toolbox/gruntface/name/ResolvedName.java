package solutions.onz.toolbox.gruntface.name;

public record ResolvedName(String text, Confidence confidence) {
    public enum Confidence { LITERAL, EVALUATED, FALLBACK }
    public ResolvedName {
        if (text == null) throw new IllegalArgumentException("text required");
        if (confidence == null) throw new IllegalArgumentException("confidence required");
    }
}

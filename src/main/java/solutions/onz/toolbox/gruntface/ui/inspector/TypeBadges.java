package solutions.onz.toolbox.gruntface.ui.inspector;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public final class TypeBadges {

    private TypeBadges() {}

    public record TypeInfo(String display, String category, String fullType) {}

    public record FieldDecl(String name, String type, boolean optional, String defaultLiteral) {}

    public static TypeInfo classify(String typeExpr) {
        String t = typeExpr == null ? "" : typeExpr.trim();
        if (t.isEmpty()) return new TypeInfo("any", "any", "");
        return switch (t) {
            case "string" -> new TypeInfo("string", "string", t);
            case "number" -> new TypeInfo("number", "number", t);
            case "bool"   -> new TypeInfo("bool",   "bool",   t);
            case "any"    -> new TypeInfo("any",    "any",    t);
            default       -> classifyComposite(t);
        };
    }

    private static TypeInfo classifyComposite(String t) {
        int paren = t.indexOf('(');
        if (paren <= 0) return new TypeInfo(t, "other", t);
        String head = t.substring(0, paren);
        String inner = stripOuterParen(t, paren);

        return switch (head) {
            case "list"   -> new TypeInfo("list<"  + collapseInner(inner) + ">", "list",   t);
            case "set"    -> new TypeInfo("set<"   + collapseInner(inner) + ">", "set",    t);
            case "map"    -> new TypeInfo("map<"   + collapseInner(inner) + ">", "map",    t);
            case "tuple"  -> new TypeInfo("tuple",                                 "tuple",  t);
            case "object" -> new TypeInfo("Object",                                "object", t);
            case "optional" -> {
                String innerType = stripOptionalDefault(inner);
                TypeInfo nested = classify(innerType);
                yield new TypeInfo(nested.display() + "?", nested.category(), t);
            }
            default -> new TypeInfo(head, "other", t);
        };
    }

    private static String collapseInner(String inner) {
        String s = inner.trim();
        if (s.startsWith("object("))   return "Object";
        if (s.startsWith("list("))     return "list";
        if (s.startsWith("set("))      return "set";
        if (s.startsWith("map("))      return "map";
        if (s.startsWith("tuple("))    return "tuple";
        if (s.startsWith("optional(")) {
            int p = s.indexOf('(');
            return collapseInner(stripOptionalDefault(stripOuterParen(s, p)));
        }
        if (s.length() > 18) return s.substring(0, 15) + "…";
        return s;
    }

    private static String stripOptionalDefault(String inner) {
        // optional(T) or optional(T, default) — return just T
        int depth = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == ',' && depth == 0) return inner.substring(0, i).trim();
        }
        return inner.trim();
    }

    /** Returns substring inside the outermost (...) — depth-aware. */
    private static String stripOuterParen(String t, int openParenIdx) {
        int close = matchingClose(t, openParenIdx, '(', ')');
        if (close < 0) return t.substring(openParenIdx + 1).trim();
        return t.substring(openParenIdx + 1, close).trim();
    }

    private static int matchingClose(String t, int openIdx, char open, char close) {
        int depth = 0;
        boolean inString = false;
        for (int i = openIdx; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '"') inString = !inString;
            if (inString) continue;
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** Make a styled badge node from a TypeInfo. Tooltip shows the full type when truncated. */
    public static Label makeBadge(TypeInfo info) {
        Label l = new Label(info.display());
        l.getStyleClass().addAll("type-badge", "type-badge-" + info.category());
        if (!info.fullType().isEmpty() && !info.fullType().equals(info.display())) {
            Tooltip tt = new Tooltip(prettyType(info.fullType()));
            tt.setShowDelay(Duration.millis(150));
            tt.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
            l.setTooltip(tt);
        }
        return l;
    }

    /** Convenience: build a badge directly from a type expression string. */
    public static Label makeBadge(String typeExpr) {
        return makeBadge(classify(typeExpr));
    }

    /**
     * Parse the inner field list of an `object({a=string, b=optional(number)})` type.
     * Returns empty list if the expression isn't an object schema.
     */
    public static List<FieldDecl> parseObjectFields(String typeExpr) {
        if (typeExpr == null) return List.of();
        String t = typeExpr.trim();
        if (!t.startsWith("object(")) return List.of();
        String inner = stripOuterParen(t, t.indexOf('('));
        // Inner should look like `{a=t1, b=t2}` — strip the braces
        inner = inner.trim();
        if (!inner.startsWith("{") || !inner.endsWith("}")) return List.of();
        inner = inner.substring(1, inner.length() - 1);

        List<String> parts = splitFieldDeclarations(inner);
        List<FieldDecl> out = new ArrayList<>();
        for (String raw : parts) {
            String part = raw.trim();
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String name = part.substring(0, eq).trim();
            String rawType = part.substring(eq + 1).trim();
            boolean optional = rawType.startsWith("optional(");
            String type = rawType;
            String def = "";
            if (optional) {
                int p = rawType.indexOf('(');
                String optInner = stripOuterParen(rawType, p);
                // optInner may be `T` or `T, default`
                int comma = findTopLevelComma(optInner);
                if (comma >= 0) {
                    type = optInner.substring(0, comma).trim();
                    def = optInner.substring(comma + 1).trim();
                } else {
                    type = optInner.trim();
                }
            }
            out.add(new FieldDecl(name, type, optional, def));
        }
        return out;
    }

    /** For map(object({...})), returns the field declarations of the inner object schema. */
    public static List<FieldDecl> parseMapObjectFields(String typeExpr) {
        if (typeExpr == null) return List.of();
        String t = typeExpr.trim();
        if (!t.startsWith("map(")) return List.of();
        String inner = stripOuterParen(t, t.indexOf('(')).trim();
        return parseObjectFields(inner);
    }

    public static boolean isMapOfObject(String typeExpr) {
        return !parseMapObjectFields(typeExpr).isEmpty();
    }

    private static List<String> splitTopLevelCommas(String s) {
        return splitTopLevel(s, /*splitOnNewlines=*/false);
    }

    /** Split on top-level commas AND newlines — HCL object schemas accept either separator. */
    private static List<String> splitFieldDeclarations(String s) {
        return splitTopLevel(s, /*splitOnNewlines=*/true);
    }

    private static List<String> splitTopLevel(String s, boolean splitOnNewlines) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') inString = !inString;
            if (inString) continue;
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == ',' && depth == 0) {
                out.add(s.substring(start, i));
                start = i + 1;
            } else if (splitOnNewlines && c == '\n' && depth == 0) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        if (start < s.length()) out.add(s.substring(start));
        return out;
    }

    private static int findTopLevelComma(String s) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') inString = !inString;
            if (inString) continue;
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == ',' && depth == 0) return i;
        }
        return -1;
    }

    /** Light pretty-print: insert newlines after `{` and `,` inside object({...}) so tooltips read nicely. */
    public static String prettyType(String typeExpr) {
        if (typeExpr == null || !typeExpr.contains("object(")) return typeExpr;
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < typeExpr.length(); i++) {
            char c = typeExpr.charAt(i);
            if (c == '"') inString = !inString;
            if (inString) { sb.append(c); continue; }
            if (c == '{') {
                depth++;
                sb.append(c).append('\n').append("  ".repeat(depth));
            } else if (c == '}') {
                depth--;
                sb.append('\n').append("  ".repeat(Math.max(0, depth))).append(c);
            } else if (c == ',' && depth > 0) {
                sb.append(c).append('\n').append("  ".repeat(depth));
                // skip following space if any
                if (i + 1 < typeExpr.length() && typeExpr.charAt(i + 1) == ' ') i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

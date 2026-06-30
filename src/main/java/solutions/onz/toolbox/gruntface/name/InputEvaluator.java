package solutions.onz.toolbox.gruntface.name;

import solutions.onz.toolbox.gruntface.model.InputValue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates a deliberately small subset of HCL expressions to a {@code String}. Supports:
 * <ol>
 *   <li>Quoted string literals: {@code "prod"}.</li>
 *   <li>{@code local.<name>} and {@code include.<inc>.locals.<name>} lookups.</li>
 *   <li>{@code var.<name>} lookup; recursive if the matched input is itself an expression.</li>
 *   <li>Interpolated strings (Task 11) — added incrementally.</li>
 * </ol>
 * Anything else returns {@code Optional.empty()}. The caller treats empty as "could not resolve".
 */
public final class InputEvaluator {

    private static final Pattern LOCAL = Pattern.compile("^local\\.([A-Za-z_][A-Za-z0-9_]*)$");
    private static final Pattern INCLUDE_LOCAL =
        Pattern.compile("^include\\.([A-Za-z_][A-Za-z0-9_]*)\\.locals\\.([A-Za-z_][A-Za-z0-9_]*)$");
    private static final Pattern VAR = Pattern.compile("^var\\.([A-Za-z_][A-Za-z0-9_]*)$");

    public Optional<String> eval(String expr, EvaluationContext ctx) {
        return eval(expr, ctx, new HashSet<>());
    }

    Optional<String> eval(String expr, EvaluationContext ctx, Set<String> visiting) {
        if (expr == null) return Optional.empty();
        String e = expr.trim();
        if (e.isEmpty()) return Optional.empty();

        // 1. Quoted literal "..." with optional interpolation (Task 11).
        if (e.startsWith("\"") && e.endsWith("\"") && e.length() >= 2) {
            String inner = e.substring(1, e.length() - 1);
            if (!inner.contains("${")) return Optional.of(unescape(inner));
            return interpolate(inner, ctx, visiting);
        }

        // 2a. local.<name>
        Matcher m = LOCAL.matcher(e);
        if (m.matches()) {
            String v = ctx.locals().get(m.group(1));
            return v == null ? Optional.empty() : Optional.of(v);
        }

        // 2b. include.<inc>.locals.<name>
        m = INCLUDE_LOCAL.matcher(e);
        if (m.matches()) {
            var inc = ctx.includeLocals().get(m.group(1));
            if (inc == null) return Optional.empty();
            String v = inc.get(m.group(2));
            return v == null ? Optional.empty() : Optional.of(v);
        }

        // 3. var.<name>
        m = VAR.matcher(e);
        if (m.matches()) {
            String name = m.group(1);
            if (!visiting.add("var:" + name)) return Optional.empty();  // cycle guard
            InputValue iv = ctx.inputs().get(name);
            if (iv == null) return Optional.empty();
            return switch (iv) {
                case InputValue.StringValue s -> Optional.of(s.value());
                case InputValue.NumberValue n -> Optional.of(n.literal());
                case InputValue.BoolValue b   -> Optional.of(Boolean.toString(b.value()));
                case InputValue.RawHcl r      -> eval(r.hcl(), ctx, visiting);
            };
        }

        return Optional.empty();
    }

    private Optional<String> interpolate(String inner, EvaluationContext ctx, Set<String> visiting) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < inner.length()) {
            int open = inner.indexOf("${", i);
            if (open < 0) {
                out.append(unescape(inner.substring(i)));
                return Optional.of(out.toString());
            }
            out.append(unescape(inner.substring(i, open)));
            int close = findMatchingClose(inner, open + 2);
            if (close < 0) return Optional.empty();
            String segExpr = inner.substring(open + 2, close).trim();
            Optional<String> segVal = eval(segExpr, ctx, visiting);
            if (segVal.isEmpty()) return Optional.empty();
            out.append(segVal.get());
            i = close + 1;
        }
        return Optional.of(out.toString());
    }

    /** Brace-and-quote tracking. Returns the index of the matching '}' or -1. */
    private static int findMatchingClose(String s, int from) {
        int depth = 1;
        boolean inString = false;
        boolean escape = false;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private static String unescape(String s) {
        // Minimal: \" → " and \\ → \. Anything else passes through verbatim.
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == '"' || n == '\\') { out.append(n); i++; continue; }
            }
            out.append(c);
        }
        return out.toString();
    }
}

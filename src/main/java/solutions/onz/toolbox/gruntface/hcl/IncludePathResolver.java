package solutions.onz.toolbox.gruntface.hcl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a small subset of Terragrunt path expressions against the filesystem.
 * Supported forms (composable):
 *   - `find_in_parent_folders("name")` — walks up from a starting directory looking for `name`.
 *   - `dirname(<expr>)` — parent dir of the resolved expression.
 *   - `"literal/path"` — string literal.
 *   - `"${<expr>}/suffix"` — single interpolation followed by literal suffix.
 *
 * Anything more complex returns Optional.empty().
 */
public final class IncludePathResolver {

    private IncludePathResolver() {}

    public static Optional<Path> resolve(String expr, Path unitFile) {
        if (expr == null) return Optional.empty();
        String trimmed = expr.trim();
        return resolveExpr(trimmed, unitFile);
    }

    private static Optional<Path> resolveExpr(String expr, Path unitFile) {
        // String literal or interpolated string: starts and ends with "
        if (expr.startsWith("\"") && endsWithUnescapedQuote(expr)) {
            String inner = expr.substring(1, expr.length() - 1);
            if (!containsInterpolation(inner)) {
                // Plain literal
                String literal = inner;
                Path unitDir = unitFile.getParent();
                if (literal.startsWith("/") || (literal.length() > 1 && literal.charAt(1) == ':')) {
                    return Optional.of(Path.of(literal));
                }
                return unitDir == null ? Optional.of(Path.of(literal)) : Optional.of(unitDir.resolve(literal).normalize());
            }
            return resolveInterpolatedString(inner, unitFile);
        }

        // find_in_parent_folders("name")
        Matcher fipf = Pattern.compile("^find_in_parent_folders\\(\\s*\"([^\"]+)\"\\s*\\)$").matcher(expr);
        if (fipf.matches()) {
            String target = fipf.group(1);
            return findInParentFolders(unitFile, target);
        }

        // dirname(<expr>)
        if (expr.startsWith("dirname(") && expr.endsWith(")")) {
            String inner = expr.substring("dirname(".length(), expr.length() - 1).trim();
            // Validate the parens match correctly
            if (isBalancedParens(inner)) {
                Optional<Path> innerResolved = resolveExpr(inner, unitFile);
                return innerResolved.map(Path::getParent);
            }
        }

        return Optional.empty();
    }

    /**
     * Handle the contents (inside the outer quotes) of an interpolated HCL string with exactly
     * one ${...} substitution surrounded by literal text. We track brace depth and quote state
     * to find the matching `}` even when the inner expression contains nested double quotes
     * (e.g. `find_in_parent_folders("root.hcl")`).
     */
    private static Optional<Path> resolveInterpolatedString(String inner, Path unitFile) {
        int open = inner.indexOf("${");
        if (open < 0) return Optional.empty();
        int close = findMatchingInterpolationClose(inner, open + 2);
        if (close < 0) return Optional.empty();

        String prefix = inner.substring(0, open);
        String exprInside = inner.substring(open + 2, close).trim();
        String suffix = inner.substring(close + 1);

        // Only the simple pattern (empty prefix + literal suffix, no further ${...}) is supported.
        if (containsInterpolation(suffix)) return Optional.empty();

        Optional<Path> resolvedExpr = resolveExpr(exprInside, unitFile);
        if (resolvedExpr.isEmpty()) return Optional.empty();

        Path base = resolvedExpr.get();
        if (prefix.isEmpty()) {
            String suffixClean = suffix.startsWith("/") ? suffix.substring(1) : suffix;
            if (suffixClean.isEmpty()) return Optional.of(base.normalize());
            return Optional.of(base.resolve(suffixClean).normalize());
        }
        // Non-empty prefix is uncommon for our patterns. Best-effort: combine textually.
        String combined = prefix + base.toString().replace('\\', '/') + suffix;
        return Optional.of(Path.of(combined).normalize());
    }

    /**
     * Starting at `from` (the index just after the opening `${`), scan forward tracking
     * brace depth and quote state to find the matching `}`. Returns -1 if unmatched.
     */
    private static int findMatchingInterpolationClose(String s, int from) {
        int depth = 1; // we are inside one ${ ... }
        boolean inString = false;
        boolean escape = false;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** True if `s` contains an unescaped `${`. */
    private static boolean containsInterpolation(String s) {
        boolean escape = false;
        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (c == '$' && s.charAt(i + 1) == '{') return true;
        }
        return false;
    }

    /** True if `s` starts with `"` and ends with an unescaped `"`, ignoring any nested quoting. */
    private static boolean endsWithUnescapedQuote(String s) {
        if (s.length() < 2) return false;
        if (s.charAt(s.length() - 1) != '"') return false;
        // Count trailing backslashes
        int bs = 0;
        for (int i = s.length() - 2; i >= 0 && s.charAt(i) == '\\'; i--) bs++;
        return bs % 2 == 0;
    }

    /** Confirms parens are balanced in `s` (outside string literals). */
    private static boolean isBalancedParens(String s) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth < 0) return false;
            }
        }
        return depth == 0;
    }

    private static Optional<Path> findInParentFolders(Path startFile, String target) {
        Path dir = startFile.getParent();
        while (dir != null) {
            Path candidate = dir.resolve(target);
            if (Files.exists(candidate)) return Optional.of(candidate);
            dir = dir.getParent();
        }
        return Optional.empty();
    }
}

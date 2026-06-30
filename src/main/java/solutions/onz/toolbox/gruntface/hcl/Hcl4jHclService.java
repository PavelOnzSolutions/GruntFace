package solutions.onz.toolbox.gruntface.hcl;

import com.bertramlabs.plugins.hcl4j.HCLParser;
import solutions.onz.toolbox.gruntface.model.*;
import solutions.onz.toolbox.gruntface.model.Module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Hcl4jHclService implements HclService {

    @Override
    public Unit parseUnit(Path terragruntHcl) throws IOException {
        String text = Files.readString(terragruntHcl);
        Map<String, Object> root;
        List<ParseIssue> issues = new ArrayList<>();
        try {
            root = new HCLParser().parse(text);
        } catch (Exception e) {
            return new Unit(
                terragruntHcl,
                deriveName(terragruntHcl),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(new ParseIssue(ParseIssue.Severity.ERROR, "HCL parse failed: " + e.getMessage())),
                text
            );
        }

        Optional<String> source = extractSource(root, text);
        List<Dependency> deps = extractDependencies(root);
        Map<String, InputValue> inputs = extractInputs(root, text);
        Optional<ByteRange> inputsRange = InputsBlockLocator.locate(text);
        List<IncludeBlock> includes = extractIncludes(root, text);

        return new Unit(
            terragruntHcl,
            deriveName(terragruntHcl),
            source,
            Optional.empty(),
            inputs,
            inputsRange,
            deps,
            includes,
            issues,
            text
        );
    }

    @Override
    public Module parseModule(Path moduleDir) throws IOException {
        List<Variable> variables = new ArrayList<>();
        try (var stream = Files.list(moduleDir)) {
            List<Path> tfFiles = stream
                .filter(p -> p.getFileName().toString().endsWith(".tf"))
                .sorted()
                .toList();
            for (Path tf : tfFiles) {
                variables.addAll(parseVariablesFromTf(tf));
            }
        }
        String name = moduleDir.getFileName().toString();
        return new Module(moduleDir, name, variables);
    }

    @Override
    public String renderInputsBlock(Map<String, InputValue> inputs, List<String> declaredOrder) {
        return InputsRenderer.render(inputs, declaredOrder);
    }

    @Override
    public Map<String, String> parseHierarchyLocals(Path hclFile) throws IOException {
        String text = Files.readString(hclFile);
        java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>(extractStringLocals(text));
        Path dir = hclFile.toAbsolutePath().normalize().getParent();
        String dirBase = (dir == null || dir.getFileName() == null) ? "" : dir.getFileName().toString();
        for (String key : extractBasenameDirLocalKeys(text)) {
            out.putIfAbsent(key, dirBase);
        }
        return out;
    }

    @Override
    public CommonHcl parseCommon(Path commonHclFile) throws IOException {
        String text = Files.readString(commonHclFile);
        Map<String, String> locals = extractStringLocals(text);
        Optional<String> base = Optional.ofNullable(locals.get("base_source_url"));
        String name = commonHclFile.getFileName().toString();
        if (name.endsWith(".hcl")) name = name.substring(0, name.length() - 4);
        return new CommonHcl(commonHclFile, name, base, locals);
    }

    /** Body text between the braces of the first top-level `locals { ... }` block, if any. */
    private static Optional<String> localsBody(String text) {
        Matcher header = Pattern.compile("(?m)^[ \\t]*locals[ \\t]*\\{").matcher(text);
        if (!header.find()) return Optional.empty();
        int openBrace = text.indexOf('{', header.start());
        int close = findMatchingClose(text, openBrace);
        if (close < 0) return Optional.empty();
        return Optional.of(text.substring(openBrace + 1, close));
    }

    /** Extract simple `key = "value"` entries from the top-level `locals { ... }` block.
     *  Anything that isn't a literal quoted string is skipped.
     */
    private static Map<String, String> extractStringLocals(String text) {
        Optional<String> body = localsBody(text);
        if (body.isEmpty()) return Map.of();
        java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>();
        Matcher line = Pattern.compile("(?m)^[ \\t]*([A-Za-z_][A-Za-z0-9_]*)[ \\t]*=[ \\t]*\"([^\"\\n]*)\"[ \\t]*$").matcher(body.get());
        while (line.find()) {
            out.put(line.group(1), line.group(2));
        }
        return out;
    }

    /** Keys in the top-level `locals { ... }` block assigned exactly `basename(get_terragrunt_dir())`. */
    private static java.util.Set<String> extractBasenameDirLocalKeys(String text) {
        Optional<String> body = localsBody(text);
        if (body.isEmpty()) return java.util.Set.of();
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        Matcher line = Pattern.compile(
            "(?m)^[ \\t]*([A-Za-z_][A-Za-z0-9_]*)[ \\t]*=[ \\t]*basename\\([ \\t]*get_terragrunt_dir\\(\\)[ \\t]*\\)[ \\t]*$"
        ).matcher(body.get());
        while (line.find()) keys.add(line.group(1));
        return keys;
    }

    @SuppressWarnings("unchecked")
    private static List<IncludeBlock> extractIncludes(Map<String, Object> root, String originalText) {
        Object inc = root.get("include");
        if (!(inc instanceof Map<?, ?> incMap)) return List.of();
        List<IncludeBlock> out = new ArrayList<>();
        for (Map.Entry<?, ?> e : incMap.entrySet()) {
            String name = String.valueOf(e.getKey());
            String pathExpr = extractIncludePathExpr(name, originalText);
            out.add(new IncludeBlock(name, pathExpr, Optional.empty()));
        }
        return out;
    }

    private static String extractIncludePathExpr(String includeName, String originalText) {
        // Find the include block by name and pull out the raw `path = ...` line(s) from the original text.
        Pattern blockStart = Pattern.compile(
            "(?m)^[ \\t]*include[ \\t]+\"" + java.util.regex.Pattern.quote(includeName) + "\"[ \\t]*\\{"
        );
        Matcher m = blockStart.matcher(originalText);
        if (!m.find()) return "";
        int openBrace = originalText.indexOf('{', m.start());
        int close = findMatchingClose(originalText, openBrace);
        if (close < 0) return "";
        String body = originalText.substring(openBrace + 1, close);
        // Find `path = <value>` — value may be a quoted string with interpolations
        Matcher pm = Pattern.compile("(?m)^[ \\t]*path[ \\t]*=[ \\t]*(.+?)$").matcher(body);
        if (!pm.find()) return "";
        return pm.group(1).trim();
    }

    private static String deriveName(Path file) {
        Path parent = file.getParent();
        return parent == null ? file.getFileName().toString() : parent.getFileName().toString();
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> extractSource(Map<String, Object> root, String originalText) {
        Object terraform = root.get("terraform");
        if (terraform instanceof Map<?, ?> tfMap) {
            Object src = ((Map<String, Object>) tfMap).get("source");
            if (src instanceof String s) return Optional.of(s);
        }
        return extractSourceFromText(originalText);
    }

    /**
     * Fallback when HCL4j can't reduce the source value to a String (most often because it's an
     * unresolved interpolation like {@code "${include.common.locals.base_source_url}"}).
     * Scans the original text for the {@code source = ...} line inside the first {@code terraform { ... }}
     * block and returns its raw right-hand side (with surrounding whitespace trimmed).
     */
    private static Optional<String> extractSourceFromText(String text) {
        Matcher header = Pattern.compile("(?m)^[ \\t]*terraform[ \\t]*\\{").matcher(text);
        if (!header.find()) return Optional.empty();
        int openBrace = text.indexOf('{', header.start());
        int close = findMatchingClose(text, openBrace);
        if (close < 0) return Optional.empty();
        String body = text.substring(openBrace + 1, close);
        Matcher src = Pattern.compile("(?m)^[ \\t]*source[ \\t]*=[ \\t]*(.+?)[ \\t]*$").matcher(body);
        if (!src.find()) return Optional.empty();
        String raw = src.group(1).trim();
        return raw.isEmpty() ? Optional.empty() : Optional.of(raw);
    }

    @SuppressWarnings("unchecked")
    private static List<Dependency> extractDependencies(Map<String, Object> root) {
        Object dep = root.get("dependency");
        if (!(dep instanceof Map<?, ?> depMap)) return List.of();
        List<Dependency> out = new ArrayList<>();
        for (Map.Entry<?, ?> e : depMap.entrySet()) {
            String name = String.valueOf(e.getKey());
            Object body = e.getValue();
            String cfg = "";
            if (body instanceof Map<?, ?> bodyMap) {
                Object cp = ((Map<String, Object>) bodyMap).get("config_path");
                if (cp instanceof String s) cfg = s;
            }
            out.add(new Dependency(name, cfg, Optional.empty()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, InputValue> extractInputs(Map<String, Object> root, String originalText) {
        Object inputs = root.get("inputs");
        if (!(inputs instanceof Map<?, ?> inputsMap)) return new LinkedHashMap<>();
        LinkedHashMap<String, InputValue> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : ((Map<String, Object>) inputsMap).entrySet()) {
            String key = String.valueOf(e.getKey());
            out.put(key, classify(key, e.getValue(), originalText));
        }
        return out;
    }

    private static InputValue classify(String key, Object value, String originalText) {
        if (value instanceof String s) return new InputValue.StringValue(s);
        if (value instanceof Boolean b) return new InputValue.BoolValue(b);
        if (value instanceof Number n) return new InputValue.NumberValue(numberLiteral(key, n, originalText));
        return new InputValue.RawHcl(extractRawValue(key, originalText));
    }

    private static String numberLiteral(String key, Number n, String originalText) {
        String raw = extractRawValue(key, originalText);
        if (!raw.isEmpty() && raw.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")) {
            return raw;
        }
        if (n instanceof Double d && d == Math.floor(d) && !Double.isInfinite(d)) {
            return Long.toString(d.longValue());
        }
        if (n instanceof Float f && f == Math.floor(f) && !Float.isInfinite(f)) {
            return Long.toString(f.longValue());
        }
        return n.toString();
    }

    private static String extractRawValue(String key, String text) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "(?m)^[ \\t]*" + java.util.regex.Pattern.quote(key) + "[ \\t]*=[ \\t]*"
        );
        java.util.regex.Matcher m = p.matcher(text);
        if (!m.find()) return "";
        int start = m.end();
        int end = scanExpressionEnd(text, start);
        if (end <= start) return "";
        return text.substring(start, end).trim();
    }

    /**
     * Scan an HCL right-hand-side expression starting at {@code start}, honoring nested
     * {@code {} [] ()}, quoted strings, and heredocs. Returns the exclusive end index — the
     * first newline at depth 0, or the index of a {@code }} that would close an enclosing block.
     */
    private static int scanExpressionEnd(String text, int start) {
        int curly = 0, square = 0, paren = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) { escape = false; continue; }
            if (inString) {
                if (c == '\\') { escape = true; continue; }
                if (c == '"') inString = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            // Skip over heredoc bodies entirely so braces inside don't affect depth.
            if (c == '<' && i + 1 < text.length() && text.charAt(i + 1) == '<') {
                Heredoc hd = Heredoc.tryRead(text, i);
                if (hd != null) { i = hd.endIndex() - 1; continue; }
            }
            if (c == '{') curly++;
            else if (c == '}') {
                if (curly == 0) return i; // closes the enclosing inputs block
                curly--;
            }
            else if (c == '[') square++;
            else if (c == ']' && square > 0) square--;
            else if (c == '(') paren++;
            else if (c == ')' && paren > 0) paren--;
            else if (c == '\n' && curly == 0 && square == 0 && paren == 0) return i;
        }
        return text.length();
    }

    private static List<Variable> parseVariablesFromTf(Path tf) throws IOException {
        String text = Files.readString(tf);
        List<Variable> out = new ArrayList<>();

        java.util.regex.Matcher header = java.util.regex.Pattern
            .compile("(?m)^[ \\t]*variable[ \\t]+\"([^\"]+)\"[ \\t]*\\{")
            .matcher(text);
        while (header.find()) {
            String name = header.group(1);
            int openBrace = text.indexOf('{', header.start());
            int close = findMatchingClose(text, openBrace);
            if (close < 0) continue;
            String body = text.substring(openBrace + 1, close);
            out.add(new Variable(
                name,
                extractAttr(body, "type"),
                extractAttr(body, "description"),
                extractDefault(body)
            ));
        }
        return out;
    }

    private static String extractAttr(String body, String attr) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?m)^[ \\t]*" + attr + "[ \\t]*=[ \\t]*")
            .matcher(body);
        if (!m.find()) return "";
        int start = m.end();

        // Heredoc?  `<<TAG ... TAG` or `<<-TAG ... TAG` (indented form).
        Heredoc hd = Heredoc.tryRead(body, start);
        if (hd != null) return hd.body();

        int end = scanExpressionEnd(body, start);
        if (end <= start) return "";
        String raw = body.substring(start, end).trim();
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private static Optional<String> extractDefault(String body) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?m)^[ \\t]*default[ \\t]*=[ \\t]*")
            .matcher(body);
        if (!m.find()) return Optional.empty();
        int start = m.end();
        int end = scanExpressionEnd(body, start);
        if (end <= start) return Optional.empty();
        return Optional.of(body.substring(start, end).trim());
    }

    private static int findMatchingClose(String text, int openIdx) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openIdx; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            // Skip heredoc bodies so braces inside don't break our brace counter.
            if (c == '<' && i + 1 < text.length() && text.charAt(i + 1) == '<') {
                Heredoc hd = Heredoc.tryRead(text, i);
                if (hd != null) { i = hd.endIndex() - 1; continue; }
            }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * HCL heredoc helper.  Handles both {@code <<TAG} and {@code <<-TAG} forms.
     * <p>The indented form ({@code <<-}) strips the leading whitespace of the closing-tag line
     * from each body line that starts with it — a pragmatic approximation of the HCL spec's
     * "strip the longest common leading whitespace" rule that's correct for the well-formed
     * heredocs we see in the wild.</p>
     */
    record Heredoc(String tag, boolean indented, String body, int startIndex, int endIndex) {

        /**
         * Try to read a heredoc starting at {@code from} (which may have leading whitespace).
         * Returns null if no well-formed heredoc starts here.
         */
        static Heredoc tryRead(String text, int from) {
            int p = from;
            while (p < text.length() && (text.charAt(p) == ' ' || text.charAt(p) == '\t')) p++;
            if (p + 1 >= text.length() || text.charAt(p) != '<' || text.charAt(p + 1) != '<') return null;
            int start = p;
            p += 2;
            boolean indented = false;
            if (p < text.length() && text.charAt(p) == '-') { indented = true; p++; }
            int tagStart = p;
            while (p < text.length()) {
                char c = text.charAt(p);
                if (Character.isLetterOrDigit(c) || c == '_') p++;
                else break;
            }
            if (p == tagStart) return null;
            String tag = text.substring(tagStart, p);
            // Skip until end of opener line
            while (p < text.length() && text.charAt(p) != '\n') p++;
            if (p >= text.length()) return null;
            p++; // consume newline after opener
            int bodyStart = p;
            while (p < text.length()) {
                int lineStart = p;
                int afterLeadingWs = p;
                if (indented) {
                    while (afterLeadingWs < text.length()
                        && (text.charAt(afterLeadingWs) == ' ' || text.charAt(afterLeadingWs) == '\t')) {
                        afterLeadingWs++;
                    }
                }
                int tagMatchStart = afterLeadingWs;
                if (tagMatchStart + tag.length() <= text.length()
                    && text.startsWith(tag, tagMatchStart)) {
                    int after = tagMatchStart + tag.length();
                    int trail = after;
                    while (trail < text.length()
                        && (text.charAt(trail) == ' ' || text.charAt(trail) == '\t')) trail++;
                    boolean atEol = trail >= text.length() || text.charAt(trail) == '\n';
                    if (atEol) {
                        int bodyEnd = lineStart;
                        while (bodyEnd > bodyStart && text.charAt(bodyEnd - 1) == '\n') bodyEnd--;
                        String body = text.substring(bodyStart, bodyEnd);
                        if (indented) {
                            String indent = text.substring(lineStart, tagMatchStart);
                            if (!indent.isEmpty()) body = stripIndent(body, indent);
                        }
                        int end = trail < text.length() ? trail + 1 : trail;
                        return new Heredoc(tag, indented, body, start, end);
                    }
                }
                while (p < text.length() && text.charAt(p) != '\n') p++;
                if (p < text.length()) p++;
            }
            return null; // unterminated
        }

        private static String stripIndent(String body, String indent) {
            StringBuilder out = new StringBuilder(body.length());
            int i = 0;
            while (i < body.length()) {
                int lineEnd = body.indexOf('\n', i);
                if (lineEnd < 0) lineEnd = body.length();
                String line = body.substring(i, lineEnd);
                if (line.startsWith(indent)) line = line.substring(indent.length());
                out.append(line);
                if (lineEnd < body.length()) out.append('\n');
                i = lineEnd + 1;
            }
            return out.toString();
        }
    }
}

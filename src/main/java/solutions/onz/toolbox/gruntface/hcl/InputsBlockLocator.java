package solutions.onz.toolbox.gruntface.hcl;

import solutions.onz.toolbox.gruntface.model.ByteRange;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InputsBlockLocator {

    private static final Pattern START = Pattern.compile(
        "(?m)^[ \\t]*inputs[ \\t]*=[ \\t]*\\{"
    );

    private InputsBlockLocator() {}

    public static Optional<ByteRange> locate(String text) {
        Matcher m = START.matcher(text);
        if (!m.find()) return Optional.empty();
        int start = m.start();
        int openBraceIdx = text.indexOf('{', m.start());
        int end = findMatchingClose(text, openBraceIdx);
        if (end < 0) return Optional.empty();
        return Optional.of(new ByteRange(start, end + 1));
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
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}

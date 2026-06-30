package solutions.onz.toolbox.gruntface.ui.edit;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based tokenizer for Terragrunt HCL. Returns RichTextFX-friendly style spans
 * for use with {@code CodeArea#setStyleSpans}. Pure function: same input -> same output,
 * no FX state, safe to call from any thread.
 *
 * Token precedence (first match wins):
 *   COMMENT  -> hcl-comment
 *   HEREDOC  -> hcl-string
 *   STRING   -> hcl-string
 *   NUMBER   -> hcl-number
 *   KEYWORD  -> hcl-keyword
 *   BLOCK    -> hcl-block
 *   ATTR     -> hcl-attr
 */
public final class HclSyntaxHighlighter {

    private HclSyntaxHighlighter() {}

    private static final String COMMENT_RE =
        "(?<COMMENT>#[^\\n]*|//[^\\n]*|/\\*[\\s\\S]*?\\*/)";
    private static final String HEREDOC_RE =
        "(?<HEREDOC><<-?[A-Za-z_][A-Za-z0-9_]*\\b[\\s\\S]*?\\n^[A-Za-z_][A-Za-z0-9_]*$)";
    private static final String STRING_RE =
        "(?<STRING>\"(?:\\\\.|[^\"\\\\])*\")";
    private static final String NUMBER_RE =
        "(?<NUMBER>\\b\\d+(?:\\.\\d+)?\\b)";
    private static final String KEYWORD_RE =
        "(?<KEYWORD>\\b(?:true|false|null|for|in|if|else|endif|endfor)\\b)";
    private static final String BLOCK_RE =
        "(?<BLOCK>\\b(?:terraform|inputs|locals|include|dependency|dependencies|generate|remote_state|skip|prevent_destroy|iam_role|download_dir)\\b)";
    private static final String ATTR_RE =
        "(?<ATTR>\\b[a-zA-Z_][a-zA-Z0-9_]*\\b(?=\\s*=))";

    private static final Pattern PATTERN = Pattern.compile(
        COMMENT_RE + "|" + HEREDOC_RE + "|" + STRING_RE + "|" + NUMBER_RE
        + "|" + KEYWORD_RE + "|" + BLOCK_RE + "|" + ATTR_RE,
        Pattern.MULTILINE
    );

    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        if (text == null || text.isEmpty()) {
            builder.add(Collections.emptyList(), 0);
            return builder.create();
        }
        Matcher m = PATTERN.matcher(text);
        int last = 0;
        while (m.find()) {
            String styleClass =
                  m.group("COMMENT") != null ? "hcl-comment"
                : m.group("HEREDOC") != null ? "hcl-string"
                : m.group("STRING")  != null ? "hcl-string"
                : m.group("NUMBER")  != null ? "hcl-number"
                : m.group("KEYWORD") != null ? "hcl-keyword"
                : m.group("BLOCK")   != null ? "hcl-block"
                : m.group("ATTR")    != null ? "hcl-attr"
                : null;
            if (styleClass == null) {
                throw new IllegalStateException(
                    "unhandled regex group at offset " + m.start());
            }
            builder.add(Collections.emptyList(), m.start() - last);
            builder.add(Collections.singleton(styleClass), m.end() - m.start());
            last = m.end();
        }
        builder.add(Collections.emptyList(), text.length() - last);
        return builder.create();
    }
}

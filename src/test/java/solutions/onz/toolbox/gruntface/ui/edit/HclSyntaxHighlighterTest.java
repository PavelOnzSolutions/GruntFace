package solutions.onz.toolbox.gruntface.ui.edit;

import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HclSyntaxHighlighterTest {

    @Test
    void tokens_simpleInputsBlock() {
        String text = "inputs = {\n  name = \"x\"\n}\n";
        StyleSpans<Collection<String>> spans = HclSyntaxHighlighter.computeHighlighting(text);

        List<String> styledClasses = new java.util.ArrayList<>();
        spans.forEach(s -> {
            if (!s.getStyle().isEmpty()) styledClasses.add(String.join(",", s.getStyle()));
        });

        assertEquals(List.of("hcl-block", "hcl-attr", "hcl-string"), styledClasses);
    }

    @Test
    void tokens_lineCommentAndBlockComment() {
        String text = "# hello\n// world\n/* block\n  comment */\n";
        StyleSpans<Collection<String>> spans = HclSyntaxHighlighter.computeHighlighting(text);

        long commentCount = 0;
        for (var s : spans) {
            if (s.getStyle().contains("hcl-comment")) commentCount++;
        }
        assertEquals(3, commentCount, "three comment tokens expected");
    }

    @Test
    void tokens_heredocSurvives() {
        String text = "x = <<-EOT\nhello\nEOT\n";
        StyleSpans<Collection<String>> spans = HclSyntaxHighlighter.computeHighlighting(text);

        boolean hasString = false;
        for (var s : spans) if (s.getStyle().contains("hcl-string")) { hasString = true; break; }
        assertTrue(hasString, "heredoc body should be styled as hcl-string");
    }

    @Test
    void tokens_emptyText() {
        StyleSpans<Collection<String>> spans = HclSyntaxHighlighter.computeHighlighting("");
        assertNotNull(spans);
        int total = 0;
        for (var s : spans) total += s.getLength();
        assertEquals(0, total);
    }

    @Test
    void tokens_pureWhitespace() {
        StyleSpans<Collection<String>> spans = HclSyntaxHighlighter.computeHighlighting("   \n\t \n");
        long styled = 0;
        for (var s : spans) if (!s.getStyle().isEmpty()) styled++;
        assertEquals(0, styled);
    }

    @Test
    void tokens_nullText() {
        StyleSpans<Collection<String>> spans = HclSyntaxHighlighter.computeHighlighting(null);
        assertNotNull(spans);
        int total = 0;
        for (var s : spans) total += s.getLength();
        assertEquals(0, total);
    }
}

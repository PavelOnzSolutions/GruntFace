package solutions.onz.toolbox.gruntface.ui.help;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HelpTopicsTest {

    @Test
    void all_returnsSixTopicsInOrder() {
        List<HelpTopic> topics = HelpTopics.all();
        assertEquals(6, topics.size(), "expect six shipped topics");
        assertEquals(
            List.of("overview", "getting-started", "reading-the-graph",
                    "editing-resources", "creating-resources", "preferences"),
            topics.stream().map(HelpTopic::id).toList());
    }

    @Test
    void each_topicResolvesToANonEmptyMarkdownResource() {
        for (HelpTopic t : HelpTopics.all()) {
            assertNotNull(t.markdown(), "markdown null for " + t.id());
            assertFalse(t.markdown().isBlank(), "markdown blank for " + t.id());
        }
    }

    @Test
    void each_titleEqualsFirstH1FromFile() {
        for (HelpTopic t : HelpTopics.all()) {
            String firstLine = t.markdown().lines()
                .filter(l -> !l.isBlank())
                .findFirst()
                .orElseThrow();
            assertTrue(firstLine.startsWith("# "),
                "first non-blank line of " + t.id() + " should start with '# '");
            String expected = firstLine.substring(2).trim();
            assertEquals(expected, t.title(),
                "title should match the H1 of " + t.id());
        }
    }
}

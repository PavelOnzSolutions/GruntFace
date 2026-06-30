package solutions.onz.toolbox.gruntface.ui.help;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Static registry of help topics. Topic order here drives the order shown in
 * the User Guide window's topic list. Each entry maps an {@code id} (used in
 * tests and logs) to the classpath path of the {@code .md} resource that
 * ships in the JAR.
 */
public final class HelpTopics {

    private static final String BASE = "/solutions/onz/toolbox/gruntface/ui/help/";

    /**
     * Entry exists (rather than a plain List<String> of filenames) because the
     * topic {@code id} — used in tests and logs — must not contain the numeric
     * ordering prefix that the filename does. Keeping the two as separate
     * fields makes the split explicit.
     */
    private record Entry(String id, String fileName) {}

    private static final List<Entry> ENTRIES = List.of(
        new Entry("overview",           "01-overview.md"),
        new Entry("getting-started",    "02-getting-started.md"),
        new Entry("reading-the-graph",  "03-reading-the-graph.md"),
        new Entry("editing-resources",  "04-editing-resources.md"),
        new Entry("creating-resources", "05-creating-resources.md"),
        new Entry("preferences",        "06-preferences.md")
    );

    private HelpTopics() {}

    public static List<HelpTopic> all() {
        return ENTRIES.stream().map(HelpTopics::load).toList();
    }

    private static HelpTopic load(Entry e) {
        String path = BASE + e.fileName();
        String md = readResource(path);
        String title = extractTitle(md, e.id(), path);
        return new HelpTopic(e.id(), path, title, md);
    }

    private static String readResource(String path) {
        try (InputStream in = HelpTopics.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Help resource missing: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }

    /** First non-blank line must start with "# "; the rest of that line is the title. */
    private static String extractTitle(String markdown, String id, String resourcePath) {
        for (String line : markdown.lines().toList()) {
            if (line.isBlank()) continue;
            if (line.startsWith("# ")) return line.substring(2).trim();
            String shown = line.length() > 80 ? line.substring(0, 80) + "…" : line;
            throw new IllegalStateException(
                "Help file '" + id + "' (" + resourcePath
                    + ") must start with '# Title' but got: \"" + shown + "\"");
        }
        throw new IllegalStateException(
            "Help file '" + id + "' (" + resourcePath + ") is blank");
    }
}

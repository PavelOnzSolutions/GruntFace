package solutions.onz.toolbox.gruntface.ui.help;

/**
 * One help topic. {@code resourcePath} is the absolute classpath path to the
 * Markdown body (e.g. {@code /solutions/onz/toolbox/gruntface/ui/help/01-overview.md}).
 * {@code title} is the topic's display label, parsed from the file's first
 * {@code # heading}. {@code markdown} is the raw file body.
 */
public record HelpTopic(String id, String resourcePath, String title, String markdown) {
}

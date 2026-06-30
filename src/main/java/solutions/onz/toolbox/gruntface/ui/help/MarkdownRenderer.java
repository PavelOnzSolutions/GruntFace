package solutions.onz.toolbox.gruntface.ui.help;

import javafx.application.HostServices;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.Document;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.parser.Parser;

/**
 * Renders a Markdown string to a {@link VBox} of native JavaFX controls.
 * Supports a small subset (see the design doc in
 * docs/superpowers/specs/2026-06-16-in-app-help-design.md, §5).
 *
 * Constructing JavaFX scene-graph nodes does not require a running JavaFX
 * toolkit, so this class is testable without a {@code JFXPanel} init.
 */
public final class MarkdownRenderer {

    private final HostServices hostServices;
    private final Parser parser = Parser.builder().build();

    /**
     * @param hostServices used to open hyperlinks; may be {@code null} in tests
     *                     (links will then be inert).
     */
    public MarkdownRenderer(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    public VBox render(String markdown) {
        VBox root = new VBox();
        root.getStyleClass().add("help-content");
        if (markdown == null || markdown.isEmpty()) return root;
        Document doc = (Document) parser.parse(markdown);
        new BlockVisitor(root).visit(doc);
        return root;
    }

    private final class BlockVisitor extends AbstractVisitor {
        private final VBox out;

        BlockVisitor(VBox out) { this.out = out; }

        @Override public void visit(Heading h) {
            StringBuilder sb = new StringBuilder();
            h.accept(new AbstractVisitor() {
                @Override public void visit(org.commonmark.node.Text t) { sb.append(t.getLiteral()); }
                @Override public void visit(org.commonmark.node.Code c) { sb.append(c.getLiteral()); }
            });
            Label l = new Label(sb.toString());
            l.setWrapText(true);
            l.getStyleClass().add(switch (h.getLevel()) {
                case 1 -> "help-h1";
                case 2 -> "help-h2";
                default -> "help-h3";
            });
            out.getChildren().add(l);
        }

        @Override public void visit(Paragraph p) {
            TextFlow tf = new TextFlow();
            tf.getStyleClass().add("help-paragraph");
            renderInlines(p, tf);
            out.getChildren().add(tf);
        }

        @Override public void visit(BulletList list) {
            VBox listBox = new VBox();
            listBox.getStyleClass().add("help-list-bullet");
            for (org.commonmark.node.Node item = list.getFirstChild();
                 item != null; item = item.getNext()) {
                listBox.getChildren().add(buildListRow("•", (ListItem) item));
            }
            out.getChildren().add(listBox);
        }

        @Override public void visit(FencedCodeBlock cb) {
            TextFlow tf = new TextFlow();
            tf.getStyleClass().add("help-code-block");
            Text txt = new Text(cb.getLiteral());
            tf.getChildren().add(txt);
            out.getChildren().add(tf);
        }

        @Override public void visit(OrderedList list) {
            VBox listBox = new VBox();
            listBox.getStyleClass().add("help-list-ordered");
            int idx = list.getStartNumber();
            for (org.commonmark.node.Node item = list.getFirstChild();
                 item != null; item = item.getNext(), idx++) {
                listBox.getChildren().add(buildListRow(idx + ".", (ListItem) item));
            }
            out.getChildren().add(listBox);
        }

        private HBox buildListRow(String marker, ListItem item) {
            Label markerLabel = new Label(marker);
            markerLabel.getStyleClass().add("help-list-marker");
            TextFlow body = new TextFlow();
            body.getStyleClass().add("help-list-body");
            // A list item's child is typically a Paragraph; pull its inlines into our
            // TextFlow. Nested lists / code blocks inside list items are intentionally
            // not rendered (spec §2: nested lists out of scope for v1).
            for (org.commonmark.node.Node child = item.getFirstChild();
                 child != null; child = child.getNext()) {
                if (child instanceof Paragraph p) {
                    renderInlines(p, body);
                }
            }
            HBox row = new HBox(6, markerLabel, body);
            row.getStyleClass().add("help-list-row");
            HBox.setHgrow(body, Priority.ALWAYS);
            return row;
        }

        private void renderInlines(org.commonmark.node.Node parent, TextFlow into) {
            for (org.commonmark.node.Node child = parent.getFirstChild();
                 child != null; child = child.getNext()) {
                renderInline(child, into, java.util.List.of());
            }
        }

        private void renderInline(org.commonmark.node.Node n, TextFlow into,
                                  java.util.List<String> inheritedClasses) {
            if (n instanceof org.commonmark.node.Text t) {
                Text txt = new Text(t.getLiteral());
                for (String c : inheritedClasses) txt.getStyleClass().add(c);
                into.getChildren().add(txt);
                return;
            }
            if (n instanceof org.commonmark.node.StrongEmphasis) {
                walkInline(n, into, append(inheritedClasses, "help-bold"));
                return;
            }
            if (n instanceof org.commonmark.node.Emphasis) {
                walkInline(n, into, append(inheritedClasses, "help-italic"));
                return;
            }
            if (n instanceof org.commonmark.node.Code c) {
                Text txt = new Text(c.getLiteral());
                txt.getStyleClass().add("help-code-inline");
                for (String cls : inheritedClasses) txt.getStyleClass().add(cls);
                into.getChildren().add(txt);
                return;
            }
            if (n instanceof Link link) {
                StringBuilder label = new StringBuilder();
                link.accept(new AbstractVisitor() {
                    @Override public void visit(org.commonmark.node.Text t) { label.append(t.getLiteral()); }
                });
                Hyperlink hl = new Hyperlink(label.toString());
                hl.getStyleClass().add("help-link");
                String href = link.getDestination();
                hl.setOnAction(ev -> {
                    if (hostServices != null && href != null && !href.isBlank()) {
                        hostServices.showDocument(href);
                    }
                });
                into.getChildren().add(hl);
                return;
            }
            if (n instanceof org.commonmark.node.HardLineBreak) {
                into.getChildren().add(new Text("\n"));
                return;
            }
            if (n instanceof org.commonmark.node.SoftLineBreak) {
                into.getChildren().add(new Text(" "));
                return;
            }
            // Fallback: walk children with no added class.
            walkInline(n, into, inheritedClasses);
        }

        private void walkInline(org.commonmark.node.Node parent, TextFlow into,
                                java.util.List<String> classes) {
            for (org.commonmark.node.Node child = parent.getFirstChild();
                 child != null; child = child.getNext()) {
                renderInline(child, into, classes);
            }
        }

        private static java.util.List<String> append(java.util.List<String> base, String s) {
            java.util.List<String> out = new java.util.ArrayList<>(base);
            out.add(s);
            return out;
        }
    }
}

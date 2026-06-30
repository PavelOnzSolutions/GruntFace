package solutions.onz.toolbox.gruntface.ui.help;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownRendererTest {

    @BeforeAll
    static void initToolkit() {
        // Label (a Control) triggers Platform.setDefaultPlatformUserAgentStylesheet
        // in its static initializer, which requires the JavaFX toolkit.
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // toolkit already initialised by an earlier test run — fine.
        }
    }

    private final MarkdownRenderer renderer = new MarkdownRenderer(null);

    @Test
    void h1_producesLabelWithHelpH1Class() {
        VBox out = renderer.render("# Hello");
        assertEquals(1, out.getChildren().size());
        Node n = out.getChildren().get(0);
        assertInstanceOf(Label.class, n);
        Label l = (Label) n;
        assertEquals("Hello", l.getText());
        assertTrue(l.getStyleClass().contains("help-h1"));
    }

    @Test
    void h2_andH3_useTheirOwnClasses() {
        VBox out = renderer.render("## Sub\n\n### Subsub");
        assertEquals(2, out.getChildren().size());
        Label h2 = (Label) out.getChildren().get(0);
        Label h3 = (Label) out.getChildren().get(1);
        assertEquals("Sub", h2.getText());
        assertTrue(h2.getStyleClass().contains("help-h2"));
        assertEquals("Subsub", h3.getText());
        assertTrue(h3.getStyleClass().contains("help-h3"));
    }

    @Test
    void paragraph_producesTextFlowWithOnePlainTextRun() {
        VBox out = renderer.render("Just a paragraph.");
        assertEquals(1, out.getChildren().size());
        TextFlow tf = (TextFlow) out.getChildren().get(0);
        List<Node> runs = tf.getChildren();
        assertEquals(1, runs.size());
        assertEquals("Just a paragraph.", ((Text) runs.get(0)).getText());
    }

    @Test
    void emptyInput_producesEmptyVBox() {
        VBox out = renderer.render("");
        assertEquals(0, out.getChildren().size());
    }

    @Test
    void boldText_producesStyledTextRun() {
        VBox out = renderer.render("Hello **world**.");
        TextFlow tf = (TextFlow) out.getChildren().get(0);
        List<Node> runs = tf.getChildren();
        assertEquals(3, runs.size());
        assertEquals("Hello ", ((Text) runs.get(0)).getText());
        assertEquals("world",  ((Text) runs.get(1)).getText());
        assertTrue(((Text) runs.get(1)).getStyleClass().contains("help-bold"));
        assertEquals(".",      ((Text) runs.get(2)).getText());
    }

    @Test
    void italicText_producesStyledTextRun() {
        VBox out = renderer.render("an *emphasised* word");
        TextFlow tf = (TextFlow) out.getChildren().get(0);
        Text mid = (Text) tf.getChildren().get(1);
        assertEquals("emphasised", mid.getText());
        assertTrue(mid.getStyleClass().contains("help-italic"));
    }

    @Test
    void inlineCode_producesStyledTextRun() {
        VBox out = renderer.render("call `foo()` here");
        TextFlow tf = (TextFlow) out.getChildren().get(0);
        Text mid = (Text) tf.getChildren().get(1);
        assertEquals("foo()", mid.getText());
        assertTrue(mid.getStyleClass().contains("help-code-inline"));
    }

    @Test
    void bulletList_producesVBoxOfHBoxRowsWithBulletPrefix() {
        VBox out = renderer.render("- first\n- second");
        assertEquals(1, out.getChildren().size());
        VBox list = (VBox) out.getChildren().get(0);
        assertTrue(list.getStyleClass().contains("help-list-bullet"));
        assertEquals(2, list.getChildren().size());

        HBox row0 = (HBox) list.getChildren().get(0);
        Label bullet0 = (Label) row0.getChildren().get(0);
        TextFlow body0 = (TextFlow) row0.getChildren().get(1);
        assertEquals("•", bullet0.getText());
        assertEquals("first", ((Text) body0.getChildren().get(0)).getText());

        HBox row1 = (HBox) list.getChildren().get(1);
        TextFlow body1 = (TextFlow) row1.getChildren().get(1);
        assertEquals("second", ((Text) body1.getChildren().get(0)).getText());
    }

    @Test
    void orderedList_usesNumericPrefix() {
        VBox out = renderer.render("1. alpha\n2. beta\n3. gamma");
        VBox list = (VBox) out.getChildren().get(0);
        assertTrue(list.getStyleClass().contains("help-list-ordered"));
        assertEquals(3, list.getChildren().size());

        for (int i = 0; i < 3; i++) {
            HBox row = (HBox) list.getChildren().get(i);
            Label num = (Label) row.getChildren().get(0);
            assertEquals((i + 1) + ".", num.getText());
        }
    }

    @Test
    void fencedCodeBlock_producesTextFlowWithCodeBlockClass() {
        String md = "```\nline-one\nline-two\n```";
        VBox out = renderer.render(md);
        assertEquals(1, out.getChildren().size());
        TextFlow tf = (TextFlow) out.getChildren().get(0);
        assertTrue(tf.getStyleClass().contains("help-code-block"));
        Text only = (Text) tf.getChildren().get(0);
        assertEquals("line-one\nline-two\n", only.getText());
    }

    @Test
    void link_producesHyperlinkWithLabelText() {
        VBox out = renderer.render("See [the docs](https://example.com) please.");
        TextFlow tf = (TextFlow) out.getChildren().get(0);
        List<Node> runs = tf.getChildren();
        assertEquals(3, runs.size());
        assertEquals("See ", ((Text) runs.get(0)).getText());
        Hyperlink link = (Hyperlink) runs.get(1);
        assertEquals("the docs", link.getText());
        assertTrue(link.getStyleClass().contains("help-link"));
        assertEquals(" please.", ((Text) runs.get(2)).getText());
    }

    @Test
    void hardLineBreak_producesNewlineInsideTextFlow() {
        // Two trailing spaces before \n is the standard Markdown hard-break syntax.
        VBox out = renderer.render("first  \nsecond");
        TextFlow tf = (TextFlow) out.getChildren().get(0);
        // commonmark splits the paragraph into 3 runs: "first", "\n", "second".
        String joined = tf.getChildren().stream()
            .filter(n -> n instanceof Text)
            .map(n -> ((Text) n).getText())
            .reduce("", String::concat);
        assertEquals("first\nsecond", joined);
    }
}

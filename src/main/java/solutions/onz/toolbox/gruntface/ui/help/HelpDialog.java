package solutions.onz.toolbox.gruntface.ui.help;

import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import solutions.onz.toolbox.gruntface.app.GruntFaceApplication;
import solutions.onz.toolbox.gruntface.ui.Prefs;

import java.util.List;

/**
 * Modeless User Guide window. Hosts a topic list on the left and a
 * Markdown-rendered content pane on the right.
 *
 * Owns nothing it cannot rebuild: topics come from {@link HelpTopics#all()},
 * markdown is re-rendered on each selection change. Window size & position
 * persist via {@link Prefs}.
 */
public final class HelpDialog {

    private HelpDialog() {}

    public static void show(Window owner) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle("GruntFace — User Guide");

        List<HelpTopic> topics = HelpTopics.all();
        ListView<HelpTopic> list = new ListView<>(FXCollections.observableArrayList(topics));
        list.getStyleClass().add("help-topics-list");
        list.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(HelpTopic item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.title());
            }
        });

        ScrollPane contentScroll = new ScrollPane();
        contentScroll.getStyleClass().add("help-content-scroll");
        contentScroll.setFitToWidth(true);

        MarkdownRenderer renderer = new MarkdownRenderer(GruntFaceApplication.hostServices());

        ChangeListener<HelpTopic> onSelect = (obs, oldV, newV) -> {
            if (newV == null) return;
            contentScroll.setContent(renderer.render(newV.markdown()));
            contentScroll.setVvalue(0.0);
        };
        list.getSelectionModel().selectedItemProperty().addListener(onSelect);

        SplitPane split = new SplitPane(list, contentScroll);
        split.setDividerPositions(0.25);

        Scene scene = new Scene(split, Prefs.helpWindowWidth(900), Prefs.helpWindowHeight(600));
        scene.getStylesheets().add(
            java.util.Objects.requireNonNull(
                HelpDialog.class.getResource("/solutions/onz/toolbox/gruntface/ui/style.css"),
                "style.css not found on classpath").toExternalForm());

        stage.setScene(scene);
        applyPersistedPositionOrOffsetFromOwner(stage, owner);

        // Persist on every close path (X, Alt+F4, programmatic). setOnCloseRequest
        // misses some of these; setOnHidden fires for all of them.
        stage.setOnHidden(ev -> {
            Prefs.helpWindowSize(scene.getWidth(), scene.getHeight());
            Prefs.helpWindowPosition(stage.getX(), stage.getY());
        });

        if (!topics.isEmpty()) list.getSelectionModel().select(0);
        stage.show();
    }

    private static void applyPersistedPositionOrOffsetFromOwner(Stage stage, Window owner) {
        double x = Prefs.helpWindowX();
        double y = Prefs.helpWindowY();
        if (!Double.isNaN(x) && !Double.isNaN(y) && onScreen(x, y)) {
            stage.setX(x);
            stage.setY(y);
        } else if (owner != null) {
            stage.setX(owner.getX() + 40);
            stage.setY(owner.getY() + 40);
        }
    }

    private static boolean onScreen(double x, double y) {
        for (Screen s : Screen.getScreens()) {
            if (s.getVisualBounds().contains(x, y)) return true;
        }
        return false;
    }
}

package solutions.onz.toolbox.gruntface.ui.edit;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Predicate;

/**
 * Modal text editor for a single HCL file. Knows nothing about Units, Commons, or
 * InfraGraph — it just hosts a CodeArea with syntax highlighting and reports the new
 * text via the onSave callback. The callback returns {@code true} to close the dialog
 * or {@code false} to keep it open (e.g. when the host needs the user to see an error
 * and try again).
 */
public final class HclEditDialog {

    private HclEditDialog() {}

    public static void show(Window owner, Path file, String initialText, Predicate<String> onSave) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Edit — " + file.getFileName());

        CodeArea codeArea = new CodeArea(initialText == null ? "" : initialText);
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.setWrapText(false);
        codeArea.getStyleClass().add("code-area");
        codeArea.setStyle("-fx-font-family: 'Consolas', 'Menlo', monospace;");

        // Tab inserts two spaces.
        codeArea.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == KeyCode.TAB) {
                codeArea.replaceSelection("  ");
                ev.consume();
            }
        });

        // Apply initial highlighting and re-apply on changes, debounced.
        codeArea.setStyleSpans(0, HclSyntaxHighlighter.computeHighlighting(codeArea.getText()));
        codeArea.multiPlainChanges()
            .successionEnds(Duration.ofMillis(150))
            .subscribe(c -> codeArea.setStyleSpans(0,
                HclSyntaxHighlighter.computeHighlighting(codeArea.getText())));

        // Dirty tracking. Compare to the original initial text so any non-user-driven
        // change events during scene construction don't accidentally flag the buffer dirty.
        final String original = initialText == null ? "" : initialText;
        final boolean[] dirty = {false};
        codeArea.textProperty().addListener((obs, oldV, newV) -> {
            if (!java.util.Objects.equals(newV, original)) dirty[0] = true;
        });

        Button saveBtn = new Button("Save");
        saveBtn.setDefaultButton(true);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setCancelButton(true);

        Runnable doSave = () -> {
            boolean closeNow = onSave.test(codeArea.getText());
            if (closeNow) stage.close();
        };

        Runnable doCancel = () -> {
            if (dirty[0]) {
                Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Discard unsaved changes?");
                a.setHeaderText("Unsaved changes");
                a.initOwner(stage);
                var result = a.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.OK) return;
            }
            stage.close();
        };

        saveBtn.setOnAction(e -> doSave.run());
        cancelBtn.setOnAction(e -> doCancel.run());

        HBox buttons = new HBox(8, cancelBtn, saveBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(8));

        BorderPane root = new BorderPane();
        root.getStyleClass().add("hcl-editor");
        root.setCenter(new VirtualizedScrollPane<>(codeArea));
        root.setBottom(buttons);

        Scene scene = new Scene(root, 900, 700);
        scene.getStylesheets().add(
            java.util.Objects.requireNonNull(
                HclEditDialog.class.getResource("hcl-editor.css"),
                "hcl-editor.css not found on classpath").toExternalForm());

        // Ctrl+S shortcut.
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN),
            doSave);

        // Window-close (X) goes through dirty prompt.
        stage.setOnCloseRequest(ev -> {
            if (dirty[0]) {
                ev.consume();
                doCancel.run();
            }
        });

        stage.setScene(scene);
        stage.show();
        Platform.runLater(codeArea::requestFocus);
    }
}

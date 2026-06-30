package solutions.onz.toolbox.gruntface.ui.inspector;

import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * TextArea with a draggable grip below it that lets the user resize it vertically.
 * The TextArea is still set with a default prefRowCount so it has a sensible initial height.
 */
public class ResizableTextArea extends VBox {

    private final TextArea textArea = new TextArea();
    private final Region handle = new Region();

    private static final double MIN_HEIGHT = 56;

    public ResizableTextArea() {
        textArea.setPrefRowCount(3);
        textArea.setWrapText(false);

        handle.getStyleClass().add("resize-handle");
        handle.setPrefHeight(6);

        getChildren().addAll(textArea, handle);
        setSpacing(0);

        final double[] startScreenY = {0};
        final double[] startHeight = {0};
        handle.setOnMousePressed(e -> {
            startScreenY[0] = e.getScreenY();
            startHeight[0] = textArea.getHeight();
            e.consume();
        });
        handle.setOnMouseDragged(e -> {
            double delta = e.getScreenY() - startScreenY[0];
            double target = Math.max(MIN_HEIGHT, startHeight[0] + delta);
            textArea.setPrefHeight(target);
            textArea.setMinHeight(target);
            e.consume();
        });
    }

    public TextArea getTextArea() { return textArea; }

    public void setText(String text) { textArea.setText(text); }
    public String getText() { return textArea.getText(); }

    public void setPromptText(String prompt) { textArea.setPromptText(prompt); }
}

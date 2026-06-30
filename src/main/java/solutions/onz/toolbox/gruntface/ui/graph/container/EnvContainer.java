package solutions.onz.toolbox.gruntface.ui.graph.container;

import javafx.scene.control.Label;
import javafx.scene.layout.Pane;

public class EnvContainer extends Pane {
    public EnvContainer(String label, int unitCount, String backgroundHex) {
        getStyleClass().addAll("diagram-container", "env");
        Label l = new Label(label + " (" + unitCount + ")");
        l.layoutXProperty().set(8);
        l.layoutYProperty().set(4);
        getChildren().add(l);
        if (backgroundHex != null) {
            setStyle("-fx-background-color: " + backgroundHex + ";");
        }
    }
}

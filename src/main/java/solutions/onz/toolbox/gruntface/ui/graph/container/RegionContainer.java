package solutions.onz.toolbox.gruntface.ui.graph.container;

import javafx.scene.control.Label;
import javafx.scene.layout.Pane;

public class RegionContainer extends Pane {
    public RegionContainer(String label, int unitCount) {
        getStyleClass().addAll("diagram-container", "region");
        Label l = new Label(label + " (" + unitCount + ")");
        l.layoutXProperty().set(8);
        l.layoutYProperty().set(4);
        getChildren().add(l);
    }
}

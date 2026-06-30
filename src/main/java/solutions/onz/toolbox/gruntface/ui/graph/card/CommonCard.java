package solutions.onz.toolbox.gruntface.ui.graph.card;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import solutions.onz.toolbox.gruntface.model.CommonHcl;

public class CommonCard extends StackPane {
    private final CommonHcl common;
    public CommonCard(CommonHcl c) {
        this.common = c;
        getStyleClass().addAll("diagram-card", "common");
        Label l = new Label(c.name());
        l.getStyleClass().add("title");
        getChildren().add(l);
        setPrefWidth(160);
    }
    public CommonHcl common() { return common; }
}

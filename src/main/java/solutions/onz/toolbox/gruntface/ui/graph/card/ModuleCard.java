package solutions.onz.toolbox.gruntface.ui.graph.card;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import solutions.onz.toolbox.gruntface.azure.AzureResourceInferrer;
import solutions.onz.toolbox.gruntface.model.Module;
import solutions.onz.toolbox.gruntface.ui.graph.SvgLoader;

public class ModuleCard extends StackPane {
    private final Module module;

    public ModuleCard(Module m) {
        this.module = m;
        getStyleClass().addAll("diagram-card", "module");

        AzureResourceInferrer.Match match = AzureResourceInferrer.inferByName(m.name());
        Node icon = match.confidence() != AzureResourceInferrer.Confidence.UNKNOWN
            ? SvgLoader.loadScaled(match.resource().iconResourcePath(), 24)
            : null;

        StackPane iconHolder = new StackPane();
        iconHolder.setMinSize(28, 28);
        iconHolder.setPrefSize(28, 28);
        iconHolder.setMaxSize(28, 28);
        if (icon != null) iconHolder.getChildren().add(icon);

        Label l = new Label(m.name());
        l.getStyleClass().add("title");

        HBox row = new HBox(8, iconHolder, l);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(l, Priority.ALWAYS);
        getChildren().add(row);

        setPrefWidth(180);
    }

    public Module module() { return module; }
}

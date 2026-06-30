package solutions.onz.toolbox.gruntface.ui.graph.card;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import solutions.onz.toolbox.gruntface.azure.AzureResourceInferrer;
import solutions.onz.toolbox.gruntface.model.ExternalModule;
import solutions.onz.toolbox.gruntface.ui.graph.SvgLoader;

public class ExternalCard extends StackPane {
    private final ExternalModule external;

    public ExternalCard(ExternalModule x) {
        this.external = x;
        getStyleClass().addAll("diagram-card", "external");

        AzureResourceInferrer.Match match = AzureResourceInferrer.inferByName(x.sourceRef());
        Node icon = match.confidence() != AzureResourceInferrer.Confidence.UNKNOWN
            ? SvgLoader.loadScaled(match.resource().iconResourcePath(), 24)
            : null;

        StackPane iconHolder = new StackPane();
        iconHolder.setMinSize(28, 28);
        iconHolder.setPrefSize(28, 28);
        iconHolder.setMaxSize(28, 28);
        if (icon != null) iconHolder.getChildren().add(icon);

        Label l = new Label(shorten(x.sourceRef()));
        l.getStyleClass().add("title");

        HBox row = new HBox(8, iconHolder, l);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(l, Priority.ALWAYS);
        getChildren().add(row);

        setPrefWidth(180);
    }

    public ExternalModule external() { return external; }

    private static String shorten(String s) {
        int q = s.indexOf('?'); String h = q >= 0 ? s.substring(0, q) : s;
        int sl = h.lastIndexOf('/'); return sl >= 0 ? h.substring(sl + 1) : h;
    }
}

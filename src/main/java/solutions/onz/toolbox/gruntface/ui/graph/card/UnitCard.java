package solutions.onz.toolbox.gruntface.ui.graph.card;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import solutions.onz.toolbox.gruntface.azure.AzureResourceInferrer;
import solutions.onz.toolbox.gruntface.model.Dependency;
import solutions.onz.toolbox.gruntface.model.Unit;
import solutions.onz.toolbox.gruntface.name.ResolvedName;
import solutions.onz.toolbox.gruntface.ui.graph.SvgLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class UnitCard extends Region {

    private final Unit unit;
    private final VBox root = new VBox(4);
    private final VBox portBox = new VBox(2);
    private final Label caret = new Label("▶");
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final BooleanProperty expanded = new SimpleBooleanProperty(false);
    private final List<OutPortDescriptor> outPorts = new ArrayList<>();
    private Consumer<Unit> onPortNavigate = u -> {};

    public record OutPortDescriptor(String label, Unit target) {}

    public UnitCard(Unit unit, AzureResourceInferrer.Match match) {
        this.unit = unit;
        getStyleClass().add("diagram-card");
        switch (match.confidence()) {
            case GUESSED -> getStyleClass().add("guessed");
            case UNKNOWN -> getStyleClass().add("unknown");
            case MATCHED -> {}
        }

        Node icon = SvgLoader.loadScaled(match.resource().iconResourcePath(), 28);
        Label title = new Label(unit.name());
        title.getStyleClass().add("title");
        Label subtitle = new Label(match.resource().displayName());
        subtitle.getStyleClass().add("subtitle");
        VBox titles = new VBox(0, title, subtitle);

        caret.getStyleClass().add("caret");
        caret.setOnMouseClicked(ev -> {
            expanded.set(!expanded.get());
            caret.setText(expanded.get() ? "▼" : "▶");
            ev.consume();
        });

        // Fixed-size holder so the icon's reported bounds never spill into the title.
        // No clip — StackPane centers the icon and the SVG was scaled to fit inside.
        javafx.scene.layout.StackPane iconHolder = new javafx.scene.layout.StackPane();
        iconHolder.setMinSize(32, 32);
        iconHolder.setPrefSize(32, 32);
        iconHolder.setMaxSize(32, 32);
        if (icon != null) iconHolder.getChildren().add(icon);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(iconHolder, titles, caret);
        HBox.setHgrow(titles, Priority.ALWAYS);
        root.getChildren().add(header);

        Optional<ResolvedName> resolved = unit.resolvedName();
        if (resolved.isPresent()) {
            Label rn = new Label(resolved.get().text());
            rn.getStyleClass().add("resolved-name");
            if (resolved.get().confidence() == ResolvedName.Confidence.FALLBACK) {
                rn.getStyleClass().add("fallback");
                rn.setText("? " + rn.getText());
            }
            root.getChildren().add(rn);
        }

        Line divider = new Line(0, 0, 220, 0);
        divider.getStyleClass().add("diagram-card-divider");
        root.getChildren().add(divider);
        root.getChildren().add(portBox);
        divider.setVisible(false); divider.setManaged(false);
        portBox.setVisible(false); portBox.setManaged(false);

        expanded.addListener((obs, was, is) -> {
            divider.setVisible(is); divider.setManaged(is);
            portBox.setVisible(is); portBox.setManaged(is);
            rebuildPortRows();
            requestLayout();
        });

        setPrefWidth(240);
        getChildren().add(root);
        selected.addListener((obs, was, is) -> pseudoClassStateChanged(SELECTED_PC, is));
    }

    public Unit unit() { return unit; }
    public BooleanProperty selectedProperty() { return selected; }
    public BooleanProperty expandedProperty() { return expanded; }

    public void setOutPorts(List<OutPortDescriptor> outs) {
        outPorts.clear();
        outPorts.addAll(outs);
        if (expanded.get()) rebuildPortRows();
    }

    public void setOnPortNavigate(Consumer<Unit> handler) {
        this.onPortNavigate = handler;
    }

    private void rebuildPortRows() {
        portBox.getChildren().clear();
        for (Dependency d : unit.dependencies()) {
            Label row = new Label("●  dependency: " + d.name());
            row.getStyleClass().add("port-row");
            d.resolvedUnitPath().ifPresent(p -> row.setOnMouseClicked(ev -> {
                // Resolution to a Unit instance is handled by the skin; here we pass the path
                // marker — the skin's port-navigate handler does the lookup.
            }));
            portBox.getChildren().add(row);
        }
        for (OutPortDescriptor out : outPorts) {
            Label row = new Label("○  output → " + out.label());
            row.getStyleClass().add("port-row");
            row.setOnMouseClicked(ev -> { onPortNavigate.accept(out.target()); ev.consume(); });
            portBox.getChildren().add(row);
        }
    }

    @Override
    protected void layoutChildren() {
        var i = getInsets();
        root.resizeRelocate(
            i.getLeft(), i.getTop(),
            getWidth() - i.getLeft() - i.getRight(),
            getHeight() - i.getTop() - i.getBottom()
        );
    }

    @Override
    protected double computePrefHeight(double width) {
        var i = getInsets();
        root.applyCss();
        double innerWidth = width - i.getLeft() - i.getRight();
        return i.getTop() + root.prefHeight(innerWidth) + i.getBottom();
    }

    @Override
    protected double computeMinHeight(double width) { return computePrefHeight(width); }

    private static final PseudoClass SELECTED_PC = PseudoClass.getPseudoClass("selected");
}

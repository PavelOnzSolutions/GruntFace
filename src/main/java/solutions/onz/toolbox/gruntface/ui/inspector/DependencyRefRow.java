package solutions.onz.toolbox.gruntface.ui.inspector;

import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import solutions.onz.toolbox.gruntface.create.DependencyReference;
import solutions.onz.toolbox.gruntface.model.InputValue;
import solutions.onz.toolbox.gruntface.model.Variable;

import java.util.List;
import java.util.Optional;

/**
 * Row composite: segmented {@code Value | Ref} toggle on top, with the delegate value-editor
 * shown in {@code Value} mode and a dependency-reference editor shown in {@code Ref} mode.
 *
 * Used by:
 *   - Wizard Step 4 inputs row builder (constructs with the dep names selected in Step 3).
 *   - Inspector {@code InputsEditor.makeRow} (constructs with the unit's parsed dependencies).
 */
public class DependencyRefRow implements ValueEditor {

    private final VBox root = new VBox(4);
    private final ChoiceBox<String> depChoice = new ChoiceBox<>();
    private final TextField suffixField = new TextField();
    private final CheckBox wrapCheck = new CheckBox("wrap in [ ]");
    private final HBox refRow = new HBox(6);
    private final ToggleGroup toggleGroup = new ToggleGroup();
    private final RadioButton valueRadio = new RadioButton("Value");
    private final RadioButton refRadio = new RadioButton("Ref");
    private final ValueEditor delegate;
    private boolean inRefMode;
    private Runnable onChange = () -> {};

    /**
     * @param availableDepNames dep names the user can reference (Step 3 selection / parsed
     *                          {@code unit.dependencies()}); may be empty.
     * @param delegate          the standard {@link ValueEditor} this row otherwise would have used
     *                          (TextField / TextArea / CheckBox / MapObjectTableEditor).
     * @param initial           initial {@link DependencyReference.Match} when re-entering or when the
     *                          existing value already parses as a reference; null otherwise.
     */
    public DependencyRefRow(List<String> availableDepNames, ValueEditor delegate,
                             DependencyReference.Match initial) {
        this.delegate = delegate;
        valueRadio.setToggleGroup(toggleGroup);
        refRadio.setToggleGroup(toggleGroup);

        depChoice.setItems(FXCollections.observableArrayList(availableDepNames));
        if (!availableDepNames.isEmpty()) depChoice.getSelectionModel().selectFirst();
        depChoice.setPrefWidth(140);
        suffixField.setPromptText("outputs.<field>");
        wrapCheck.setTooltip(new javafx.scene.control.Tooltip("Wrap in [ ] for list-typed inputs"));

        refRow.getChildren().addAll(depChoice, suffixField, wrapCheck);
        HBox.setHgrow(suffixField, Priority.ALWAYS);

        HBox toggleRow = new HBox(8, valueRadio, refRadio);
        root.getChildren().addAll(toggleRow);

        if (initial != null && availableDepNames.contains(initial.depName())) {
            depChoice.getSelectionModel().select(initial.depName());
            // Keep the suffix verbatim — `outputs.x`, `inputs.y`, `locals.z` all round-trip.
            suffixField.setText(initial.outputsPath());
            wrapCheck.setSelected(initial.wrappedInList());
            setRefMode(true);
            refRadio.setSelected(true);
        } else {
            setRefMode(false);
            valueRadio.setSelected(true);
        }

        ChangeListener<javafx.scene.control.Toggle> listener = (obs, prev, now) -> {
            if (now == refRadio) setRefMode(true);
            else setRefMode(false);
            onChange.run();
        };
        toggleGroup.selectedToggleProperty().addListener(listener);

        depChoice.getSelectionModel().selectedItemProperty()
            .addListener((obs, o, n) -> onChange.run());
        suffixField.textProperty().addListener((obs, o, n) -> onChange.run());
        wrapCheck.selectedProperty().addListener((obs, o, n) -> onChange.run());

        // Disable Ref if no deps available.
        if (availableDepNames.isEmpty()) {
            refRadio.setDisable(true);
            refRadio.setTooltip(new javafx.scene.control.Tooltip(
                "No dependency blocks available — declare one in Step 3 (wizard) or in your editor (Inspector)."));
        }
    }

    private void setRefMode(boolean ref) {
        this.inRefMode = ref;
        if (root.getChildren().size() > 1) root.getChildren().remove(1);
        root.getChildren().add(ref ? refRow : delegate.node());
    }

    @Override
    public Node node() { return root; }

    /** Register a callback fired whenever the toggle flips or any Ref-mode field changes. */
    public void setOnChange(Runnable r) { this.onChange = r == null ? () -> {} : r; }

    @Override
    public InputValue read(Variable v, InputValue previous) {
        if (!inRefMode) return delegate.read(v, previous);
        String dep = depChoice.getSelectionModel().getSelectedItem();
        String suffixCore = suffixField.getText() == null ? "" : suffixField.getText().trim();
        if (dep == null || dep.isBlank() || suffixCore.isBlank()) return previous;
        // Auto-prepend `outputs.` only if the user didn't type their own namespace prefix.
        String fullPath = (suffixCore.startsWith("outputs.")
                || suffixCore.startsWith("inputs.")
                || suffixCore.startsWith("locals."))
            ? suffixCore
            : "outputs." + suffixCore;
        return DependencyReference.build(dep, fullPath, wrapCheck.isSelected());
    }

    /**
     * Convenience: detect if a value already parses as a dependency reference, returning the
     * {@code Match} the caller can hand to the constructor for pre-population. Returns empty if
     * the value is not a reference.
     */
    public static Optional<DependencyReference.Match> detect(InputValue value) {
        return DependencyReference.parse(value);
    }
}

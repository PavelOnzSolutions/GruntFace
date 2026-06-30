package solutions.onz.toolbox.gruntface.ui.create;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import solutions.onz.toolbox.gruntface.create.*;
import solutions.onz.toolbox.gruntface.model.InfraGraph;
import solutions.onz.toolbox.gruntface.model.InputValue;

import java.util.*;

public class Step4Inputs {

    @FXML private VBox formHost;
    @FXML private Label errorLabel;

    private final Map<String, EditorRef> editors = new LinkedHashMap<>();
    private Runnable onValidityChange = () -> {};
    private List<String> availableDepNames = List.of();

    public void init(ResourceTemplate template, WizardMode mode, InfraGraph graph,
                     LocationSuggester.Suggestion suggestion, String folderName,
                     List<String> availableDepNames,
                     Map<String, InputValue> previousValues) {
        this.availableDepNames = List.copyOf(availableDepNames);
        editors.clear();
        formHost.getChildren().clear();

        List<TemplateSchema> schema = SchemaDeriver.derive(template, mode, graph);

        List<TemplateSchema> conventional = schema.stream().filter(s -> s.group() == SchemaGroup.CONVENTIONAL).toList();
        List<TemplateSchema> templateRows = schema.stream().filter(s -> s.group() == SchemaGroup.TEMPLATE).toList();

        if (!conventional.isEmpty()) {
            formHost.getChildren().add(sectionTitle("Conventional inputs"));
            for (TemplateSchema row : conventional) {
                addRow(row, prefillConventional(row, suggestion, folderName, previousValues));
            }
        }
        if (!templateRows.isEmpty()) {
            formHost.getChildren().add(sectionTitle(mode instanceof WizardMode.ResourceFromInclude
                ? "Observed inputs" : "Template inputs"));
            for (TemplateSchema row : templateRows) {
                addRow(row, previousValues.getOrDefault(row.name(),
                    row.defaultLiteral().map(d -> (InputValue) new InputValue.StringValue(d)).orElse(null)));
            }
        }
        // Extra inputs trigger.
        Button addExtra = new Button("+ Add input");
        addExtra.setOnAction(e -> addExtraRow(""));
        formHost.getChildren().add(addExtra);

        // Pre-existing extras (from previousValues but not in schema).
        Set<String> known = new HashSet<>();
        for (TemplateSchema s : schema) known.add(s.name());
        for (Map.Entry<String, InputValue> e : previousValues.entrySet()) {
            if (!known.contains(e.getKey())) addExtraRow(e.getKey(), e.getValue());
        }

        validate();
    }

    public void setOnValidityChange(Runnable r) { this.onValidityChange = r; }
    public boolean isValid() { return errorMessage().isEmpty(); }

    public LinkedHashMap<String, InputValue> values() {
        LinkedHashMap<String, InputValue> out = new LinkedHashMap<>();
        for (Map.Entry<String, EditorRef> e : editors.entrySet()) {
            InputValue v;
            if (e.getValue() instanceof EditorRefWrapper w) {
                v = w.row.read(null, null);
            } else {
                v = readEditor(e.getValue());
            }
            if (v != null) out.put(e.getKey(), v);
        }
        return out;
    }

    private void addRow(TemplateSchema s, InputValue prefill) {
        HBox row = new HBox(8);
        Label name = new Label((s.required() ? "• " : "  ") + s.name());
        name.setMinWidth(180);
        name.setPrefWidth(180);
        if (!s.description().isBlank()) name.setTooltip(new Tooltip(s.description()));

        EditorRef ed = buildEditor(s.typeExpr(), prefill, s.required());
        // Adapt EditorRef to a ValueEditor, then wrap with the Ref toggle.
        solutions.onz.toolbox.gruntface.ui.inspector.ValueEditor delegate =
            new solutions.onz.toolbox.gruntface.ui.inspector.ValueEditor() {
                @Override public javafx.scene.Node node() { return ed.node; }
                @Override public solutions.onz.toolbox.gruntface.model.InputValue read(
                        solutions.onz.toolbox.gruntface.model.Variable v,
                        solutions.onz.toolbox.gruntface.model.InputValue previous) {
                    return readEditor(ed);
                }
            };
        var initial = solutions.onz.toolbox.gruntface.create.DependencyReference.parse(prefill).orElse(null);
        solutions.onz.toolbox.gruntface.ui.inspector.DependencyRefRow refRow =
            new solutions.onz.toolbox.gruntface.ui.inspector.DependencyRefRow(
                availableDepNames, delegate, initial);
        refRow.setOnChange(this::validate);

        HBox.setHgrow(refRow.node(), Priority.ALWAYS);
        row.getChildren().addAll(name, refRow.node());
        formHost.getChildren().add(row);

        // Wrapper retains the EditorRef for type metadata; values() reads through the DependencyRefRow.
        editors.put(s.name(), new EditorRefWrapper(ed, refRow));
        attachValidityHook(ed);
    }

    private void addExtraRow(String key) { addExtraRow(key, null); }

    private void addExtraRow(String key, InputValue prefill) {
        HBox row = new HBox(8);
        TextField k = new TextField(key);
        k.setMinWidth(180);
        k.setPrefWidth(180);
        EditorRef ed = buildEditor("string", prefill, false);
        HBox.setHgrow(ed.node, Priority.ALWAYS);
        Button remove = new Button("×");
        remove.setOnAction(e -> {
            formHost.getChildren().remove(row);
            if (!k.getText().isBlank()) editors.remove(k.getText());
            validate();
        });
        row.getChildren().addAll(k, ed.node, remove);
        // Insert before the "+ Add input" button (which sits at index size-1, or just before trailing extras).
        int insertAt = Math.max(0, formHost.getChildren().size() - 1);
        formHost.getChildren().add(insertAt, row);
        k.textProperty().addListener((obs, oldK, newK) -> {
            editors.remove(oldK);
            if (!newK.isBlank()) editors.put(newK, ed);
            validate();
        });
        if (!k.getText().isBlank()) editors.put(k.getText(), ed);
        attachValidityHook(ed);
    }

    private void attachValidityHook(EditorRef ed) {
        if (ed.node instanceof TextField tf) tf.textProperty().addListener((o, a, b) -> validate());
        else if (ed.node instanceof TextArea ta) ta.textProperty().addListener((o, a, b) -> validate());
        else if (ed.node instanceof CheckBox cb) cb.selectedProperty().addListener((o, a, b) -> validate());
    }

    private void validate() {
        Optional<String> err = errorMessage();
        if (err.isPresent()) {
            errorLabel.setText(err.get());
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        } else {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
        onValidityChange.run();
    }

    private Optional<String> errorMessage() {
        for (var entry : editors.entrySet()) {
            EditorRef ed = entry.getValue();
            if (!ed.required) continue;
            InputValue v = (ed instanceof EditorRefWrapper w)
                ? w.row.read(null, null)
                : readEditor(ed);
            if (isEmpty(v)) return Optional.of("Required input '" + entry.getKey() + "' is empty.");
        }
        return Optional.empty();
    }

    private static boolean isEmpty(InputValue v) {
        if (v == null) return true;
        if (v instanceof InputValue.StringValue sv) return sv.value().isBlank();
        if (v instanceof InputValue.NumberValue nv) return nv.literal().isBlank();
        if (v instanceof InputValue.RawHcl rv) return rv.hcl().isBlank();
        return false;
    }

    private static javafx.scene.Node sectionTitle(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-weight: bold; -fx-padding: 6 0 0 0");
        return l;
    }

    private EditorRef buildEditor(String typeExpr, InputValue prefill, boolean required) {
        String t = typeExpr == null ? "" : typeExpr.toLowerCase();
        if (t.equals("bool")) {
            CheckBox cb = new CheckBox();
            if (prefill instanceof InputValue.BoolValue bv) cb.setSelected(bv.value());
            return new EditorRef(cb, "bool", required);
        }
        if (t.equals("number")) {
            TextField tf = new TextField();
            if (prefill instanceof InputValue.NumberValue nv) tf.setText(nv.literal());
            else if (prefill instanceof InputValue.StringValue sv) tf.setText(sv.value());
            return new EditorRef(tf, "number", required);
        }
        if (t.startsWith("list(") || t.startsWith("map(") || t.startsWith("object(") || t.contains("(")) {
            TextArea ta = new TextArea();
            if (prefill instanceof InputValue.RawHcl rv) ta.setText(rv.hcl());
            else if (prefill instanceof InputValue.StringValue sv) ta.setText(sv.value());
            ta.setPrefRowCount(3);
            return new EditorRef(ta, "raw", required);
        }
        TextField tf = new TextField();
        if (prefill instanceof InputValue.StringValue sv) tf.setText(sv.value());
        else if (prefill instanceof InputValue.NumberValue nv) tf.setText(nv.literal());
        return new EditorRef(tf, "string", required);
    }

    private InputValue readEditor(EditorRef ed) {
        if (ed.kind.equals("bool")) {
            return new InputValue.BoolValue(((CheckBox) ed.node).isSelected());
        }
        if (ed.kind.equals("number")) {
            String s = ((TextField) ed.node).getText();
            if (s == null || s.isBlank()) return null;
            return new InputValue.NumberValue(s);
        }
        if (ed.kind.equals("raw")) {
            String s = ((TextArea) ed.node).getText();
            if (s == null || s.isBlank()) return null;
            return new InputValue.RawHcl(s);
        }
        String s = ((TextField) ed.node).getText();
        if (s == null || s.isBlank()) return null;
        return new InputValue.StringValue(s);
    }

    private InputValue prefillConventional(TemplateSchema s,
                                            LocationSuggester.Suggestion sug,
                                            String folderName,
                                            Map<String, InputValue> previous) {
        InputValue prev = previous.get(s.name());
        if (prev != null) return prev;
        return switch (s.name()) {
            case ConventionalInputs.PURPOSE -> new InputValue.StringValue(
                sug.purpose().orElse(folderName == null ? "" : folderName));
            case ConventionalInputs.LOCATION_SHORT -> sug.locationShort()
                .<InputValue>map(InputValue.StringValue::new).orElse(null);
            case ConventionalInputs.ENVIRONMENT -> sug.environment()
                .<InputValue>map(InputValue.StringValue::new).orElse(null);
            default -> null;
        };
    }

    /** Holder for a row's editor + its declared metadata. */
    private static class EditorRef {
        final javafx.scene.Node node;
        final String kind;       // "string" | "number" | "bool" | "raw"
        final boolean required;
        EditorRef(javafx.scene.Node node, String kind, boolean required) {
            this.node = node; this.kind = kind; this.required = required;
        }
    }

    /** Couples the legacy {@link EditorRef} (for type metadata) with the wrapping {@link solutions.onz.toolbox.gruntface.ui.inspector.DependencyRefRow}. */
    private static class EditorRefWrapper extends EditorRef {
        final solutions.onz.toolbox.gruntface.ui.inspector.DependencyRefRow row;
        EditorRefWrapper(EditorRef base,
                          solutions.onz.toolbox.gruntface.ui.inspector.DependencyRefRow row) {
            super(base.node, base.kind, base.required);
            this.row = row;
        }
    }
}

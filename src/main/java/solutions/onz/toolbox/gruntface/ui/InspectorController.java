package solutions.onz.toolbox.gruntface.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import solutions.onz.toolbox.gruntface.create.ResourceTemplate;
import solutions.onz.toolbox.gruntface.create.WizardMode;
import solutions.onz.toolbox.gruntface.model.ByteRange;
import solutions.onz.toolbox.gruntface.model.CommonHcl;
import solutions.onz.toolbox.gruntface.model.Dependency;
import solutions.onz.toolbox.gruntface.model.ExternalModule;
import solutions.onz.toolbox.gruntface.model.InputValue;
import solutions.onz.toolbox.gruntface.model.Module;
import solutions.onz.toolbox.gruntface.model.ParseIssue;
import solutions.onz.toolbox.gruntface.model.Unit;
import solutions.onz.toolbox.gruntface.model.Variable;
import solutions.onz.toolbox.gruntface.ui.inspector.MapObjectTableEditor;
import solutions.onz.toolbox.gruntface.ui.inspector.ResizableTextArea;
import solutions.onz.toolbox.gruntface.ui.inspector.TypeBadges;
import solutions.onz.toolbox.gruntface.ui.inspector.ValueEditor;

import java.awt.Desktop;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class InspectorController {

    @FXML private Label emptyHint;
    @FXML private VBox content;
    @FXML private Label title;
    @FXML private Label subtitle;
    @FXML private VBox bodyBox;

    private MainController mainController;

    public void setMainController(MainController c) { this.mainController = c; }

    public void showEmpty() {
        emptyHint.setVisible(true);
        emptyHint.setManaged(true);
        content.setVisible(false);
        content.setManaged(false);
        bodyBox.getChildren().clear();
    }

    public void showModule(Module module) {
        showHeader(module.name(), module.dir().toString());
        bodyBox.getChildren().clear();
        bodyBox.getChildren().add(sectionTitle("Variables"));

        TableView<Variable> table = new TableView<>(FXCollections.observableArrayList(module.variables()));
        TableColumn<Variable, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        TableColumn<Variable, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().typeExpr()));
        TableColumn<Variable, String> defaultCol = new TableColumn<>("Default");
        defaultCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().defaultLiteral().orElse("")));
        TableColumn<Variable, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().description()));
        table.getColumns().addAll(List.of(nameCol, typeCol, defaultCol, descCol));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);
        bodyBox.getChildren().add(table);
        addUseAsTemplateButton(new WizardMode.ResourceFromModule(), new ResourceTemplate.LocalModuleTemplate(module));
    }

    public void showExternal(ExternalModule ext) {
        showHeader("(external module)", ext.sourceRef());
        bodyBox.getChildren().clear();
        bodyBox.getChildren().add(new Label(
            "This module's variable schema isn't available (not in the modules root)."));
        addUseAsTemplateButton(new WizardMode.ResourceFromModule(), new ResourceTemplate.ExternalModuleTemplate(ext));
    }

    public void showCommon(CommonHcl common) {
        showHeader(common.name(), common.file().toString());
        bodyBox.getChildren().clear();

        bodyBox.getChildren().add(sectionTitle("Resolved source"));
        if (common.baseSourceUrl().isPresent()) {
            TextField url = new TextField(common.baseSourceUrl().get());
            url.setEditable(false);
            bodyBox.getChildren().add(url);
        } else {
            Label none = new Label("(no base_source_url literal in this file)");
            none.setStyle("-fx-text-fill: #888;");
            bodyBox.getChildren().add(none);
        }

        bodyBox.getChildren().add(sectionTitle("String locals"));
        if (common.locals().isEmpty()) {
            bodyBox.getChildren().add(new Label("(no simple string locals)"));
        } else {
            ListView<String> list = new ListView<>();
            for (Map.Entry<String, String> e : common.locals().entrySet()) {
                list.getItems().add(e.getKey() + " = \"" + e.getValue() + "\"");
            }
            list.setPrefHeight(Math.min(180, 24 * common.locals().size() + 8));
            bodyBox.getChildren().add(list);
        }
        addUseAsTemplateButton(new WizardMode.ResourceFromInclude(), new ResourceTemplate.IncludeTemplate(common));
    }

    public void showUnit(Unit unit, Module sourceModule, ExternalModule external, CommonHcl linkedCommon) {
        showHeader(unit.name(), unit.file().toString());
        bodyBox.getChildren().clear();

        HBox header = new HBox(8);
        Button reveal = new Button("Reveal file location");
        reveal.setOnAction(e -> revealInFileManager(unit));
        header.getChildren().add(reveal);
        bodyBox.getChildren().add(header);

        bodyBox.getChildren().add(sectionTitle("Source"));
        if (sourceModule != null) {
            bodyBox.getChildren().add(new Label("Module: " + sourceModule.name()));
        } else if (external != null) {
            Label src = new Label("External: " + external.sourceRef());
            src.setWrapText(true);
            bodyBox.getChildren().add(src);
        } else if (unit.sourceRef().isPresent()) {
            Label missing = new Label("Source not resolved: " + unit.sourceRef().get());
            missing.setStyle("-fx-text-fill: #c62828;");
            missing.setWrapText(true);
            bodyBox.getChildren().add(missing);
        } else {
            bodyBox.getChildren().add(new Label("(no terraform.source declared in this file)"));
        }

        if (linkedCommon != null) {
            bodyBox.getChildren().add(sectionTitle("Common (include)"));
            Label name = new Label(linkedCommon.name() + " — " + linkedCommon.file());
            name.setWrapText(true);
            bodyBox.getChildren().add(name);
            if (linkedCommon.baseSourceUrl().isPresent()) {
                Label url = new Label("base_source_url: " + linkedCommon.baseSourceUrl().get());
                url.setWrapText(true);
                url.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
                bodyBox.getChildren().add(url);
            }
        }

        for (ParseIssue iss : unit.issues()) {
            Label l = new Label(iss.severity() + ": " + iss.message());
            l.setStyle(iss.severity() == ParseIssue.Severity.ERROR
                ? "-fx-text-fill: #c62828;" : "-fx-text-fill: #ef6c00;");
            l.setWrapText(true);
            bodyBox.getChildren().add(l);
        }

        bodyBox.getChildren().add(sectionTitle("Dependencies"));
        if (unit.dependencies().isEmpty()) {
            bodyBox.getChildren().add(new Label("(none)"));
        } else {
            ListView<String> list = new ListView<>();
            for (Dependency d : unit.dependencies()) {
                String suffix = d.resolvedUnitPath().isPresent() ? "" : " (unresolved)";
                list.getItems().add(d.name() + " → " + d.configPath() + suffix);
            }
            list.setPrefHeight(Math.min(120, 24 * unit.dependencies().size() + 8));
            bodyBox.getChildren().add(list);
        }

        bodyBox.getChildren().add(sectionTitle("Inputs"));

        InputsEditor editor = new InputsEditor(unit, sourceModule, external);
        editor.setMainController(mainController);
        bodyBox.getChildren().add(editor);
        VBox.setVgrow(editor, Priority.ALWAYS);
    }

    private static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("param-section-title");
        return l;
    }

    private void addUseAsTemplateButton(WizardMode mode, ResourceTemplate template) {
        Button btn = new Button("Use as template…");
        btn.setOnAction(e -> mainController.openWizardFromInspector(mode, template));
        bodyBox.getChildren().add(btn);
    }

    private void showHeader(String t, String sub) {
        emptyHint.setVisible(false);
        emptyHint.setManaged(false);
        content.setVisible(true);
        content.setManaged(true);
        title.setText(t);
        subtitle.setText(sub);
    }

    private static void revealInFileManager(Unit unit) {
        try {
            java.io.File dir = unit.file().getParent().toFile();
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir);
        } catch (IOException ignored) {}
    }

    /* ===================================================================== */
    /*  Inputs editor                                                        */
    /* ===================================================================== */

    public static class InputsEditor extends VBox {
        private final Unit unit;
        private final Module sourceModule;
        private final ExternalModule external;
        private final LinkedHashMap<String, ValueEditor> editors = new LinkedHashMap<>();
        private final Button saveButton = new Button("Save…");
        private MainController mainController;

        public InputsEditor(Unit unit, Module sourceModule, ExternalModule external) {
            super(6);
            this.unit = unit;
            this.sourceModule = sourceModule;
            this.external = external;
            build();
        }

        public void setMainController(MainController c) { this.mainController = c; }

        private void build() {
            if (sourceModule != null) {
                buildSchemaForm(sourceModule);
            } else if (!unit.inputs().isEmpty()) {
                buildSchemaLessForm();
            } else {
                buildFreeTextForm();
            }
            getChildren().add(saveButton);
            saveButton.setOnAction(e -> onSave());
        }

        private void buildSchemaForm(Module module) {
            Set<String> declared = new LinkedHashSet<>();
            for (Variable v : module.variables()) declared.add(v.name());

            for (Variable v : module.variables()) {
                getChildren().add(makeRow(v, unit.inputs().get(v.name())));
            }

            List<String> extras = unit.inputs().keySet().stream()
                .filter(k -> !declared.contains(k)).toList();
            if (!extras.isEmpty()) {
                Label extraLbl = new Label("Extra inputs (not declared in module)");
                extraLbl.setStyle("-fx-text-fill: #ef6c00; -fx-font-weight: bold;");
                getChildren().add(extraLbl);
                for (String k : extras) {
                    getChildren().add(makeRow(
                        new Variable(k, "", "", Optional.empty()),
                        unit.inputs().get(k)
                    ));
                }
            }
        }

        private void buildSchemaLessForm() {
            Label hint = new Label(external != null
                ? "Inputs (no schema — external module). Editing what's already in the file."
                : "Inputs (no schema). Editing what's already in the file.");
            hint.setStyle("-fx-text-fill: #666;");
            hint.setWrapText(true);
            getChildren().add(hint);

            for (Map.Entry<String, InputValue> e : unit.inputs().entrySet()) {
                Variable v = new Variable(e.getKey(), inferType(e.getValue()), "", Optional.empty());
                getChildren().add(makeRow(v, e.getValue()));
            }
        }

        private void buildFreeTextForm() {
            Label hint = new Label(external != null
                ? "Free-text editor (external module — no inputs found)"
                : "Free-text editor (no inputs found)");
            hint.setStyle("-fx-text-fill: #666;");
            getChildren().add(hint);
            ResizableTextArea wrap = new ResizableTextArea();
            wrap.getTextArea().setPrefRowCount(12);
            wrap.setText(renderInputsRaw(unit));
            editors.put("__raw__", new ValueEditor() {
                @Override public Node node() { return wrap; }
                @Override public InputValue read(Variable v, InputValue prev) {
                    return new InputValue.RawHcl(wrap.getText());
                }
            });
            getChildren().add(wrap);
        }

        private static String inferType(InputValue v) {
            if (v instanceof InputValue.StringValue) return "string";
            if (v instanceof InputValue.BoolValue) return "bool";
            if (v instanceof InputValue.NumberValue) return "number";
            return "";
        }

        private VBox makeRow(Variable v, InputValue value) {
            VBox row = new VBox(4);
            row.getStyleClass().add("param-row");

            HBox header = new HBox(8);
            header.setAlignment(Pos.CENTER_LEFT);

            Label name = new Label(v.name());
            name.getStyleClass().add("param-name");
            header.getChildren().add(name);

            if (!v.typeExpr().isEmpty()) {
                TypeBadges.TypeInfo info = TypeBadges.classify(v.typeExpr());
                header.getChildren().add(TypeBadges.makeBadge(info));
            }

            if (!v.description().isEmpty()) {
                Label info = new Label("ⓘ"); // info circle
                info.getStyleClass().add("param-info-icon");
                Tooltip tt = new Tooltip(v.description().trim());
                tt.setShowDelay(Duration.millis(150));
                tt.setHideDelay(Duration.millis(200));
                tt.setWrapText(true);
                tt.setMaxWidth(520);
                tt.setStyle("-fx-font-size: 12px;");
                info.setTooltip(tt);
                header.getChildren().add(info);
            }

            row.getChildren().add(header);

            ValueEditor delegate = buildEditor(v, value);
            java.util.List<String> availableDepNames = unit.dependencies().stream()
                .map(solutions.onz.toolbox.gruntface.model.Dependency::name)
                .toList();
            var initial = solutions.onz.toolbox.gruntface.create.DependencyReference.parse(value)
                .orElse(null);
            // If the value parses as a ref but the unit has no matching dep block, fall back to delegate.
            ValueEditor editor = (initial != null && !availableDepNames.contains(initial.depName()))
                ? delegate
                : new solutions.onz.toolbox.gruntface.ui.inspector.DependencyRefRow(
                      availableDepNames, delegate, initial);
            editors.put(v.name(), editor);
            row.getChildren().add(editor.node());

            if (value instanceof InputValue.RawHcl && !isComplexType(v.typeExpr())
                && !(editor instanceof MapObjectTableEditor)
                && !(editor instanceof solutions.onz.toolbox.gruntface.ui.inspector.DependencyRefRow)) {
                Label tag = new Label("expression");
                tag.getStyleClass().add("expression-tag");
                row.getChildren().add(tag);
            }
            return row;
        }

        private ValueEditor buildEditor(Variable v, InputValue value) {
            String typeExpr = v.typeExpr();

            // 1. bool → CheckBox
            if ("bool".equals(typeExpr)) {
                CheckBox cb = new CheckBox();
                if (value instanceof InputValue.BoolValue b) cb.setSelected(b.value());
                else if (v.defaultLiteral().orElse("").equals("true")) cb.setSelected(true);
                return new ValueEditor() {
                    @Override public Node node() { return cb; }
                    @Override public InputValue read(Variable var, InputValue prev) {
                        return new InputValue.BoolValue(cb.isSelected());
                    }
                };
            }

            // 2. map(object({...})) → editable breakdown table when parseable
            if (TypeBadges.isMapOfObject(typeExpr)) {
                Optional<MapObjectTableEditor> table = MapObjectTableEditor.tryBuild(v, value);
                if (table.isPresent()) return table.get();
                // else fall through to text area
            }

            // 3. Collection types or RawHcl values → resizable TextArea (raw HCL)
            if (isComplexType(typeExpr) || value instanceof InputValue.RawHcl) {
                ResizableTextArea wrap = new ResizableTextArea();
                if (value instanceof InputValue.RawHcl r) wrap.setText(r.hcl());
                else if (value instanceof InputValue.StringValue s) wrap.setText("\"" + s.value() + "\"");
                else if (value instanceof InputValue.NumberValue n) wrap.setText(n.literal());
                else if (value instanceof InputValue.BoolValue b) wrap.setText(Boolean.toString(b.value()));
                else if (v.defaultLiteral().isPresent()) wrap.setPromptText(v.defaultLiteral().get());
                return new ValueEditor() {
                    @Override public Node node() { return wrap; }
                    @Override public InputValue read(Variable var, InputValue prev) {
                        String txt = wrap.getText() == null ? "" : wrap.getText().trim();
                        if (prev instanceof InputValue.RawHcl || isComplexType(var.typeExpr())) {
                            return new InputValue.RawHcl(txt);
                        }
                        return new InputValue.StringValue(txt);
                    }
                };
            }

            // 4. number / string → TextField
            TextField tf = new TextField();
            if (value instanceof InputValue.StringValue s) tf.setText(s.value());
            else if (value instanceof InputValue.NumberValue n) tf.setText(n.literal());
            else if (v.defaultLiteral().isPresent()) tf.setPromptText(v.defaultLiteral().get());
            return new ValueEditor() {
                @Override public Node node() { return tf; }
                @Override public InputValue read(Variable var, InputValue prev) {
                    if ("number".equals(var.typeExpr())) return new InputValue.NumberValue(tf.getText().trim());
                    return new InputValue.StringValue(tf.getText());
                }
            };
        }

        private static boolean isComplexType(String t) {
            if (t == null) return false;
            return t.startsWith("list") || t.startsWith("set") || t.startsWith("map")
                || t.startsWith("object") || t.startsWith("tuple");
        }

        private static String renderInputsRaw(Unit u) {
            if (u.inputsRange().isEmpty()) return "";
            ByteRange r = u.inputsRange().get();
            String slice = u.originalText().substring(r.start(), r.end());
            int firstBrace = slice.indexOf('{');
            int lastBrace = slice.lastIndexOf('}');
            if (firstBrace < 0 || lastBrace < 0) return slice;
            return slice.substring(firstBrace + 1, lastBrace).trim();
        }

        private void onSave() {
            if (mainController == null) return;

            // Free-text fallback path
            if (editors.containsKey("__raw__")) {
                InputValue raw = editors.get("__raw__").read(null, null);
                String txt = raw instanceof InputValue.RawHcl r ? r.hcl() : "";
                mainController.saveUnitFreeText(unit, txt);
                return;
            }

            LinkedHashMap<String, InputValue> values = new LinkedHashMap<>();
            List<String> order = new java.util.ArrayList<>();

            if (sourceModule != null) {
                for (Variable v : sourceModule.variables()) {
                    ValueEditor ed = editors.get(v.name());
                    if (ed == null) continue;
                    InputValue val = ed.read(v, unit.inputs().get(v.name()));
                    if (val != null) values.put(v.name(), val);
                    order.add(v.name());
                }
            }

            for (String k : unit.inputs().keySet()) {
                if (values.containsKey(k)) continue;
                ValueEditor ed = editors.get(k);
                if (ed == null) continue;
                Variable v = new Variable(k, inferType(unit.inputs().get(k)), "", Optional.empty());
                InputValue val = ed.read(v, unit.inputs().get(k));
                if (val != null) values.put(k, val);
            }

            mainController.saveUnit(unit, values, order);
        }
    }
}

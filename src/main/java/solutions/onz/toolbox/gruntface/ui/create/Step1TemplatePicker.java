package solutions.onz.toolbox.gruntface.ui.create;

import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import solutions.onz.toolbox.gruntface.create.ResourceTemplate;
import solutions.onz.toolbox.gruntface.create.TemplateCatalog;
import solutions.onz.toolbox.gruntface.create.WizardMode;
import solutions.onz.toolbox.gruntface.model.*;
import solutions.onz.toolbox.gruntface.model.Module;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class Step1TemplatePicker {

    @FXML private RadioButton modeResourceFromInclude;
    @FXML private RadioButton modeResourceFromModule;
    @FXML private RadioButton modeIncludeFromModule;
    @FXML private TextField filterField;
    @FXML private ListView<ResourceTemplate> templateList;
    @FXML private Label previewName;
    @FXML private Label previewFile;
    @FXML private Label previewSource;
    @FXML private Label previewVariables;
    @FXML private Label previewUsedBy;
    @FXML private Label emptyHint;

    private InfraGraph graph;
    private final ObservableList<ResourceTemplate> all = FXCollections.observableArrayList();
    private final ObservableList<ResourceTemplate> filtered = FXCollections.observableArrayList();
    private Runnable onValidityChange = () -> {};
    private Consumer<WizardMode> onModeChange = m -> {};

    public void init(InfraGraph graph) {
        this.graph = graph;
        templateList.setItems(filtered);
        templateList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(ResourceTemplate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : labelFor(item));
            }
        });
        ChangeListener<Object> refresh = (obs, oldV, newV) -> {
            updatePreview();
            onValidityChange.run();
        };
        templateList.getSelectionModel().selectedItemProperty().addListener(refresh);
        filterField.textProperty().addListener((obs, oldV, newV) -> applyFilter());
        reloadList();
    }

    public void setOnValidityChange(Runnable r) { this.onValidityChange = r; }
    public void setOnModeChange(Consumer<WizardMode> c) { this.onModeChange = c; }

    public void selectTemplate(ResourceTemplate t) {
        if (t == null) return;
        for (ResourceTemplate it : filtered) {
            if (it.id().equals(t.id())) { templateList.getSelectionModel().select(it); break; }
        }
    }

    public void setMode(WizardMode mode) {
        if (mode instanceof WizardMode.ResourceFromInclude) modeResourceFromInclude.setSelected(true);
        else if (mode instanceof WizardMode.ResourceFromModule) modeResourceFromModule.setSelected(true);
        else modeIncludeFromModule.setSelected(true);
        reloadList();
    }

    public WizardMode currentMode() {
        if (modeResourceFromInclude.isSelected()) return new WizardMode.ResourceFromInclude();
        if (modeResourceFromModule.isSelected()) return new WizardMode.ResourceFromModule();
        return new WizardMode.IncludeFromModule();
    }

    public Optional<ResourceTemplate> selectedTemplate() {
        return Optional.ofNullable(templateList.getSelectionModel().getSelectedItem());
    }

    public boolean isValid() { return selectedTemplate().isPresent(); }

    @FXML void onModeChange() {
        reloadList();
        onModeChange.accept(currentMode());
    }

    private void reloadList() {
        List<ResourceTemplate> ts = TemplateCatalog.forMode(graph, currentMode());
        all.setAll(ts);
        applyFilter();
        updateEmptyHint(ts.isEmpty());
        onValidityChange.run();
    }

    private void applyFilter() {
        String f = filterField.getText() == null ? "" : filterField.getText().toLowerCase();
        if (f.isBlank()) filtered.setAll(all);
        else filtered.setAll(all.stream().filter(t -> labelFor(t).toLowerCase().contains(f)).toList());
    }

    private void updateEmptyHint(boolean empty) {
        if (!empty) {
            emptyHint.setVisible(false);
            emptyHint.setManaged(false);
            return;
        }
        WizardMode m = currentMode();
        String msg;
        if (m instanceof WizardMode.ResourceFromInclude) {
            msg = "No includes found in this project. Create one first via 'New Include from Module'.";
        } else {
            msg = "No modules found in this project.";
        }
        emptyHint.setText(msg);
        emptyHint.setVisible(true);
        emptyHint.setManaged(true);
    }

    private void updatePreview() {
        ResourceTemplate t = templateList.getSelectionModel().getSelectedItem();
        if (t == null) { clearPreview(); return; }
        if (t instanceof ResourceTemplate.IncludeTemplate it) {
            CommonHcl c = it.include();
            previewName.setText(c.name());
            previewFile.setText(c.file().toString());
            previewSource.setText("Source: " + c.baseSourceUrl().orElse("(none)"));
            previewVariables.setText("Locals: " + String.join(", ", c.locals().keySet()));
            previewUsedBy.setText("Used by " + countIncludeUsers(c) + " existing units");
        } else if (t instanceof ResourceTemplate.LocalModuleTemplate lm) {
            Module m = lm.module();
            previewName.setText(m.name());
            previewFile.setText(m.dir().toString());
            previewSource.setText("Source: local module directory");
            String vars = m.variables().isEmpty()
                ? "Variables: (none declared)"
                : "Variables: " + m.variables().stream().map(Variable::name).reduce((a, b) -> a + ", " + b).orElse("");
            previewVariables.setText(vars);
            previewUsedBy.setText("Used by " + countModuleUsers(m) + " existing units");
        } else if (t instanceof ResourceTemplate.ExternalModuleTemplate xm) {
            previewName.setText("(external module)");
            previewFile.setText("");
            previewSource.setText("Source: " + xm.external().sourceRef());
            previewVariables.setText("Variables: (cannot introspect remote modules)");
            previewUsedBy.setText("Used by " + countExternalUsers(xm.external()) + " existing units");
        }
    }

    private void clearPreview() {
        previewName.setText("");
        previewFile.setText("");
        previewSource.setText("");
        previewVariables.setText("");
        previewUsedBy.setText("");
    }

    private int countIncludeUsers(CommonHcl c) {
        int n = 0;
        for (InfraGraph.IncludesEdge e : graph.includesEdges()) {
            if (e.to().file().equals(c.file())) n++;
        }
        return n;
    }
    private int countModuleUsers(Module m) {
        int n = 0;
        for (InfraGraph.UsesEdge e : graph.usesEdges()) {
            if (e.to() instanceof Module mm && mm.dir().equals(m.dir())) n++;
        }
        return n;
    }
    private int countExternalUsers(ExternalModule x) {
        int n = 0;
        for (InfraGraph.UsesEdge e : graph.usesEdges()) {
            if (e.to() instanceof ExternalModule xx && xx.sourceRef().equals(x.sourceRef())) n++;
        }
        return n;
    }

    private String labelFor(ResourceTemplate t) {
        if (t instanceof ResourceTemplate.IncludeTemplate it) {
            var match = solutions.onz.toolbox.gruntface.azure.AzureResourceInferrer
                .inferByName(it.include().name());
            return it.include().name() + " [" + tagOf(match) + "]";
        }
        if (t instanceof ResourceTemplate.LocalModuleTemplate lm) {
            var match = solutions.onz.toolbox.gruntface.azure.AzureResourceInferrer
                .inferByName(lm.module().name());
            return lm.module().name() + " [" + tagOf(match) + "]";
        }
        if (t instanceof ResourceTemplate.ExternalModuleTemplate xm) {
            var match = solutions.onz.toolbox.gruntface.azure.AzureResourceInferrer
                .inferByName(xm.external().sourceRef());
            return xm.external().sourceRef() + " [" + tagOf(match) + "]";
        }
        return t.displayName();
    }

    private String tagOf(solutions.onz.toolbox.gruntface.azure.AzureResourceInferrer.Match match) {
        return match.resource().id();
    }
}

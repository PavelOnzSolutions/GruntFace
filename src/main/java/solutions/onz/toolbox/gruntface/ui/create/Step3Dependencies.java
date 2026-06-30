package solutions.onz.toolbox.gruntface.ui.create;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import solutions.onz.toolbox.gruntface.create.DependencyDecl;
import solutions.onz.toolbox.gruntface.model.InfraGraph;
import solutions.onz.toolbox.gruntface.model.Unit;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Step 3 of the create wizard (Resource flows only): pick sibling units to declare as
 * {@code dependency} blocks in the new resource.
 *
 * "Same environment directory" is defined as: units whose parent directory equals the new
 * resource's parent directory (i.e. siblings of the new folder).
 */
public class Step3Dependencies {

    private static final Pattern HCL_IDENT = Pattern.compile("[A-Za-z_]\\w*");

    @FXML private TextField filterField;
    @FXML private VBox rowsHost;
    @FXML private Label statusLabel;
    @FXML private Label errorLabel;

    private final List<Row> rows = new ArrayList<>();
    private Runnable onValidityChange = () -> {};

    public void init(InfraGraph graph, Path parentDirOfTarget,
                     LinkedHashMap<String, DependencyDecl> cached) {
        rows.clear();
        rowsHost.getChildren().clear();

        // Siblings = units whose containing folder sits directly inside `parentDirOfTarget`.
        // A unit's file is `<parentDirOfTarget>/<folder>/terragrunt.hcl`, so we compare
        // `unit.file().getParent().getParent()` (the unit's folder's parent) to `parentDirOfTarget`.
        Path normalized = parentDirOfTarget.toAbsolutePath().normalize();
        List<Unit> siblings = graph.units().stream()
            .filter(u -> {
                Path unitFolder = u.file().getParent();
                if (unitFolder == null) return false;
                Path containingDir = unitFolder.getParent();
                if (containingDir == null) return false;
                return containingDir.toAbsolutePath().normalize().equals(normalized);
            })
            .sorted(Comparator.comparing(u -> u.file().getParent().getFileName().toString()))
            .toList();

        if (siblings.isEmpty()) {
            Label empty = new Label(
                "No sibling units yet — skip this step and add dependencies after creating peers.");
            empty.setStyle("-fx-text-fill: #666;");
            rowsHost.getChildren().add(empty);
        } else {
            for (Unit u : siblings) {
                String folderName = u.file().getParent().getFileName().toString();
                String defaultName = sanitiseName(folderName);
                String relative = "../" + folderName;
                Row r = makeRow(folderName, defaultName, relative);
                rows.add(r);
                rowsHost.getChildren().add(r.node);
                // Restore from cache (by folder relative path).
                cached.values().stream()
                    .filter(d -> d.configPath().equals(relative))
                    .findFirst()
                    .ifPresent(d -> {
                        r.check.setSelected(true);
                        r.nameField.setText(d.name());
                    });
            }
        }

        filterField.textProperty().addListener((obs, o, n) -> applyFilter(n));
        refresh();
    }

    public void setOnValidityChange(Runnable r) { this.onValidityChange = r; }

    public boolean isValid() { return validationMessage().isEmpty(); }

    public LinkedHashMap<String, DependencyDecl> selectedDependencies() {
        LinkedHashMap<String, DependencyDecl> out = new LinkedHashMap<>();
        for (Row r : rows) {
            if (!r.check.isSelected()) continue;
            String name = r.nameField.getText().trim();
            out.put(name, new DependencyDecl(name, r.relativePath));
        }
        return out;
    }

    private Row makeRow(String folderName, String defaultName, String relative) {
        CheckBox check = new CheckBox(folderName);
        check.setMinWidth(260);
        TextField nameField = new TextField(defaultName);
        nameField.setPromptText("dep name");
        nameField.setDisable(true);
        HBox h = new HBox(8, check, new Label("name:"), nameField);
        HBox.setHgrow(nameField, Priority.ALWAYS);

        ChangeListener<Boolean> tickListener = (obs, was, isNow) -> {
            nameField.setDisable(!isNow);
            refresh();
        };
        check.selectedProperty().addListener(tickListener);
        nameField.textProperty().addListener((obs, o, n) -> refresh());

        return new Row(h, check, nameField, relative, folderName);
    }

    private void applyFilter(String filter) {
        String needle = filter == null ? "" : filter.toLowerCase().trim();
        for (Row r : rows) {
            boolean show = needle.isEmpty() || r.folderName.toLowerCase().contains(needle);
            r.node.setVisible(show);
            r.node.setManaged(show);
        }
    }

    private void refresh() {
        Optional<String> err = validationMessage();
        if (err.isPresent()) {
            errorLabel.setText(err.get());
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        } else {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
        long selected = rows.stream().filter(r -> r.check.isSelected()).count();
        statusLabel.setText(selected == 0
            ? "No dependencies (optional)"
            : selected + " selected");
        onValidityChange.run();
    }

    private Optional<String> validationMessage() {
        Set<String> seen = new HashSet<>();
        for (Row r : rows) {
            if (!r.check.isSelected()) continue;
            String n = r.nameField.getText() == null ? "" : r.nameField.getText().trim();
            if (n.isEmpty()) return Optional.of("Dependency '" + r.folderName + "' needs a name.");
            if (!HCL_IDENT.matcher(n).matches()) {
                return Optional.of("Dependency name '" + n + "' must match [A-Za-z_]\\w*.");
            }
            if (!seen.add(n)) return Optional.of("Duplicate dependency name: " + n);
        }
        return Optional.empty();
    }

    private static String sanitiseName(String folderName) {
        String s = folderName.toLowerCase().replace('-', '_').replaceAll("[^a-z0-9_]+", "_");
        if (s.isEmpty() || Character.isDigit(s.charAt(0))) s = "dep_" + s;
        return s;
    }

    private static final class Row {
        final HBox node;
        final CheckBox check;
        final TextField nameField;
        final String relativePath;   // "../folderName"
        final String folderName;
        Row(HBox node, CheckBox check, TextField nameField, String relativePath, String folderName) {
            this.node = node;
            this.check = check;
            this.nameField = nameField;
            this.relativePath = relativePath;
            this.folderName = folderName;
        }
    }
}

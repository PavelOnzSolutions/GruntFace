package solutions.onz.toolbox.gruntface.ui.create;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import solutions.onz.toolbox.gruntface.azure.AzureResourceInferrer;
import solutions.onz.toolbox.gruntface.create.*;
import solutions.onz.toolbox.gruntface.model.InputValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Step2Location {

    @FXML private TreeView<Path> dirTree;
    @FXML private TextField folderNameField;
    @FXML private Label resolvedPathLabel;
    @FXML private Label resolvedNameLabel;
    @FXML private Label errorLabel;

    private Path terragruntRoot;
    private WizardMode mode;
    private ResourceTemplate template;
    private Runnable onValidityChange = () -> {};
    private static final Pattern FOLDER_NAME = Pattern.compile("[a-zA-Z0-9._-]+");

    public void init(Path terragruntRoot, WizardMode mode, ResourceTemplate template, Path prefillParent) {
        this.terragruntRoot = terragruntRoot;
        this.mode = mode;
        this.template = template;
        buildTree();
        if (prefillParent != null) selectInTree(prefillParent);
        folderNameField.setText(defaultFolderName(template));
        folderNameField.textProperty().addListener((obs, o, n) -> refresh());
        dirTree.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> refresh());
        refresh();
    }

    public void setOnValidityChange(Runnable r) { this.onValidityChange = r; }

    public boolean isValid() {
        return validationMessage().isEmpty();
    }

    public Optional<Path> selectedParentDir() {
        TreeItem<Path> sel = dirTree.getSelectionModel().getSelectedItem();
        return Optional.ofNullable(sel == null ? null : sel.getValue());
    }

    public String folderName() { return folderNameField.getText() == null ? "" : folderNameField.getText().trim(); }

    public LocationSuggester.Suggestion suggestion() {
        return selectedParentDir()
            .map(p -> LocationSuggester.suggest(p.resolve(folderName()), terragruntRoot))
            .orElse(LocationSuggester.Suggestion.empty());
    }

    private void buildTree() {
        TreeItem<Path> root = new TreeItem<>(terragruntRoot);
        root.setExpanded(true);
        populate(root);
        dirTree.setRoot(root);
        dirTree.setCellFactory(tv -> new TreeCell<>() {
            @Override protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item.equals(terragruntRoot) ? "<project root>" : item.getFileName().toString());
            }
        });
    }

    private void populate(TreeItem<Path> node) {
        node.getChildren().clear();
        try (Stream<Path> s = Files.list(node.getValue())) {
            List<Path> dirs = s.filter(Files::isDirectory).sorted().toList();
            for (Path d : dirs) {
                TreeItem<Path> child = new TreeItem<>(d);
                if (hasChildren(d)) child.getChildren().add(new TreeItem<>(null));  // expand placeholder
                child.expandedProperty().addListener((obs, was, isNow) -> {
                    if (isNow && child.getChildren().size() == 1 && child.getChildren().get(0).getValue() == null) {
                        populate(child);
                    }
                });
                node.getChildren().add(child);
            }
        } catch (IOException ignored) { /* surface as empty branch */ }
    }

    private static boolean hasChildren(Path p) {
        try (Stream<Path> s = Files.list(p)) { return s.anyMatch(Files::isDirectory); }
        catch (IOException e) { return false; }
    }

    private void selectInTree(Path target) {
        TreeItem<Path> match = findInTree(dirTree.getRoot(), target.toAbsolutePath().normalize());
        if (match != null) {
            dirTree.getSelectionModel().select(match);
            for (TreeItem<Path> p = match; p != null; p = p.getParent()) p.setExpanded(true);
        }
    }

    private TreeItem<Path> findInTree(TreeItem<Path> node, Path target) {
        if (node.getValue() != null && node.getValue().toAbsolutePath().normalize().equals(target)) return node;
        if (!node.isExpanded() && node.getValue() != null && Files.isDirectory(node.getValue())) {
            node.setExpanded(true);
        }
        for (TreeItem<Path> c : node.getChildren()) {
            TreeItem<Path> hit = findInTree(c, target);
            if (hit != null) return hit;
        }
        return null;
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
        resolvedPathLabel.setText("Resolved path: " + resolvedTargetPathText());
        resolvedNameLabel.setText("Resolved name: " + resolvedNameText());
        onValidityChange.run();
    }

    private Optional<String> validationMessage() {
        if (selectedParentDir().isEmpty()) return Optional.of("Pick a parent directory.");
        String name = folderName();
        if (name.isEmpty()) return Optional.of("Folder name is required.");
        if (!FOLDER_NAME.matcher(name).matches()) return Optional.of("Folder name contains invalid characters.");
        Path target = targetPath();
        if (Files.exists(target)) return Optional.of("Target already exists: " + target);
        return Optional.empty();
    }

    private Path targetPath() {
        Path parent = selectedParentDir().orElse(terragruntRoot);
        if (mode instanceof WizardMode.IncludeFromModule) return parent.resolve(folderName() + ".hcl");
        return parent.resolve(folderName()).resolve("terragrunt.hcl");
    }

    private String resolvedTargetPathText() {
        if (selectedParentDir().isEmpty() || folderName().isEmpty()) return "(complete the fields above)";
        return targetPath().toString();
    }

    private String resolvedNameText() {
        if (!(mode instanceof WizardMode.ResourceFromInclude)) {
            return "(name not synthesised for this mode)";
        }
        LocationSuggester.Suggestion s = suggestion();
        var match = AzureResourceInferrer.inferByName(template.displayName());
        String prefix = solutions.onz.toolbox.gruntface.name.AzureResourceNaming.prefixFor(match.resource().id())
            .orElse("res");
        String purpose = s.purpose().orElse(folderName().isEmpty() ? "?" : folderName());
        String region = s.locationShort().orElse("?");
        String env = s.environment().orElse("?");
        return prefix + "-" + purpose + "-" + region + "-" + env;
    }

    private String defaultFolderName(ResourceTemplate t) {
        if (t == null) return "";
        return t.displayName().toLowerCase().replaceAll("[^a-z0-9._-]+", "-");
    }
}

package solutions.onz.toolbox.gruntface.ui;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import solutions.onz.toolbox.gruntface.app.GruntFaceApplication;
import solutions.onz.toolbox.gruntface.discovery.DiscoveryService;
import solutions.onz.toolbox.gruntface.hcl.Hcl4jHclService;
import solutions.onz.toolbox.gruntface.hcl.HclService;
import solutions.onz.toolbox.gruntface.hcl.InputsRenderer;
import solutions.onz.toolbox.gruntface.model.ByteRange;
import solutions.onz.toolbox.gruntface.model.CommonHcl;
import solutions.onz.toolbox.gruntface.model.ExternalModule;
import solutions.onz.toolbox.gruntface.model.InfraGraph;
import solutions.onz.toolbox.gruntface.model.InputValue;
import solutions.onz.toolbox.gruntface.model.Module;
import solutions.onz.toolbox.gruntface.model.Unit;
import solutions.onz.toolbox.gruntface.create.CreatePlan;
import solutions.onz.toolbox.gruntface.create.CreateRequest;
import solutions.onz.toolbox.gruntface.create.CreateService;
import solutions.onz.toolbox.gruntface.create.InfraGraphPatcher;
import solutions.onz.toolbox.gruntface.create.ResourceTemplate;
import solutions.onz.toolbox.gruntface.create.WizardMode;
import solutions.onz.toolbox.gruntface.ui.create.CreatePreviewDialog;
import solutions.onz.toolbox.gruntface.ui.create.NewResourceWizard;
import solutions.onz.toolbox.gruntface.ui.create.WizardPrefill;
import solutions.onz.toolbox.gruntface.ui.graph.DiagramView;
import solutions.onz.toolbox.gruntface.ui.help.HelpDialog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainController {

    @FXML private StackPane graphHost;
    @FXML private VBox inspectorHost;
    @FXML private Label emptyState;
    @FXML private Label statusLabel;
    @FXML private CheckMenuItem showModulesCheck;
    @FXML private CheckBox showModulesToolbar;
    @FXML private CheckMenuItem showUnlinkedModulesCheck;
    @FXML private CheckBox showUnlinkedModulesToolbar;
    @FXML private CheckMenuItem showIncludesCheck;
    @FXML private CheckBox showIncludesToolbar;
    @FXML private CheckMenuItem showUnlinkedIncludesCheck;
    @FXML private CheckBox showUnlinkedIncludesToolbar;
    @FXML private SplitPane splitPane;
    @FXML private RadioMenuItem themeLightItem;
    @FXML private RadioMenuItem themeDarkItem;
    @FXML private RadioMenuItem themeAutoItem;

    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "gruntface-io"); t.setDaemon(true); return t;
    });
    private final HclService hcl = new Hcl4jHclService();
    private final DiscoveryService discovery = new DiscoveryService(hcl);
    private final CreateService createService = new CreateService();

    private DiagramView graphView;
    private InspectorController inspector;
    private InfraGraph currentGraph;
    private Path terragruntRoot;
    private Optional<Path> commonsOverride = Optional.empty();
    private Optional<Path> modulesOverride = Optional.empty();
    private Optional<String> postLoadSelectId = Optional.empty();

    @FXML
    public void initialize() {
        graphView = new DiagramView();
        graphView.setVisible(false);
        graphHost.getChildren().add(graphView);
        graphView.setOnSelect(id -> Platform.runLater(() -> onNodeSelected(id)));
        graphView.setOnDeselect(() -> Platform.runLater(this::onNothingSelected));
        graphView.setOnContextMenuRequest((screenX, screenY, nodeId) -> Platform.runLater(() ->
            showCanvasOrNodeContextMenu(screenX, screenY, nodeId)));

        try {
            javafx.fxml.FXMLLoader fx = new javafx.fxml.FXMLLoader(getClass().getResource(
                    "/solutions/onz/toolbox/gruntface/ui/inspector.fxml"));
            javafx.scene.Parent insp = fx.load();
            inspector = fx.getController();
            inspector.setMainController(this);
            inspectorHost.getChildren().setAll(insp);
            VBox.setVgrow(insp, Priority.ALWAYS);
            inspector.showEmpty();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Prefs.terragruntRoot().ifPresent(p -> terragruntRoot = p);
        commonsOverride = Prefs.commonsRoot();
        modulesOverride = Prefs.modulesRoot();
        boolean sm = Prefs.showModules();
        boolean si = Prefs.showIncludes();
        boolean sum = Prefs.showUnlinkedModules();
        boolean sui = Prefs.showUnlinkedIncludes();
        showModulesCheck.setSelected(sm);
        showModulesToolbar.setSelected(sm);
        showUnlinkedModulesCheck.setSelected(sum);
        showUnlinkedModulesToolbar.setSelected(sum);
        showIncludesCheck.setSelected(si);
        showIncludesToolbar.setSelected(si);
        showUnlinkedIncludesCheck.setSelected(sui);
        showUnlinkedIncludesToolbar.setSelected(sui);
        updateUnlinkedTogglesEnabled();
        if (terragruntRoot != null) loadAsync();

        ThemeManager tm = GruntFaceApplication.themeManager();
        switch (tm.getMode()) {
            case LIGHT -> themeLightItem.setSelected(true);
            case DARK  -> themeDarkItem.setSelected(true);
            case AUTO  -> themeAutoItem.setSelected(true);
        }
    }

    @FXML
    void onOpenTerragrunt() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose Terragrunt project root");
        if (terragruntRoot != null && terragruntRoot.toFile().isDirectory()) {
            chooser.setInitialDirectory(terragruntRoot.toFile());
        }
        File dir = chooser.showDialog(graphHost.getScene().getWindow());
        if (dir == null) return;
        terragruntRoot = dir.toPath();
        Prefs.terragruntRoot(terragruntRoot);
        loadAsync();
    }

    @FXML
    void onSetCommonsLocation() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose commons directory (overrides auto-detect)");
        commonsOverride.ifPresent(p -> { if (p.toFile().isDirectory()) chooser.setInitialDirectory(p.toFile()); });
        File dir = chooser.showDialog(graphHost.getScene().getWindow());
        if (dir == null) return;
        commonsOverride = Optional.of(dir.toPath());
        Prefs.commonsRoot(dir.toPath());
        if (terragruntRoot != null) loadAsync();
    }

    @FXML
    void onSetModulesLocation() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose Terraform modules directory (overrides auto-detect)");
        modulesOverride.ifPresent(p -> { if (p.toFile().isDirectory()) chooser.setInitialDirectory(p.toFile()); });
        File dir = chooser.showDialog(graphHost.getScene().getWindow());
        if (dir == null) return;
        modulesOverride = Optional.of(dir.toPath());
        Prefs.modulesRoot(dir.toPath());
        if (terragruntRoot != null) loadAsync();
    }

    @FXML
    void onClearLocationOverrides() {
        commonsOverride = Optional.empty();
        modulesOverride = Optional.empty();
        Prefs.clearCommonsRoot();
        Prefs.clearModulesRoot();
        if (terragruntRoot != null) loadAsync();
    }

    @FXML
    void onReload() { if (terragruntRoot != null) loadAsync(); }

    @FXML
    void onFit() { if (graphView != null) graphView.fit(); }

    @FXML
    void onToggleShowModules(ActionEvent e) {
        boolean show;
        if (e.getSource() == showModulesCheck) {
            show = showModulesCheck.isSelected();
            showModulesToolbar.setSelected(show);
        } else {
            show = showModulesToolbar.isSelected();
            showModulesCheck.setSelected(show);
        }
        Prefs.showModules(show);
        updateUnlinkedTogglesEnabled();
        rerender();
    }

    @FXML
    void onToggleShowIncludes(ActionEvent e) {
        boolean show;
        if (e.getSource() == showIncludesCheck) {
            show = showIncludesCheck.isSelected();
            showIncludesToolbar.setSelected(show);
        } else {
            show = showIncludesToolbar.isSelected();
            showIncludesCheck.setSelected(show);
        }
        Prefs.showIncludes(show);
        updateUnlinkedTogglesEnabled();
        rerender();
    }

    @FXML
    void onToggleShowUnlinkedModules(ActionEvent e) {
        boolean show;
        if (e.getSource() == showUnlinkedModulesCheck) {
            show = showUnlinkedModulesCheck.isSelected();
            showUnlinkedModulesToolbar.setSelected(show);
        } else {
            show = showUnlinkedModulesToolbar.isSelected();
            showUnlinkedModulesCheck.setSelected(show);
        }
        Prefs.showUnlinkedModules(show);
        rerender();
    }

    @FXML
    void onToggleShowUnlinkedIncludes(ActionEvent e) {
        boolean show;
        if (e.getSource() == showUnlinkedIncludesCheck) {
            show = showUnlinkedIncludesCheck.isSelected();
            showUnlinkedIncludesToolbar.setSelected(show);
        } else {
            show = showUnlinkedIncludesToolbar.isSelected();
            showUnlinkedIncludesCheck.setSelected(show);
        }
        Prefs.showUnlinkedIncludes(show);
        rerender();
    }

    private void updateUnlinkedTogglesEnabled() {
        boolean modulesOn = showModulesCheck.isSelected();
        showUnlinkedModulesCheck.setDisable(!modulesOn);
        showUnlinkedModulesToolbar.setDisable(!modulesOn);
        boolean includesOn = showIncludesCheck.isSelected();
        showUnlinkedIncludesCheck.setDisable(!includesOn);
        showUnlinkedIncludesToolbar.setDisable(!includesOn);
    }

    @FXML
    void onQuit() { Platform.exit(); }

    @FXML void onThemeLight() {
        GruntFaceApplication.themeManager().setMode(ThemeManager.Mode.LIGHT);
    }
    @FXML void onThemeDark() {
        GruntFaceApplication.themeManager().setMode(ThemeManager.Mode.DARK);
    }
    @FXML void onThemeAuto() {
        GruntFaceApplication.themeManager().setMode(ThemeManager.Mode.AUTO);
    }

    @FXML
    void onShowUserGuide() {
        HelpDialog.show(graphHost.getScene().getWindow());
    }

    @FXML
    void onAbout() {
        Alert a = new Alert(Alert.AlertType.INFORMATION,
            "GruntFace v1.2.0.2 — Terragrunt IaC Visualizer. © Pavel Onz 2026");
        a.setHeaderText("About GruntFace");

        ImageView headerIcon = new ImageView(
                new Image(Objects.requireNonNull(getClass().getResourceAsStream("icon.png")))
        );

        headerIcon.setFitWidth(64);
        headerIcon.setFitHeight(64);

        a.setGraphic(headerIcon);
        a.showAndWait();
    }

    @FXML void onNewResourceFromInclude() {
        openWizard(new WizardPrefill(
            Optional.of(new WizardMode.ResourceFromInclude()), Optional.empty(), Optional.empty()));
    }
    @FXML void onNewResourceFromModule() {
        openWizard(new WizardPrefill(
            Optional.of(new WizardMode.ResourceFromModule()), Optional.empty(), Optional.empty()));
    }
    @FXML void onNewIncludeFromModule() {
        openWizard(new WizardPrefill(
            Optional.of(new WizardMode.IncludeFromModule()), Optional.empty(), Optional.empty()));
    }

    private void openWizard(WizardPrefill prefill) {
        if (currentGraph == null || terragruntRoot == null) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Open a Terragrunt project first.");
            a.showAndWait();
            return;
        }
        try {
            NewResourceWizard.open(
                graphHost.getScene().getWindow(),
                currentGraph, terragruntRoot, prefill,
                req -> onWizardConfirmed(req));
        } catch (IOException e) {
            Alert a = new Alert(Alert.AlertType.ERROR, e.getMessage());
            a.setHeaderText("Failed to open the wizard");
            a.showAndWait();
        }
    }

    private void onWizardConfirmed(CreateRequest req) {
        CreatePlan plan = createService.plan(req, terragruntRoot);
        if (plan.conflictsWith().isPresent()) {
            Alert a = new Alert(Alert.AlertType.ERROR,
                "Target file already exists: " + plan.conflictsWith().get());
            a.setHeaderText("File exists");
            a.showAndWait();
            return;
        }
        boolean go = CreatePreviewDialog.show(graphHost.getScene().getWindow(), plan);
        if (!go) return;

        io.submit(() -> {
            try {
                createService.commit(plan);
                if (req.mode() instanceof WizardMode.IncludeFromModule) {
                    CommonHcl c = discovery.linkSingleCommon(plan.targetFile());
                    Platform.runLater(() -> {
                        currentGraph = InfraGraphPatcher.addCommon(currentGraph, c);
                        rerender();
                        graphView.select("c::" + c.file());
                        statusLabel.setText("Created " + plan.targetFile().getFileName());
                    });
                } else {
                    Unit u = discovery.linkSingleUnit(plan.targetFile(), currentGraph);
                    Platform.runLater(() -> {
                        currentGraph = InfraGraphPatcher.addUnit(currentGraph, u);
                        rerender();
                        graphView.select("u::" + u.file());
                        inspector.showUnit(u, findUsedModule(u), findUsedExternal(u), findUsedCommon(u));
                        statusLabel.setText("Created " + plan.targetFile().getFileName());
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    Alert a = new Alert(Alert.AlertType.ERROR, e.getMessage());
                    a.setHeaderText("Create failed");
                    a.showAndWait();
                });
            }
        });
    }

    public void openWizardFromInspector(WizardMode mode, ResourceTemplate template) {
        openWizard(new WizardPrefill(Optional.of(mode), Optional.of(template), Optional.empty()));
    }

    private void showCanvasOrNodeContextMenu(double screenX, double screenY, String nodeId) {
        ContextMenu menu = new ContextMenu();
        if (nodeId == null) {
            addItem(menu, "New Resource (extend Include)…", "fth-file-plus",
                () -> openWizard(new WizardPrefill(
                    Optional.of(new WizardMode.ResourceFromInclude()),
                    Optional.empty(), Optional.empty())));
            addItem(menu, "New Resource (extend Module)…", "fth-file-plus",
                () -> openWizard(new WizardPrefill(
                    Optional.of(new WizardMode.ResourceFromModule()),
                    Optional.empty(), Optional.empty())));
            addItem(menu, "New Include…", "fth-file-plus",
                () -> openWizard(new WizardPrefill(
                    Optional.of(new WizardMode.IncludeFromModule()),
                    Optional.empty(), Optional.empty())));
        } else if (nodeId.startsWith("c::")) {
            CommonHcl include = findCommonByNodeId(nodeId);
            if (include != null) {
                addItem(menu, "Create resource from this…", "fth-file-plus",
                    () -> openWizard(new WizardPrefill(
                        Optional.of(new WizardMode.ResourceFromInclude()),
                        Optional.of(new ResourceTemplate.IncludeTemplate(include)),
                        Optional.empty())));
                addItem(menu, "Edit raw HCL…", "fth-edit-3", () -> editCommonRawHcl(include));
                addItem(menu, "Delete…", "fth-trash-2", () -> deleteCommon(include));
            }
        } else if (nodeId.startsWith("u::")) {
            Unit unit = findUnitByNodeId(nodeId);
            if (unit != null) {
                addItem(menu, "Edit raw HCL…", "fth-edit-3", () -> editUnitRawHcl(unit));
                addItem(menu, "Delete…", "fth-trash-2", () -> deleteUnit(unit));
            }
        } else if (nodeId.startsWith("m::")) {
            Module m = findModuleByNodeId(nodeId);
            if (m != null) {
                addItem(menu, "Create resource from this…", "fth-file-plus",
                    () -> openWizard(new WizardPrefill(
                        Optional.of(new WizardMode.ResourceFromModule()),
                        Optional.of(new ResourceTemplate.LocalModuleTemplate(m)),
                        Optional.empty())));
                addItem(menu, "Create Include from this…", "fth-file-plus",
                    () -> openWizard(new WizardPrefill(
                        Optional.of(new WizardMode.IncludeFromModule()),
                        Optional.of(new ResourceTemplate.LocalModuleTemplate(m)),
                        Optional.empty())));
            }
        } else if (nodeId.startsWith("x::")) {
            ExternalModule ext = findExternalByNodeId(nodeId);
            if (ext != null) {
                addItem(menu, "Create resource from this…", "fth-file-plus",
                    () -> openWizard(new WizardPrefill(
                        Optional.of(new WizardMode.ResourceFromModule()),
                        Optional.of(new ResourceTemplate.ExternalModuleTemplate(ext)),
                        Optional.empty())));
                addItem(menu, "Create Include from this…", "fth-file-plus",
                    () -> openWizard(new WizardPrefill(
                        Optional.of(new WizardMode.IncludeFromModule()),
                        Optional.of(new ResourceTemplate.ExternalModuleTemplate(ext)),
                        Optional.empty())));
            }
        }
        if (!menu.getItems().isEmpty()) {
            menu.show(graphHost.getScene().getWindow(), screenX, screenY);
        }
    }

    private static void addItem(ContextMenu menu, String label, Runnable action) {
        addItem(menu, label, null, action);
    }

    private static void addItem(ContextMenu menu, String label, String iconLiteral, Runnable action) {
        MenuItem mi = new MenuItem(label);
        if (iconLiteral != null) {
            mi.setGraphic(new org.kordamp.ikonli.javafx.FontIcon(iconLiteral));
        }
        mi.setOnAction(e -> action.run());
        menu.getItems().add(mi);
    }

    private CommonHcl findCommonByNodeId(String nodeId) {
        if (currentGraph == null) return null;
        String path = nodeId.substring(3);
        for (CommonHcl c : currentGraph.commons()) {
            if (c.file().toString().equals(path)) return c;
        }
        return null;
    }

    private Module findModuleByNodeId(String nodeId) {
        if (currentGraph == null) return null;
        String path = nodeId.substring(3);
        for (Module m : currentGraph.modules()) {
            if (m.dir().toString().equals(path)) return m;
        }
        return null;
    }

    private ExternalModule findExternalByNodeId(String nodeId) {
        if (currentGraph == null) return null;
        String ref = nodeId.substring(3);
        for (ExternalModule x : currentGraph.externals()) {
            if (x.sourceRef().equals(ref)) return x;
        }
        return null;
    }

    private Unit findUnitByNodeId(String nodeId) {
        if (currentGraph == null) return null;
        String path = nodeId.substring(3);
        for (Unit u : currentGraph.units()) {
            if (u.file().toString().equals(path)) return u;
        }
        return null;
    }

    private void loadAsync() {
        System.err.println("[DEBUG] loadAsync entry, terragruntRoot=" + terragruntRoot
            + " commons=" + commonsOverride + " modules=" + modulesOverride);
        statusLabel.setText("Loading…");
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(60, 60);
        graphHost.getChildren().add(pi);
        graphView.setVisible(false);
        emptyState.setVisible(false);

        Path tg = terragruntRoot;
        Optional<Path> commons = commonsOverride;
        Optional<Path> modules = modulesOverride;
        io.submit(() -> {
            try {
                System.err.println("[DEBUG] io: discovery.load starting");
                long t0 = System.nanoTime();
                InfraGraph g;
                if (commons.isPresent() || modules.isPresent()) {
                    // Resolve to the actual roots, falling back to auto-detect for any that's empty.
                    g = discovery.load(tg, commons, modules);
                } else {
                    g = discovery.load(tg);
                }
                System.err.println("[DEBUG] io: discovery.load returned in "
                    + ((System.nanoTime() - t0) / 1_000_000) + "ms; units=" + g.units().size());
                Platform.runLater(() -> onLoaded(g, pi));
            } catch (Throwable e) {
                System.err.println("[DEBUG] io: discovery.load THREW " + e);
                e.printStackTrace();
                Platform.runLater(() -> onLoadFailed(e instanceof Exception ex ? ex
                    : new RuntimeException(e), pi));
            }
        });
    }

    private void onLoaded(InfraGraph g, ProgressIndicator pi) {
        System.err.println("[DEBUG] onLoaded entry, units=" + g.units().size());
        graphHost.getChildren().remove(pi);
        currentGraph = g;
        graphView.setVisible(true);
        rerender();
        statusLabel.setText(
            g.units().size() + " units · " +
            g.commons().size() + " commons · " +
            g.modules().size() + " modules · " +
            g.externals().size() + " external · " +
            g.cycles().size() + " cycle(s)"
        );
        if (postLoadSelectId.isPresent()) {
            String id = postLoadSelectId.get();
            postLoadSelectId = Optional.empty();
            graphView.select(id);
        }
    }

    private void onLoadFailed(Exception e, ProgressIndicator pi) {
        graphHost.getChildren().remove(pi);
        statusLabel.setText("Load failed: " + e.getMessage());
        Alert a = new Alert(Alert.AlertType.ERROR, e.getMessage());
        a.setHeaderText("Failed to load project");
        a.showAndWait();
    }

    private void onNodeSelected(String nodeId) {
        if (currentGraph == null) return;
        if (nodeId.startsWith("m::")) {
            String path = nodeId.substring(3);
            currentGraph.modules().stream()
                .filter(m -> m.dir().toString().equals(path))
                .findFirst()
                .ifPresent(inspector::showModule);
        } else if (nodeId.startsWith("x::")) {
            String ref = nodeId.substring(3);
            currentGraph.externals().stream()
                .filter(e -> e.sourceRef().equals(ref))
                .findFirst()
                .ifPresent(inspector::showExternal);
        } else if (nodeId.startsWith("c::")) {
            String path = nodeId.substring(3);
            currentGraph.commons().stream()
                .filter(c -> c.file().toString().equals(path))
                .findFirst()
                .ifPresent(inspector::showCommon);
        } else if (nodeId.startsWith("u::")) {
            String path = nodeId.substring(3);
            currentGraph.units().stream()
                .filter(u -> u.file().toString().equals(path))
                .findFirst()
                .ifPresent(u -> inspector.showUnit(
                    u,
                    findUsedModule(u),
                    findUsedExternal(u),
                    findUsedCommon(u)
                ));
        }
        graphView.select(nodeId);
    }

    private void onNothingSelected() {
        statusLabel.setText("Nothing selected");
        inspector.showEmpty();
        graphView.clearSelection();
    }

    private Module findUsedModule(Unit u) {
        if (currentGraph == null) return null;
        for (InfraGraph.UsesEdge e : currentGraph.usesEdges()) {
            Unit from = (Unit) e.from();
            if (from.file().equals(u.file()) && e.to() instanceof Module m) return m;
        }
        return null;
    }

    private ExternalModule findUsedExternal(Unit u) {
        if (currentGraph == null) return null;
        for (InfraGraph.UsesEdge e : currentGraph.usesEdges()) {
            Unit from = (Unit) e.from();
            if (from.file().equals(u.file()) && e.to() instanceof ExternalModule x) return x;
        }
        return null;
    }

    private CommonHcl findUsedCommon(Unit u) {
        if (currentGraph == null) return null;
        for (InfraGraph.IncludesEdge e : currentGraph.includesEdges()) {
            if (e.from().file().equals(u.file())) return e.to();
        }
        return null;
    }

    public void saveUnit(Unit unit, Map<String, InputValue> newInputs, List<String> declaredOrder) {
        try {
            String onDisk = Files.readString(unit.file());
            if (!onDisk.equals(unit.originalText())) {
                Alert a = new Alert(Alert.AlertType.WARNING,
                    "The file has changed on disk since we read it. Reload before saving.");
                a.setHeaderText("File changed on disk");
                a.showAndWait();
                return;
            }
            ByteRange range = unit.inputsRange().orElseThrow(() -> new IllegalStateException("no inputs block"));
            String renderedBlock = InputsRenderer.render(newInputs, declaredOrder);
            String newText = unit.originalText().substring(0, range.start())
                + renderedBlock
                + unit.originalText().substring(range.end());

            boolean go = DiffDialog.show(graphHost.getScene().getWindow(),
                "Save changes — " + unit.file().getFileName(),
                unit.originalText(), newText);
            if (!go) return;

            Path tmp = unit.file().resolveSibling(unit.file().getFileName() + ".gruntface.tmp");
            Files.writeString(tmp, newText);
            Files.move(tmp, unit.file(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            Unit reparsed = hcl.parseUnit(unit.file());
            currentGraph = replaceUnit(currentGraph, unit, reparsed);
            rerender();
            inspector.showUnit(reparsed, findUsedModule(reparsed), findUsedExternal(reparsed), findUsedCommon(reparsed));
            statusLabel.setText("Saved " + unit.file().getFileName());
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage());
            a.setHeaderText("Save failed");
            a.showAndWait();
        }
    }

    public void saveUnitFreeText(Unit unit, String rawInsideBraces) {
        try {
            String onDisk = Files.readString(unit.file());
            if (!onDisk.equals(unit.originalText())) {
                Alert a = new Alert(Alert.AlertType.WARNING,
                    "File changed on disk — reload before saving.");
                a.showAndWait();
                return;
            }
            ByteRange range = unit.inputsRange().orElseThrow();
            String newBlock = "inputs = {\n" + indent(rawInsideBraces) + "\n}";
            String newText = unit.originalText().substring(0, range.start())
                + newBlock
                + unit.originalText().substring(range.end());
            boolean go = DiffDialog.show(graphHost.getScene().getWindow(),
                "Save changes — " + unit.file().getFileName(),
                unit.originalText(), newText);
            if (!go) return;
            Path tmp = unit.file().resolveSibling(unit.file().getFileName() + ".gruntface.tmp");
            Files.writeString(tmp, newText);
            Files.move(tmp, unit.file(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            Unit reparsed = hcl.parseUnit(unit.file());
            currentGraph = replaceUnit(currentGraph, unit, reparsed);
            rerender();
            inspector.showUnit(reparsed, findUsedModule(reparsed), findUsedExternal(reparsed), findUsedCommon(reparsed));
            statusLabel.setText("Saved " + unit.file().getFileName());
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage());
            a.setHeaderText("Save failed");
            a.showAndWait();
        }
    }

    /**
     * Saves a Unit's full raw HCL after a HclEditDialog session. Drift check, then
     * DiffDialog, atomic write, reparse via discovery, and InfraGraphPatcher.replaceUnit.
     *
     * @return true on a successful save (caller may close the editor); false if the user
     *         cancelled the diff dialog, the file drifted on disk, or an error occurred.
     */
    private boolean saveUnitRawFile(Unit oldU, String originalText, String newText) {
        try {
            if (originalText.equals(newText)) {
                statusLabel.setText("No changes");
                return true;
            }
            String onDisk = Files.readString(oldU.file());
            if (!onDisk.equals(originalText)) {
                Alert a = new Alert(Alert.AlertType.WARNING,
                    "The file has changed on disk since we opened it. Reload before saving.");
                a.setHeaderText("File changed on disk");
                a.showAndWait();
                return false;
            }
            boolean go = DiffDialog.show(graphHost.getScene().getWindow(),
                "Save changes — " + oldU.file().getFileName(),
                originalText, newText);
            if (!go) return false;

            Path tmp = oldU.file().resolveSibling(oldU.file().getFileName() + ".gruntface.tmp");
            Files.writeString(tmp, newText);
            Files.move(tmp, oldU.file(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            Unit newU = discovery.linkSingleUnit(oldU.file(), currentGraph);
            currentGraph = InfraGraphPatcher.replaceUnit(currentGraph, oldU, newU);
            rerender();
            graphView.select("u::" + newU.file());
            inspector.showUnit(newU,
                findUsedModule(newU), findUsedExternal(newU), findUsedCommon(newU));
            statusLabel.setText("Saved " + oldU.file().getFileName());
            return true;
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage());
            a.setHeaderText("Save failed");
            a.showAndWait();
            return false;
        }
    }

    /**
     * Saves a CommonHcl's full raw HCL. Because a Common's locals interpolate into every
     * including Unit's terraform { source }, we don't patch in place — we trigger a full
     * loadAsync and reselect the same node after the reload completes.
     */
    private boolean saveCommonRawFile(CommonHcl oldC, String originalText, String newText) {
        try {
            if (originalText.equals(newText)) {
                statusLabel.setText("No changes");
                return true;
            }
            String onDisk = Files.readString(oldC.file());
            if (!onDisk.equals(originalText)) {
                Alert a = new Alert(Alert.AlertType.WARNING,
                    "The file has changed on disk since we opened it. Reload before saving.");
                a.setHeaderText("File changed on disk");
                a.showAndWait();
                return false;
            }
            boolean go = DiffDialog.show(graphHost.getScene().getWindow(),
                "Save changes — " + oldC.file().getFileName(),
                originalText, newText);
            if (!go) return false;

            Path tmp = oldC.file().resolveSibling(oldC.file().getFileName() + ".gruntface.tmp");
            Files.writeString(tmp, newText);
            Files.move(tmp, oldC.file(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            postLoadSelectId = Optional.of("c::" + oldC.file());
            statusLabel.setText("Saved " + oldC.file().getFileName());
            loadAsync();
            return true;
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage());
            a.setHeaderText("Save failed");
            a.showAndWait();
            return false;
        }
    }

    private void editUnitRawHcl(Unit unit) {
        try {
            String original = Files.readString(unit.file());
            solutions.onz.toolbox.gruntface.ui.edit.HclEditDialog.show(
                graphHost.getScene().getWindow(),
                unit.file(),
                original,
                newText -> saveUnitRawFile(unit, original, newText));
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage());
            a.setHeaderText("Could not open file");
            a.showAndWait();
        }
    }

    private void editCommonRawHcl(CommonHcl common) {
        try {
            String original = Files.readString(common.file());
            solutions.onz.toolbox.gruntface.ui.edit.HclEditDialog.show(
                graphHost.getScene().getWindow(),
                common.file(),
                original,
                newText -> saveCommonRawFile(common, original, newText));
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage());
            a.setHeaderText("Could not open file");
            a.showAndWait();
        }
    }

    private void deleteUnit(Unit unit) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete " + unit.file().getFileName() + "? This cannot be undone.");
        confirm.setHeaderText("Delete resource");
        var result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != javafx.scene.control.ButtonType.OK) return;

        try {
            Files.delete(unit.file());
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage());
            a.setHeaderText("Delete failed");
            a.showAndWait();
            return;
        }
        currentGraph = InfraGraphPatcher.removeUnit(currentGraph, unit);
        rerender();
        graphView.clearSelection();
        inspector.showEmpty();
        statusLabel.setText("Deleted " + unit.file().getFileName());
    }

    private void deleteCommon(CommonHcl common) {
        java.util.List<Unit> refs = new java.util.ArrayList<>();
        java.util.Set<Path> seen = new java.util.HashSet<>();
        for (InfraGraph.IncludesEdge e : currentGraph.includesEdges()) {
            if (e.to().file().equals(common.file()) && seen.add(e.from().file())) {
                refs.add(e.from());
            }
        }
        if (!refs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(refs.size(), 5); i++) {
                if (i > 0) sb.append(", ");
                sb.append(refs.get(i).file().getFileName());
            }
            if (refs.size() > 5) sb.append(" and ").append(refs.size() - 5).append(" more");
            Alert a = new Alert(Alert.AlertType.INFORMATION,
                common.file().getFileName() + " is included by " + refs.size()
                    + " unit(s): " + sb + ". Remove those includes first.");
            a.setHeaderText("Include is in use");
            a.showAndWait();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete " + common.file().getFileName() + "? This cannot be undone.");
        confirm.setHeaderText("Delete include");
        var result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != javafx.scene.control.ButtonType.OK) return;

        try {
            Files.delete(common.file());
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage());
            a.setHeaderText("Delete failed");
            a.showAndWait();
            return;
        }
        currentGraph = InfraGraphPatcher.removeCommon(currentGraph, common);
        rerender();
        graphView.clearSelection();
        inspector.showEmpty();
        statusLabel.setText("Deleted " + common.file().getFileName());
    }

    private static String indent(String s) {
        StringBuilder sb = new StringBuilder();
        for (String line : s.lines().toList()) sb.append("  ").append(line).append('\n');
        if (sb.length() > 0 && sb.charAt(sb.length()-1) == '\n') sb.setLength(sb.length()-1);
        return sb.toString();
    }

    private void rerender() {
        if (currentGraph == null || terragruntRoot == null) return;
        graphView.render(currentGraph, terragruntRoot, new DiagramView.RenderOptions(
            showModulesCheck.isSelected(),
            showIncludesCheck.isSelected(),
            showUnlinkedModulesCheck.isSelected(),
            showUnlinkedIncludesCheck.isSelected()));
    }

    private static InfraGraph replaceUnit(InfraGraph g, Unit oldU, Unit newU) {
        java.util.List<Unit> units = new java.util.ArrayList<>(g.units());
        for (int i = 0; i < units.size(); i++) {
            if (units.get(i).file().equals(oldU.file())) { units.set(i, newU); break; }
        }
        java.util.List<InfraGraph.UsesEdge> uses = new java.util.ArrayList<>();
        for (InfraGraph.UsesEdge e : g.usesEdges()) {
            Unit from = ((Unit) e.from()).file().equals(oldU.file()) ? newU : (Unit) e.from();
            uses.add(new InfraGraph.UsesEdge(from, e.to()));
        }
        java.util.List<InfraGraph.DependsOnEdge> deps = new java.util.ArrayList<>();
        for (InfraGraph.DependsOnEdge e : g.dependsEdges()) {
            Unit from = e.from().file().equals(oldU.file()) ? newU : e.from();
            Unit to   = e.to().file().equals(oldU.file()) ? newU : e.to();
            deps.add(new InfraGraph.DependsOnEdge(from, to));
        }
        java.util.List<InfraGraph.IncludesEdge> incs = new java.util.ArrayList<>();
        for (InfraGraph.IncludesEdge e : g.includesEdges()) {
            Unit from = e.from().file().equals(oldU.file()) ? newU : e.from();
            incs.add(new InfraGraph.IncludesEdge(from, e.to()));
        }
        return new InfraGraph(
            units, g.modules(), g.externals(), g.commons(),
            uses, deps, incs, g.cycles()
        );
    }

}

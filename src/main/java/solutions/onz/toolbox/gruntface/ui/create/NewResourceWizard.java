package solutions.onz.toolbox.gruntface.ui.create;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import solutions.onz.toolbox.gruntface.create.*;
import solutions.onz.toolbox.gruntface.model.InfraGraph;
import solutions.onz.toolbox.gruntface.model.InputValue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

public class NewResourceWizard {

    @FXML private Label step1Header;
    @FXML private Label step2Header;
    @FXML private Label step3Header;
    @FXML private Label step4Header;
    @FXML private StackPane body;
    @FXML private Button cancelBtn;
    @FXML private Button backBtn;
    @FXML private Button nextBtn;
    @FXML private Label statusLabel;

    private Stage stage;
    private InfraGraph graph;
    private Path terragruntRoot;
    private Consumer<CreateRequest> onConfirm = req -> {};

    private Step1TemplatePicker step1;
    private Step2Location step2;
    private Step3Dependencies step3deps;
    private Step4Inputs step4;
    private Parent step1Root;
    private Parent step2Root;
    private Parent step3depsRoot;
    private Parent step4Root;

    private int currentStep = 1;
    private LinkedHashMap<String, DependencyDecl> step3depsCache = new LinkedHashMap<>();
    private LinkedHashMap<String, InputValue> step4Cache = new LinkedHashMap<>();

    public static NewResourceWizard open(Window owner, InfraGraph graph, Path terragruntRoot,
                                          WizardPrefill prefill, Consumer<CreateRequest> onConfirm) throws IOException {
        FXMLLoader loader = new FXMLLoader(NewResourceWizard.class.getResource(
            "/solutions/onz/toolbox/gruntface/ui/create/new-resource-wizard.fxml"));
        Parent root = loader.load();
        NewResourceWizard ctrl = loader.getController();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setScene(new Scene(root));
        stage.setTitle("New …");
        ctrl.stage = stage;
        ctrl.graph = graph;
        ctrl.terragruntRoot = terragruntRoot;
        ctrl.onConfirm = onConfirm;
        ctrl.buildSteps(prefill);
        stage.getScene().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) { ctrl.onCancel(); ev.consume(); }
            if (ev.getCode() == KeyCode.ENTER && ev.isControlDown() && ctrl.currentStep == 4) {
                ctrl.onNext(); ev.consume();
            }
        });
        stage.show();
        return ctrl;
    }

    private void buildSteps(WizardPrefill prefill) throws IOException {
        // Step 1
        FXMLLoader l1 = new FXMLLoader(getClass().getResource(
            "/solutions/onz/toolbox/gruntface/ui/create/step1-template.fxml"));
        step1Root = l1.load();
        step1 = l1.getController();
        step1.init(graph);
        prefill.mode().ifPresent(step1::setMode);
        prefill.template().ifPresent(step1::selectTemplate);
        step1.setOnValidityChange(this::updateButtons);

        // Step 2
        FXMLLoader l2 = new FXMLLoader(getClass().getResource(
            "/solutions/onz/toolbox/gruntface/ui/create/step2-location.fxml"));
        step2Root = l2.load();
        step2 = l2.getController();
        step2.setOnValidityChange(this::updateButtons);

        // Step 3 (Dependencies)
        FXMLLoader l3 = new FXMLLoader(getClass().getResource(
            "/solutions/onz/toolbox/gruntface/ui/create/step3-dependencies.fxml"));
        step3depsRoot = l3.load();
        step3deps = l3.getController();
        step3deps.setOnValidityChange(this::updateButtons);

        // Step 4 (Inputs)
        FXMLLoader l4 = new FXMLLoader(getClass().getResource(
            "/solutions/onz/toolbox/gruntface/ui/create/step4-inputs.fxml"));
        step4Root = l4.load();
        step4 = l4.getController();
        step4.setOnValidityChange(this::updateButtons);

        showStep(1);
    }

    @FXML private void onCancel() { stage.close(); }

    @FXML private void onBack() {
        if (currentStep == 4) {
            step4Cache = step4.values();
            showStep(skipStep3() ? 2 : 3);
            return;
        }
        if (currentStep == 3) {
            step3depsCache = step3deps.selectedDependencies();
            showStep(2);
            return;
        }
        showStep(Math.max(1, currentStep - 1));
    }

    @FXML private void onNext() {
        switch (currentStep) {
            case 1 -> {
                if (!step1.isValid()) return;
                step2.init(terragruntRoot, step1.currentMode(),
                    step1.selectedTemplate().orElseThrow(),
                    /* prefillParent */ null);
                showStep(2);
            }
            case 2 -> {
                if (!step2.isValid()) return;
                if (skipStep3()) {
                    initStep4();
                    showStep(4);
                } else {
                    Path target = step2.selectedParentDir().orElseThrow().resolve(step2.folderName());
                    step3deps.init(graph, target.getParent() == null ? target : target.getParent(), step3depsCache);
                    showStep(3);
                }
            }
            case 3 -> {
                if (!step3deps.isValid()) return;
                step3depsCache = step3deps.selectedDependencies();
                initStep4();
                showStep(4);
            }
            case 4 -> {
                if (!step4.isValid()) return;
                CreateRequest req = new CreateRequest(
                    step1.currentMode(),
                    step1.selectedTemplate().orElseThrow(),
                    step2.selectedParentDir().orElseThrow(),
                    step2.folderName(),
                    step3depsCache,
                    step4.values());
                onConfirm.accept(req);
                stage.close();
            }
        }
    }

    private void initStep4() {
        step4.init(
            step1.selectedTemplate().orElseThrow(),
            step1.currentMode(),
            graph,
            step2.suggestion(),
            step2.folderName(),
            new java.util.ArrayList<>(step3depsCache.keySet()),
            step4Cache);
    }

    private boolean skipStep3() {
        return step1.currentMode() instanceof WizardMode.IncludeFromModule;
    }

    public void closeAfterCommit() { stage.close(); }
    public void returnToStep1WithMessage(String msg) { statusLabel.setText(msg); showStep(1); }
    public void returnToStep2WithMessage(String msg) { statusLabel.setText(msg); showStep(2); }

    private void showStep(int n) {
        currentStep = n;
        body.getChildren().setAll(switch (n) {
            case 1 -> step1Root;
            case 2 -> step2Root;
            case 3 -> step3depsRoot;
            default -> step4Root;
        });
        backBtn.setDisable(n == 1);
        nextBtn.setText(n == 4 ? "Preview & Create" : "Next");
        setHeaderActive(n);
        updateButtons();
    }

    private void setHeaderActive(int n) {
        step1Header.setStyle(n == 1 ? "-fx-font-weight: bold" : "");
        step2Header.setStyle(n == 2 ? "-fx-font-weight: bold" : "");
        boolean skip = skipStep3();
        step3Header.setStyle(skip
            ? "-fx-text-fill: #aaa"
            : (n == 3 ? "-fx-font-weight: bold" : ""));
        step4Header.setStyle(n == 4 ? "-fx-font-weight: bold" : "");
    }

    private void updateButtons() {
        boolean valid = switch (currentStep) {
            case 1 -> step1 != null && step1.isValid();
            case 2 -> step2 != null && step2.isValid();
            case 3 -> step3deps != null && step3deps.isValid();
            default -> step4 != null && step4.isValid();
        };
        nextBtn.setDisable(!valid);
        statusLabel.setText(valid ? "" : hintForInvalid());
    }

    private String hintForInvalid() {
        return switch (currentStep) {
            case 1 -> "Choose a template.";
            case 2 -> "Complete the location fields.";
            case 3 -> "Resolve dependency-name issues to continue.";
            default -> "Fill required inputs.";
        };
    }
}

package solutions.onz.toolbox.gruntface.ui.create;

import javafx.stage.Window;
import solutions.onz.toolbox.gruntface.create.CreatePlan;
import solutions.onz.toolbox.gruntface.ui.DiffDialog;

public final class CreatePreviewDialog {
    private CreatePreviewDialog() {}

    /** Shows a modal diff dialog for the planned write. Returns true if the user confirms. */
    public static boolean show(Window owner, CreatePlan plan) {
        return DiffDialog.show(
            owner,
            "Create — " + plan.targetFile().getFileName(),
            "",                       // empty "before"
            plan.contentToWrite());
    }
}

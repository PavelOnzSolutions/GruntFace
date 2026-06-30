package solutions.onz.toolbox.gruntface.create;

import solutions.onz.toolbox.gruntface.model.InputValue;

import java.nio.file.Path;
import java.util.LinkedHashMap;

public record CreateRequest(
    WizardMode mode,
    ResourceTemplate template,
    Path parentDir,
    String folderName,
    LinkedHashMap<String, DependencyDecl> dependencies,
    LinkedHashMap<String, InputValue> inputs
) {
    public CreateRequest {
        dependencies = new LinkedHashMap<>(dependencies);
        inputs = new LinkedHashMap<>(inputs);
    }

    @Override
    public LinkedHashMap<String, DependencyDecl> dependencies() {
        return new LinkedHashMap<>(dependencies);
    }

    @Override
    public LinkedHashMap<String, InputValue> inputs() {
        return new LinkedHashMap<>(inputs);
    }
}

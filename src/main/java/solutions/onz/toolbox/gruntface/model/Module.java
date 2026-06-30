package solutions.onz.toolbox.gruntface.model;

import java.nio.file.Path;
import java.util.List;

public record Module(Path dir, String name, List<Variable> variables) {
    public Module {
        variables = List.copyOf(variables);
    }
}

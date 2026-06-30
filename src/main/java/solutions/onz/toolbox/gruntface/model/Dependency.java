package solutions.onz.toolbox.gruntface.model;

import java.nio.file.Path;
import java.util.Optional;

public record Dependency(
    String name,
    String configPath,
    Optional<Path> resolvedUnitPath
) {}

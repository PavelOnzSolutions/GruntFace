package solutions.onz.toolbox.gruntface.create;

import java.nio.file.Path;
import java.util.Optional;

public record CreatePlan(
    Path targetFile,
    String contentToWrite,
    Optional<Path> conflictsWith     // present iff targetFile already exists
) {}

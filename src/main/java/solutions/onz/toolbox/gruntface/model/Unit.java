package solutions.onz.toolbox.gruntface.model;

import solutions.onz.toolbox.gruntface.name.ResolvedName;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record Unit(
    Path file,
    String name,
    Optional<String> sourceRef,
    Optional<Path> sourceLocalPath,
    Map<String, InputValue> inputs,
    Optional<ByteRange> inputsRange,
    List<Dependency> dependencies,
    List<IncludeBlock> includes,
    List<ParseIssue> issues,
    String originalText,
    Optional<ResolvedName> resolvedName
) {
    public Unit {
        inputs = new LinkedHashMap<>(inputs);
        dependencies = List.copyOf(dependencies);
        includes = List.copyOf(includes);
        issues = List.copyOf(issues);
    }

    /** Convenience constructor for the common case where no name has been resolved yet. */
    public Unit(
        Path file, String name,
        Optional<String> sourceRef, Optional<Path> sourceLocalPath,
        Map<String, InputValue> inputs, Optional<ByteRange> inputsRange,
        List<Dependency> dependencies, List<IncludeBlock> includes,
        List<ParseIssue> issues, String originalText
    ) {
        this(file, name, sourceRef, sourceLocalPath, inputs, inputsRange,
            dependencies, includes, issues, originalText, Optional.empty());
    }
}

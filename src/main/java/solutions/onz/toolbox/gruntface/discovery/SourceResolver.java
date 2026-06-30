package solutions.onz.toolbox.gruntface.discovery;

import solutions.onz.toolbox.gruntface.model.ExternalModule;
import solutions.onz.toolbox.gruntface.model.ParseIssue;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class SourceResolver {

    public record Result(
        Optional<Path> localModule,
        Optional<ExternalModule> external,
        Optional<ParseIssue> issue
    ) {}

    private SourceResolver() {}

    public static Result resolve(Optional<String> sourceRef, Path unitFile, List<Path> moduleDirs) {
        if (sourceRef.isEmpty()) return new Result(Optional.empty(), Optional.empty(), Optional.empty());
        return resolve(sourceRef.get(), unitFile, moduleDirs);
    }

    public static Result resolve(String sourceRef, Path unitFile, List<Path> moduleDirs) {
        if (sourceRef == null || sourceRef.isBlank()) {
            return new Result(Optional.empty(), Optional.empty(), Optional.empty());
        }

        if (isExternal(sourceRef)) {
            return new Result(Optional.empty(), Optional.of(new ExternalModule(sourceRef)), Optional.empty());
        }

        Path unitDir = unitFile.getParent();
        Path candidate;
        try {
            Path sourcePath = Path.of(sourceRef);
            if (sourceRef.startsWith("/") || (sourceRef.length() > 1 && sourceRef.charAt(1) == ':')) {
                candidate = sourcePath.normalize();
            } else {
                candidate = unitDir == null
                    ? sourcePath.normalize()
                    : unitDir.resolve(sourceRef).normalize();
            }
        } catch (InvalidPathException e) {
            // sourceRef contains characters illegal on this filesystem (e.g. an unresolved
            // ${include.X.locals.Y} interpolation, or a URL fragment with '?' on Windows).
            // Surface as a ParseIssue rather than crashing discovery.
            return new Result(
                Optional.empty(),
                Optional.empty(),
                Optional.of(new ParseIssue(
                    ParseIssue.Severity.ERROR,
                    "Source is not a recognised local path or external URL: " + sourceRef
                ))
            );
        }

        for (Path m : moduleDirs) {
            if (m.toAbsolutePath().normalize().equals(candidate.toAbsolutePath().normalize())) {
                return new Result(Optional.of(m), Optional.empty(), Optional.empty());
            }
        }

        return new Result(
            Optional.empty(),
            Optional.empty(),
            Optional.of(new ParseIssue(
                ParseIssue.Severity.ERROR,
                "Source module not found at " + candidate
            ))
        );
    }

    private static boolean isExternal(String s) {
        if (s.startsWith("git::") || s.startsWith("git@")) return true;
        if (s.startsWith("github.com/")) return true;
        if (s.startsWith("tfr://") || s.startsWith("registry.terraform.io/")) return true;
        if (s.startsWith("http://") || s.startsWith("https://")) return true;
        if (s.startsWith("s3::") || s.startsWith("gcs::")) return true;
        return false;
    }
}

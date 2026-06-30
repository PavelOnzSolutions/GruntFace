package solutions.onz.toolbox.gruntface.discovery;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SourceResolverTest {

    @Test
    void resolvesRelativeLocalSourceToDiscoveredModule() {
        Path unit = Path.of("/root/terragrunt/dev/vpc/terragrunt.hcl");
        List<Path> moduleDirs = List.of(Path.of("/root/modules/vpc"));

        var result = SourceResolver.resolve("../../../modules/vpc", unit, moduleDirs);

        assertTrue(result.localModule().isPresent());
        assertEquals(Path.of("/root/modules/vpc").toAbsolutePath(),
                     result.localModule().orElseThrow().toAbsolutePath());
        assertTrue(result.external().isEmpty());
    }

    @Test
    void unresolvableRelativeSourceReturnsNoLink() {
        Path unit = Path.of("/root/terragrunt/dev/vpc/terragrunt.hcl");
        var result = SourceResolver.resolve("../../../modules/missing", unit, List.of());
        assertTrue(result.localModule().isEmpty());
        assertTrue(result.external().isEmpty());
        assertTrue(result.issue().isPresent());
    }

    @Test
    void gitSourceIsExternal() {
        Path unit = Path.of("/root/terragrunt/dev/aks/terragrunt.hcl");
        var result = SourceResolver.resolve("git::https://example.com/mod.git//path?ref=v1", unit, List.of());
        assertTrue(result.external().isPresent());
        assertTrue(result.localModule().isEmpty());
    }

    @Test
    void registrySourceIsExternal() {
        Path unit = Path.of("/root/terragrunt/dev/aks/terragrunt.hcl");
        var result = SourceResolver.resolve("registry.terraform.io/foo/bar/aws", unit, List.of());
        assertTrue(result.external().isPresent());
    }

    @Test
    void sourceRefWithIllegalPathCharsDoesNotThrow() {
        // Defensive: an unresolved interpolation that slips past the upstream resolver
        // (e.g. "include.common.locals.base_source_url?ref=<commit>") contains '?',
        // which is illegal on Windows paths. Discovery must surface a ParseIssue
        // rather than crash with InvalidPathException.
        Path unit = Path.of("/root/terragrunt/dev/aks/terragrunt.hcl");
        var result = SourceResolver.resolve(
            "include.common.locals.base_source_url?ref=a71c0c9396cd32eac7fa66238ce3ecd8bb855219",
            unit, List.of());
        assertTrue(result.localModule().isEmpty());
        assertTrue(result.external().isEmpty());
        assertTrue(result.issue().isPresent());
    }

    @Test
    void emptySourceReturnsNothing() {
        var result = SourceResolver.resolve(Optional.empty(), Path.of("/x/y/terragrunt.hcl"), List.of());
        assertTrue(result.localModule().isEmpty());
        assertTrue(result.external().isEmpty());
    }
}

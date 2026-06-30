package solutions.onz.toolbox.gruntface.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import solutions.onz.toolbox.gruntface.hcl.Hcl4jHclService;
import solutions.onz.toolbox.gruntface.model.InfraGraph;
import solutions.onz.toolbox.gruntface.model.Unit;
import solutions.onz.toolbox.gruntface.name.ResolvedName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveryServiceHierarchyTest {

    @Test
    void handWrittenUnit_resolvesNameFromHierarchyAndUnitLocals(@TempDir Path tmp) throws IOException {
        copyTree(Path.of("src/test/resources/fixtures/hierarchy-project"), tmp);
        InfraGraph graph = new DiscoveryService(new Hcl4jHclService()).load(tmp);

        Unit kv = graph.units().stream()
            .filter(u -> u.file().toString().endsWith("key-vault" + java.io.File.separator + "terragrunt.hcl"))
            .findFirst().orElseThrow();

        ResolvedName r = kv.resolvedName().orElseThrow();
        assertEquals(ResolvedName.Confidence.EVALUATED, r.confidence(),
            "expected a synthesised name, not the FALLBACK fallback");
        assertEquals("kv-wpa-secrets-gwc-prod", r.text());
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path src : s.toList()) {
                Path dst = to.resolve(from.relativize(src).toString());
                if (Files.isDirectory(src)) Files.createDirectories(dst);
                else { Files.createDirectories(dst.getParent()); Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING); }
            }
        }
    }
}

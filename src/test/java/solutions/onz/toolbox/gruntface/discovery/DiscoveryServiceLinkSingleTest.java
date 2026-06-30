package solutions.onz.toolbox.gruntface.discovery;

import org.junit.jupiter.api.Test;
import solutions.onz.toolbox.gruntface.hcl.Hcl4jHclService;
import solutions.onz.toolbox.gruntface.model.InfraGraph;
import solutions.onz.toolbox.gruntface.model.Unit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveryServiceLinkSingleTest {

    @Test
    void linkSingleUnit_resolvesIncludeAndSource(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        // Copy the include-project fixture to a tmp dir.
        Path src = Path.of("src/test/resources/fixtures/include-project");
        copyTree(src, tmp);
        DiscoveryService disc = new DiscoveryService(new Hcl4jHclService());
        InfraGraph baseGraph = disc.load(tmp);

        // Pick an existing unit and ask the service to relink it solo.
        Unit existing = baseGraph.units().stream()
            .filter(u -> u.file().toString().endsWith("key-vault-dbx" + java.io.File.separator + "terragrunt.hcl"))
            .findFirst().orElseThrow();
        Unit linked = disc.linkSingleUnit(existing.file(), baseGraph);

        // The freshly-linked unit must match the one produced by a full walk.
        assertEquals(existing.sourceLocalPath(), linked.sourceLocalPath());
        assertEquals(existing.includes().size(), linked.includes().size());
        assertEquals(existing.resolvedName().map(r -> r.text()),
                     linked.resolvedName().map(r -> r.text()));
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

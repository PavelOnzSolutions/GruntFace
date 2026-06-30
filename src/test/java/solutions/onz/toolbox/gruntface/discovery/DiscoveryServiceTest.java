package solutions.onz.toolbox.gruntface.discovery;

import solutions.onz.toolbox.gruntface.hcl.Hcl4jHclService;
import solutions.onz.toolbox.gruntface.model.ExternalModule;
import solutions.onz.toolbox.gruntface.model.InfraGraph;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveryServiceTest {

    private final DiscoveryService service = new DiscoveryService(new Hcl4jHclService());

    @Test
    void discoversSampleProject() throws Exception {
        Path tgRoot = Path.of("src/test/resources/fixtures/sample-project/terragrunt");
        Path modRoot = Path.of("src/test/resources/fixtures/sample-project/modules");

        InfraGraph graph = service.load(tgRoot, modRoot);

        assertEquals(3, graph.units().size());
        assertEquals(3, graph.modules().size());
        assertEquals(1, graph.externals().size(), "aks unit uses an external git source");
        assertEquals(3, graph.usesEdges().size());
        assertEquals(2, graph.dependsEdges().size());
        assertTrue(graph.cycles().isEmpty());
    }

    @Test
    void unitsAreOrderedAndNamedByDirectory() throws Exception {
        Path tgRoot = Path.of("src/test/resources/fixtures/sample-project/terragrunt");
        Path modRoot = Path.of("src/test/resources/fixtures/sample-project/modules");

        InfraGraph graph = service.load(tgRoot, modRoot);

        var names = graph.units().stream().map(u -> u.name()).sorted().toList();
        assertEquals(java.util.List.of("aks", "subnets", "vpc"), names);
    }

    @Test
    void discoversCommonsAndLinksIncludes() throws Exception {
        Path root = Path.of("src/test/resources/fixtures/include-project");
        InfraGraph graph = service.load(root);

        assertEquals(1, graph.units().size(), "one unit under applications/");
        assertEquals(1, graph.commons().size(), "one common under _common/");
        assertEquals("key-vault", graph.commons().get(0).name());

        assertEquals(1, graph.includesEdges().size(),
            "the unit's 'common' include should map to the key-vault common");
        assertEquals(graph.units().get(0).file(), graph.includesEdges().get(0).from().file());
        assertEquals(graph.commons().get(0).file(), graph.includesEdges().get(0).to().file());
    }

    @Test
    void resolvesInterpolatedSourceToExternalGitUrl() throws Exception {
        Path root = Path.of("src/test/resources/fixtures/include-project");
        InfraGraph graph = service.load(root);

        // The aks unit's terraform.source = ${include.common.locals.base_source_url}
        // -> common's local base_source_url is a git@ URL -> should be an external module.
        assertEquals(1, graph.externals().size(), "expected one external module");
        assertTrue(graph.externals().get(0).sourceRef().startsWith("git@"),
            "expected git@ URL, got: " + graph.externals().get(0).sourceRef());

        // The uses edge from the unit should point to that external
        assertEquals(1, graph.usesEdges().size());
        InfraGraph.UsesEdge edge = graph.usesEdges().get(0);
        assertTrue(edge.to() instanceof ExternalModule,
            "edge target should be ExternalModule");
    }

    @Test
    void resolvesInterpolatedSourceWithRefSuffixToExternalGitUrl() throws Exception {
        // Regression: source = "${include.common.locals.base_source_url}?ref=<commit>"
        // Hcl4j renders this as the literal "include.common.locals.base_source_url?ref=<commit>".
        // The interpolation resolver must recognise the unresolved interpolation prefix and
        // append the literal "?ref=<commit>" suffix to the looked-up value.
        Path root = Path.of("src/test/resources/fixtures/include-project-ref");
        InfraGraph graph = service.load(root);

        assertEquals(1, graph.externals().size(), "expected one external module");
        String url = graph.externals().get(0).sourceRef();
        assertTrue(url.startsWith("git@"), "expected git@ URL, got: " + url);
        assertTrue(url.endsWith("?ref=a71c0c9396cd32eac7fa66238ce3ecd8bb855219"),
            "expected ?ref=<commit> suffix to be preserved, got: " + url);
    }

    @Test
    void singleRootAutodetectsCommonsDirectory() throws Exception {
        Path root = Path.of("src/test/resources/fixtures/include-project");
        InfraGraph graph = service.load(root);
        assertEquals(1, graph.commons().size(),
            "_common/ should be auto-discovered under the Terragrunt root");
    }
}

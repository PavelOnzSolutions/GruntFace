package solutions.onz.toolbox.gruntface.ui.graph.layout;

import org.junit.jupiter.api.Test;
import solutions.onz.toolbox.gruntface.model.*;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class HierarchyBuilderTest {

    private static Unit unit(String relPath) {
        Path root = Path.of("C:/proj/terragrunt-azure");
        return new Unit(
            root.resolve(relPath).resolve("terragrunt.hcl"),
            Path.of(relPath).getFileName().toString(),
            Optional.empty(), Optional.empty(),
            Map.of(), Optional.empty(),
            List.of(), List.of(), List.of(), ""
        );
    }

    @Test
    void classifies_region_and_env_from_path_segments() {
        Path root = Path.of("C:/proj/terragrunt-azure");
        Unit u = unit("platform/management/gwc/prod/key-vault");
        HierarchyBuilder.Result r = HierarchyBuilder.classify(u, root);
        assertEquals("gwc", r.region().orElseThrow());
        assertEquals("prod", r.env().orElseThrow());
    }

    @Test
    void uncategorised_when_pattern_does_not_match() {
        Path root = Path.of("C:/proj/terragrunt-azure");
        Unit u = unit("_common");                       // sits directly under root
        HierarchyBuilder.Result r = HierarchyBuilder.classify(u, root);
        assertTrue(r.region().isEmpty());
        assertTrue(r.env().isEmpty());
    }

    @Test
    void classifies_when_root_is_opened_at_a_lower_depth() {
        // Root opened at applications/<appName>/ — unit lives at <region>/<env>/<resource>/terragrunt.hcl
        // which is only 3 segments below the root. classify() must still work.
        Path root = Path.of("C:/proj/terragrunt-azure/applications/billing");
        Unit u = new Unit(
            root.resolve("gwc/prod/key-vault/terragrunt.hcl"),
            "key-vault",
            Optional.empty(), Optional.empty(),
            Map.of(), Optional.empty(),
            List.of(), List.of(), List.of(), ""
        );
        HierarchyBuilder.Result r = HierarchyBuilder.classify(u, root);
        assertEquals("gwc", r.region().orElseThrow());
        assertEquals("prod", r.env().orElseThrow());
    }

    @Test
    void groups_units_into_container_ids() {
        Path root = Path.of("C:/proj/terragrunt-azure");
        Unit a = unit("platform/management/gwc/prod/key-vault");
        Unit b = unit("platform/management/gwc/prod/vm-jumphost");
        Unit c = unit("platform/management/weu/prod/log-analytics-sentinel");
        Unit d = unit("_common");

        HierarchyBuilder.Tree t = HierarchyBuilder.build(List.of(a, b, c, d), root);

        assertEquals(2, t.regionIds().size());            // gwc, weu
        assertTrue(t.regionIds().containsAll(List.of("region:gwc", "region:weu")));
        assertEquals(Set.of("env:gwc/prod", "env:weu/prod"), new HashSet<>(t.envIds()));
        assertEquals("env:gwc/prod", t.containerOf(a).orElseThrow());
        assertEquals("env:gwc/prod", t.containerOf(b).orElseThrow());
        assertEquals("env:weu/prod", t.containerOf(c).orElseThrow());
        assertEquals("uncategorised", t.containerOf(d).orElseThrow());
    }
}

package solutions.onz.toolbox.gruntface.ui.graph.layout;

import org.junit.jupiter.api.Test;
import solutions.onz.toolbox.gruntface.model.*;
import solutions.onz.toolbox.gruntface.model.Module;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ElkLayoutServiceTest {

    private static Unit unit(Path root, String relDir, String name) {
        return new Unit(
            root.resolve(relDir).resolve("terragrunt.hcl"),
            name,
            Optional.empty(), Optional.empty(),
            Map.of(), Optional.empty(),
            List.of(), List.of(), List.of(), ""
        );
    }

    @Test
    void produces_one_node_per_unit_with_non_zero_geometry() {
        Path root = Path.of("C:/proj");
        Unit a = unit(root, "platform/management/gwc/prod/key-vault", "key-vault");
        Unit b = unit(root, "platform/management/gwc/prod/vm-jumphost", "vm-jumphost");
        InfraGraph g = new InfraGraph(
            List.of(a, b), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        DiagramLayout layout = new ElkLayoutService().layout(g, root, true, true, true, true);

        assertEquals(2, layout.nodes().size());
        for (DiagramLayout.NodeBox n : layout.nodes()) {
            assertTrue(n.width() > 0, "width > 0");
            assertTrue(n.height() > 0, "height > 0");
        }
        // Two units, different ids, distinct positions.
        Set<String> ids = new HashSet<>();
        for (DiagramLayout.NodeBox n : layout.nodes()) ids.add(n.id());
        assertEquals(2, ids.size());
    }

    @Test
    void produces_region_and_env_containers_with_parent_chain() {
        Path root = Path.of("C:/proj");
        Unit a = unit(root, "platform/management/gwc/prod/key-vault", "key-vault");
        Unit b = unit(root, "platform/management/gwc/preprod/key-vault", "key-vault");
        Unit c = unit(root, "platform/management/weu/prod/key-vault", "key-vault");
        InfraGraph g = new InfraGraph(
            List.of(a, b, c), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        DiagramLayout layout = new ElkLayoutService().layout(g, root, true, true, true, true);

        // 2 regions (gwc, weu) + 3 envs (gwc/prod, gwc/preprod, weu/prod) = 5 containers.
        assertEquals(5, layout.containers().size());
        long regions = layout.containers().stream()
            .filter(c0 -> c0.kind() == DiagramLayout.ContainerKind.REGION).count();
        long envs = layout.containers().stream()
            .filter(c0 -> c0.kind() == DiagramLayout.ContainerKind.ENV).count();
        assertEquals(2, regions);
        assertEquals(3, envs);

        // Every env container has a region parent; every unit node has an env parent.
        Map<String, DiagramLayout.ContainerBox> byId = new HashMap<>();
        for (DiagramLayout.ContainerBox cb : layout.containers()) byId.put(cb.id(), cb);
        for (DiagramLayout.ContainerBox env : layout.containers()) {
            if (env.kind() != DiagramLayout.ContainerKind.ENV) continue;
            assertNotNull(env.parentId(), "env parentId");
            assertEquals(DiagramLayout.ContainerKind.REGION, byId.get(env.parentId()).kind());
        }
        for (DiagramLayout.NodeBox n : layout.nodes()) {
            assertNotNull(n.parentId(), "unit parentId (env id)");
            assertEquals(DiagramLayout.ContainerKind.ENV, byId.get(n.parentId()).kind());
        }
    }

    @Test
    void depends_on_edge_has_bend_points_with_source_and_target_endpoints() {
        Path root = Path.of("C:/proj");
        Unit kv = unit(root, "platform/management/gwc/prod/key-vault", "key-vault");
        Unit vm = unit(root, "platform/management/gwc/prod/vm-jumphost", "vm-jumphost");
        InfraGraph g = new InfraGraph(
            List.of(kv, vm), List.of(), List.of(), List.of(),
            List.of(), List.of(new InfraGraph.DependsOnEdge(vm, kv)), List.of(), List.of()
        );

        DiagramLayout layout = new ElkLayoutService().layout(g, root, true, true, true, true);

        assertEquals(1, layout.edges().size());
        DiagramLayout.EdgeShape e = layout.edges().get(0);
        assertEquals(DiagramLayout.EdgeKind.DEPENDS_ON, e.kind());
        assertTrue(e.bendPoints().size() >= 2, "bend points include source + target endpoints");
        // Endpoints are inside the source and target node bounding boxes (with tolerance).
        DiagramLayout.NodeBox src = layout.nodes().stream()
            .filter(n -> n.id().equals(e.sourceNodeId())).findFirst().orElseThrow();
        DiagramLayout.NodeBox tgt = layout.nodes().stream()
            .filter(n -> n.id().equals(e.targetNodeId())).findFirst().orElseThrow();
        double[] first = e.bendPoints().get(0);
        double[] last = e.bendPoints().get(e.bendPoints().size() - 1);
        assertWithin(first[0], src.x() - 1, src.x() + src.width() + 1);
        assertWithin(first[1], src.y() - 1, src.y() + src.height() + 1);
        assertWithin(last[0], tgt.x() - 1, tgt.x() + tgt.width() + 1);
        assertWithin(last[1], tgt.y() - 1, tgt.y() + tgt.height() + 1);
    }

    @Test
    void excludes_module_nodes_when_show_modules_is_false() {
        Path root = Path.of("C:/proj");
        Unit u = unit(root, "platform/management/gwc/prod/key-vault", "key-vault");
        Module m = new Module(Path.of("C:/proj/modules/key-vault"), "key-vault", List.of());
        InfraGraph g = new InfraGraph(
            List.of(u), List.of(m), List.of(), List.of(),
            List.of(new InfraGraph.UsesEdge(u, m)), List.of(), List.of(), List.of()
        );

        DiagramLayout shown = new ElkLayoutService().layout(g, root, true, true, true, true);
        DiagramLayout hidden = new ElkLayoutService().layout(g, root, false, true, true, true);

        long shownModules = shown.nodes().stream()
            .filter(n -> n.kind() == DiagramLayout.NodeKind.MODULE).count();
        long hiddenModules = hidden.nodes().stream()
            .filter(n -> n.kind() == DiagramLayout.NodeKind.MODULE).count();
        assertEquals(1, shownModules);
        assertEquals(0, hiddenModules);
        assertEquals(0, hidden.edges().stream()
            .filter(e -> e.kind() == DiagramLayout.EdgeKind.USES).count());
    }

    private static void assertWithin(double v, double lo, double hi) {
        assertTrue(v >= lo && v <= hi, "expected " + v + " in [" + lo + ", " + hi + "]");
    }
}

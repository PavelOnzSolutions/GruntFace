package solutions.onz.toolbox.gruntface.create;

import org.junit.jupiter.api.Test;
import solutions.onz.toolbox.gruntface.model.*;
import solutions.onz.toolbox.gruntface.model.Module;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class InfraGraphPatcherReplaceUnitTest {

    private static Unit unit(Path file, Optional<String> sourceRef, Optional<Path> sourceLocal,
                             List<Dependency> deps, List<IncludeBlock> includes) {
        return new Unit(
            file, file.getParent().getFileName().toString(),
            sourceRef, sourceLocal,
            new LinkedHashMap<>(), Optional.empty(),
            deps, includes, List.of(), "");
    }

    @Test
    void replaceUnit_swapsUnitInList() {
        Unit oldU = unit(Path.of("/r/a/x/terragrunt.hcl"),
            Optional.empty(), Optional.empty(), List.of(), List.of());
        Unit newU = unit(Path.of("/r/a/x/terragrunt.hcl"),
            Optional.empty(), Optional.empty(), List.of(), List.of());

        InfraGraph base = new InfraGraph(
            List.of(oldU), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of());

        InfraGraph after = InfraGraphPatcher.replaceUnit(base, oldU, newU);

        assertEquals(1, after.units().size());
        assertSame(newU, after.units().get(0));
    }

    @Test
    void replaceUnit_addsNewOutgoingDependsEdges() {
        Unit dep1 = unit(Path.of("/r/a/d1/terragrunt.hcl"),
            Optional.empty(), Optional.empty(), List.of(), List.of());
        Unit dep2 = unit(Path.of("/r/a/d2/terragrunt.hcl"),
            Optional.empty(), Optional.empty(), List.of(), List.of());
        Unit oldU = unit(Path.of("/r/a/x/terragrunt.hcl"),
            Optional.empty(), Optional.empty(), List.of(), List.of());
        Unit newU = unit(Path.of("/r/a/x/terragrunt.hcl"),
            Optional.empty(), Optional.empty(),
            List.of(new Dependency("d1", "../d1", Optional.of(dep1.file())),
                    new Dependency("d2", "../d2", Optional.of(dep2.file()))),
            List.of());

        InfraGraph base = new InfraGraph(
            List.of(oldU, dep1, dep2), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of());

        InfraGraph after = InfraGraphPatcher.replaceUnit(base, oldU, newU);

        assertEquals(2, after.dependsEdges().size());
        for (InfraGraph.DependsOnEdge e : after.dependsEdges()) {
            assertSame(newU, e.from(), "edge.from must be the new unit instance");
        }
    }

    @Test
    void replaceUnit_dropsRemovedOutgoingDependsEdges() {
        Unit dep1 = unit(Path.of("/r/a/d1/terragrunt.hcl"),
            Optional.empty(), Optional.empty(), List.of(), List.of());
        Unit dep2 = unit(Path.of("/r/a/d2/terragrunt.hcl"),
            Optional.empty(), Optional.empty(), List.of(), List.of());
        Unit oldU = unit(Path.of("/r/a/x/terragrunt.hcl"),
            Optional.empty(), Optional.empty(),
            List.of(new Dependency("d1", "../d1", Optional.of(dep1.file())),
                    new Dependency("d2", "../d2", Optional.of(dep2.file()))),
            List.of());
        Unit newU = unit(Path.of("/r/a/x/terragrunt.hcl"),
            Optional.empty(), Optional.empty(),
            List.of(new Dependency("d1", "../d1", Optional.of(dep1.file()))),
            List.of());

        InfraGraph base = new InfraGraph(
            List.of(oldU, dep1, dep2), List.of(), List.of(), List.of(),
            List.of(),
            List.of(new InfraGraph.DependsOnEdge(oldU, dep1),
                    new InfraGraph.DependsOnEdge(oldU, dep2)),
            List.of(), List.of());

        InfraGraph after = InfraGraphPatcher.replaceUnit(base, oldU, newU);

        assertEquals(1, after.dependsEdges().size());
        assertEquals(dep1.file(), after.dependsEdges().get(0).to().file());
    }

    @Test
    void replaceUnit_rebindsIncomingDependsEdgesToNewInstance() {
        Unit oldU = unit(Path.of("/r/a/x/terragrunt.hcl"),
            Optional.empty(), Optional.empty(), List.of(), List.of());
        Unit newU = unit(Path.of("/r/a/x/terragrunt.hcl"),
            Optional.empty(), Optional.empty(), List.of(), List.of());
        Unit dependant = unit(Path.of("/r/a/y/terragrunt.hcl"),
            Optional.empty(), Optional.empty(),
            List.of(new Dependency("x", "../x", Optional.of(oldU.file()))),
            List.of());

        InfraGraph base = new InfraGraph(
            List.of(oldU, dependant), List.of(), List.of(), List.of(),
            List.of(),
            List.of(new InfraGraph.DependsOnEdge(dependant, oldU)),
            List.of(), List.of());

        InfraGraph after = InfraGraphPatcher.replaceUnit(base, oldU, newU);

        assertEquals(1, after.dependsEdges().size());
        assertSame(dependant, after.dependsEdges().get(0).from());
        assertSame(newU, after.dependsEdges().get(0).to(),
            "incoming dep edge must rebind to the new unit instance");
    }

    @Test
    void replaceUnit_switchesUsesEdgeWhenSourceChanges() {
        Module m = new Module(Path.of("/r/modules/vm"), "vm", List.of());
        ExternalModule ext = new ExternalModule("git::https://example/mod.git?ref=v1");
        Unit oldU = unit(Path.of("/r/a/x/terragrunt.hcl"),
            Optional.empty(), Optional.of(m.dir()), List.of(), List.of());
        Unit newU = unit(Path.of("/r/a/x/terragrunt.hcl"),
            Optional.of(ext.sourceRef()), Optional.empty(), List.of(), List.of());

        InfraGraph base = new InfraGraph(
            List.of(oldU), List.of(m), List.of(ext), List.of(),
            List.of(new InfraGraph.UsesEdge(oldU, m)),
            List.of(), List.of(), List.of());

        InfraGraph after = InfraGraphPatcher.replaceUnit(base, oldU, newU);

        assertEquals(1, after.usesEdges().size());
        assertSame(newU, after.usesEdges().get(0).from());
        assertSame(ext, after.usesEdges().get(0).to(),
            "uses edge must point at the external module after source switch");
    }

    @Test
    void replaceUnit_recomputesCyclesWhenDepsChange() {
        Unit a = unit(Path.of("/r/a/terragrunt.hcl"),
            Optional.empty(), Optional.empty(), List.of(), List.of());
        Unit bOld = unit(Path.of("/r/b/terragrunt.hcl"),
            Optional.empty(), Optional.empty(),
            List.of(new Dependency("a", "../a", Optional.of(a.file()))),
            List.of());
        Unit bNew = unit(Path.of("/r/b/terragrunt.hcl"),
            Optional.empty(), Optional.empty(), List.of(), List.of());

        InfraGraph base = new InfraGraph(
            List.of(a, bOld), List.of(), List.of(), List.of(),
            List.of(),
            List.of(new InfraGraph.DependsOnEdge(a, bOld),
                    new InfraGraph.DependsOnEdge(bOld, a)),
            List.of(),
            List.of(List.of(a, bOld)));

        InfraGraph after = InfraGraphPatcher.replaceUnit(base, bOld, bNew);

        assertEquals(1, after.dependsEdges().size());
        assertEquals(0, after.cycles().size());
    }
}

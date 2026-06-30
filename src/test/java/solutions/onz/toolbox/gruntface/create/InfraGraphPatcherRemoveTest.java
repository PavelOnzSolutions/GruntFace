package solutions.onz.toolbox.gruntface.create;

import org.junit.jupiter.api.Test;
import solutions.onz.toolbox.gruntface.model.*;
import solutions.onz.toolbox.gruntface.model.Module;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class InfraGraphPatcherRemoveTest {

    private static Unit unit(Path file, List<Dependency> deps, List<IncludeBlock> includes,
                             Optional<Path> sourceLocal) {
        return new Unit(
            file, file.getParent().getFileName().toString(),
            Optional.empty(), sourceLocal,
            new LinkedHashMap<>(), Optional.empty(),
            deps, includes, List.of(), "");
    }

    @Test
    void removeUnit_dropsOutgoingUsesIncludesDependsEdges() {
        Module m = new Module(Path.of("/r/modules/vm"), "vm", List.of());
        CommonHcl c = new CommonHcl(Path.of("/r/_common/vm.hcl"), "vm", Optional.empty(), Map.of());
        Unit other = unit(Path.of("/r/a/o/terragrunt.hcl"), List.of(), List.of(), Optional.empty());
        Unit u = unit(Path.of("/r/a/x/terragrunt.hcl"),
            List.of(new Dependency("o", "../o", Optional.of(other.file()))),
            List.of(new IncludeBlock("common", "...", Optional.of(c.file()))),
            Optional.of(m.dir()));

        InfraGraph base = new InfraGraph(
            List.of(other, u), List.of(m), List.of(), List.of(c),
            List.of(new InfraGraph.UsesEdge(u, m)),
            List.of(new InfraGraph.DependsOnEdge(u, other)),
            List.of(new InfraGraph.IncludesEdge(u, c)),
            List.of());

        InfraGraph after = InfraGraphPatcher.removeUnit(base, u);

        assertEquals(1, after.units().size());
        assertEquals(other.file(), after.units().get(0).file());
        assertEquals(0, after.usesEdges().size());
        assertEquals(0, after.includesEdges().size());
        assertEquals(0, after.dependsEdges().size());
    }

    @Test
    void removeUnit_dropsIncomingDependsEdges() {
        Unit target = unit(Path.of("/r/a/t/terragrunt.hcl"), List.of(), List.of(), Optional.empty());
        Unit other  = unit(Path.of("/r/a/o/terragrunt.hcl"),
            List.of(new Dependency("t", "../t", Optional.of(target.file()))),
            List.of(), Optional.empty());

        InfraGraph base = new InfraGraph(
            List.of(target, other), List.of(), List.of(), List.of(),
            List.of(), List.of(new InfraGraph.DependsOnEdge(other, target)),
            List.of(), List.of());

        InfraGraph after = InfraGraphPatcher.removeUnit(base, target);

        assertEquals(1, after.units().size());
        assertEquals(other.file(), after.units().get(0).file());
        assertEquals(0, after.dependsEdges().size(),
            "incoming dep edge to the removed unit must be dropped");
    }

    @Test
    void removeUnit_recomputesCyclesAfterRemoval() {
        Unit a = unit(Path.of("/r/a/terragrunt.hcl"), List.of(), List.of(), Optional.empty());
        Unit b = unit(Path.of("/r/b/terragrunt.hcl"), List.of(), List.of(), Optional.empty());
        InfraGraph base = new InfraGraph(
            List.of(a, b), List.of(), List.of(), List.of(),
            List.of(),
            List.of(new InfraGraph.DependsOnEdge(a, b), new InfraGraph.DependsOnEdge(b, a)),
            List.of(),
            List.of(List.of(a, b)));

        InfraGraph after = InfraGraphPatcher.removeUnit(base, b);

        assertEquals(0, after.cycles().size());
    }

    @Test
    void removeCommon_dropsIncludesEdgesToCommon() {
        CommonHcl c = new CommonHcl(Path.of("/r/_common/vm.hcl"), "vm", Optional.empty(), Map.of());
        Unit u = unit(Path.of("/r/a/x/terragrunt.hcl"), List.of(),
            List.of(new IncludeBlock("common", "...", Optional.of(c.file()))), Optional.empty());

        InfraGraph base = new InfraGraph(
            List.of(u), List.of(), List.of(), List.of(c),
            List.of(), List.of(),
            List.of(new InfraGraph.IncludesEdge(u, c)),
            List.of());

        InfraGraph after = InfraGraphPatcher.removeCommon(base, c);

        assertEquals(0, after.commons().size());
        assertEquals(0, after.includesEdges().size());
        assertEquals(1, after.units().size(), "units list untouched");
    }

    @Test
    void removeCommon_leavesUnrelatedEdgesIntact() {
        Module m = new Module(Path.of("/r/modules/vm"), "vm", List.of());
        CommonHcl keep = new CommonHcl(Path.of("/r/_common/keep.hcl"), "keep", Optional.empty(), Map.of());
        CommonHcl drop = new CommonHcl(Path.of("/r/_common/drop.hcl"), "drop", Optional.empty(), Map.of());
        Unit u = unit(Path.of("/r/a/x/terragrunt.hcl"), List.of(),
            List.of(new IncludeBlock("keep", "...", Optional.of(keep.file()))),
            Optional.of(m.dir()));

        InfraGraph base = new InfraGraph(
            List.of(u), List.of(m), List.of(), List.of(keep, drop),
            List.of(new InfraGraph.UsesEdge(u, m)), List.of(),
            List.of(new InfraGraph.IncludesEdge(u, keep)),
            List.of());

        InfraGraph after = InfraGraphPatcher.removeCommon(base, drop);

        assertEquals(1, after.commons().size());
        assertEquals(keep.file(), after.commons().get(0).file());
        assertEquals(1, after.usesEdges().size());
        assertEquals(1, after.includesEdges().size());
    }
}

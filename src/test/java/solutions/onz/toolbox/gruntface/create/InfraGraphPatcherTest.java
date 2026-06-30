package solutions.onz.toolbox.gruntface.create;

import org.junit.jupiter.api.Test;
import solutions.onz.toolbox.gruntface.model.*;
import solutions.onz.toolbox.gruntface.model.Module;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class InfraGraphPatcherTest {

    @Test
    void addUnit_appendsUnitAndDerivedEdges() {
        Module m = new Module(Path.of("/r/modules/vm"), "vm", List.of());
        CommonHcl c = new CommonHcl(Path.of("/r/_common/vm.hcl"), "vm", Optional.empty(), Map.of());
        InfraGraph base = new InfraGraph(
            List.of(), List.of(m), List.of(), List.of(c),
            List.of(), List.of(), List.of(), List.of());

        Unit linked = new Unit(
            Path.of("/r/a/x/terragrunt.hcl"), "x",
            Optional.empty(), Optional.of(m.dir()),
            new LinkedHashMap<>(), Optional.empty(),
            List.of(),
            List.of(new IncludeBlock("common", "...", Optional.of(c.file()))),
            List.of(), "");

        InfraGraph after = InfraGraphPatcher.addUnit(base, linked);

        assertEquals(1, after.units().size());
        assertEquals(1, after.usesEdges().size(), "uses edge to local module");
        assertEquals(1, after.includesEdges().size(), "includes edge to common");
        assertEquals(0, after.dependsEdges().size());
    }

    @Test
    void addCommon_appendsCommonOnly() {
        InfraGraph base = new InfraGraph(
            List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of());

        CommonHcl c = new CommonHcl(Path.of("/r/_common/new.hcl"), "new", Optional.empty(), Map.of());

        InfraGraph after = InfraGraphPatcher.addCommon(base, c);
        assertEquals(1, after.commons().size());
        assertEquals(0, after.units().size());
    }

    @Test
    void addUnit_buildsDependsOnEdgesFromResolvedDependencies() {
        // Existing sibling: target of the new unit's "uami" dependency.
        Unit existing = new Unit(
            Path.of("/r/apps/payments/gwc/prod/managed-identity-apps/terragrunt.hcl"),
            "managed-identity-apps",
            Optional.empty(), Optional.empty(),
            new LinkedHashMap<>(), Optional.empty(),
            List.of(), List.of(), List.of(), "");

        InfraGraph base = new InfraGraph(
            List.of(existing), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of());

        Unit newUnit = new Unit(
            Path.of("/r/apps/payments/gwc/prod/key-vault-secrets/terragrunt.hcl"),
            "key-vault-secrets",
            Optional.empty(), Optional.empty(),
            new LinkedHashMap<>(), Optional.empty(),
            List.of(new Dependency("uami", "../managed-identity-apps",
                Optional.of(existing.file()))),
            List.of(), List.of(), "");

        InfraGraph after = InfraGraphPatcher.addUnit(base, newUnit);

        assertEquals(2, after.units().size());
        assertEquals(1, after.dependsEdges().size());
        assertEquals(newUnit.file(), after.dependsEdges().get(0).from().file());
        assertEquals(existing.file(), after.dependsEdges().get(0).to().file());
    }
}

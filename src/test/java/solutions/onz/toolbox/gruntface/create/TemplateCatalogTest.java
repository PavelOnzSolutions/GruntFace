package solutions.onz.toolbox.gruntface.create;

import org.junit.jupiter.api.Test;
import solutions.onz.toolbox.gruntface.model.*;
import solutions.onz.toolbox.gruntface.model.Module;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TemplateCatalogTest {

    @Test
    void resourceFromInclude_returnsOnlyIncludes() {
        InfraGraph g = graphWith(
            List.of(common("key-vault"), common("storage-account")),
            List.of(module("vm")),
            List.of(external("git::https://example/x"))
        );
        List<ResourceTemplate> got = TemplateCatalog.forMode(g, new WizardMode.ResourceFromInclude());
        assertEquals(2, got.size());
        assertTrue(got.stream().allMatch(t -> t instanceof ResourceTemplate.IncludeTemplate));
        assertEquals(List.of("key-vault", "storage-account"),
            got.stream().map(ResourceTemplate::displayName).toList());
    }

    @Test
    void resourceFromModule_returnsLocalsThenExternals() {
        InfraGraph g = graphWith(
            List.of(common("ignored")),
            List.of(module("vm"), module("kv")),
            List.of(external("git::https://example/x"))
        );
        List<ResourceTemplate> got = TemplateCatalog.forMode(g, new WizardMode.ResourceFromModule());
        assertEquals(3, got.size());
        assertTrue(got.get(0) instanceof ResourceTemplate.LocalModuleTemplate);
        assertTrue(got.get(1) instanceof ResourceTemplate.LocalModuleTemplate);
        assertTrue(got.get(2) instanceof ResourceTemplate.ExternalModuleTemplate);
    }

    @Test
    void includeFromModule_returnsLocalsThenExternals_noIncludes() {
        InfraGraph g = graphWith(
            List.of(common("ignored")),
            List.of(module("vm")),
            List.of(external("git::https://example/x"))
        );
        List<ResourceTemplate> got = TemplateCatalog.forMode(g, new WizardMode.IncludeFromModule());
        assertEquals(2, got.size());
        assertTrue(got.get(0) instanceof ResourceTemplate.LocalModuleTemplate);
        assertTrue(got.get(1) instanceof ResourceTemplate.ExternalModuleTemplate);
    }

    @Test
    void emptyGraph_returnsEmptyList() {
        InfraGraph g = graphWith(List.of(), List.of(), List.of());
        assertEquals(List.of(), TemplateCatalog.forMode(g, new WizardMode.ResourceFromInclude()));
        assertEquals(List.of(), TemplateCatalog.forMode(g, new WizardMode.ResourceFromModule()));
        assertEquals(List.of(), TemplateCatalog.forMode(g, new WizardMode.IncludeFromModule()));
    }

    private static InfraGraph graphWith(List<CommonHcl> commons, List<Module> modules, List<ExternalModule> externals) {
        return new InfraGraph(List.of(), modules, externals, commons, List.of(), List.of(), List.of(), List.of());
    }

    private static CommonHcl common(String name) {
        return new CommonHcl(Path.of("/tmp/_common/" + name + ".hcl"), name, Optional.empty(), Map.of());
    }
    private static Module module(String name) {
        return new Module(Path.of("/tmp/modules/" + name), name, List.of());
    }
    private static ExternalModule external(String ref) { return new ExternalModule(ref); }
}

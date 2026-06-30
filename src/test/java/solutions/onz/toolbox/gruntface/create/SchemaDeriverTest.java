package solutions.onz.toolbox.gruntface.create;

import org.junit.jupiter.api.Test;
import solutions.onz.toolbox.gruntface.model.*;
import solutions.onz.toolbox.gruntface.model.Module;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SchemaDeriverTest {

    @Test
    void resourceFromInclude_emitsConventionalThenPeerObservedKeys() {
        CommonHcl include = new CommonHcl(
            Path.of("/r/_common/key-vault.hcl"), "key-vault", Optional.empty(), Map.of());
        Unit existing = unit("/r/a/b/x/terragrunt.hcl", include,
            Map.of(ConventionalInputs.PURPOSE, str("dbx"), "extra_tag", str("foo")));
        InfraGraph g = new InfraGraph(
            List.of(existing), List.of(), List.of(),
            List.of(include),
            List.of(), List.of(),
            List.of(new InfraGraph.IncludesEdge(existing, include)),
            List.of());

        List<TemplateSchema> schema = SchemaDeriver.derive(
            new ResourceTemplate.IncludeTemplate(include),
            new WizardMode.ResourceFromInclude(),
            g);

        // 4 conventional + 1 peer-observed ("purpose" is conventional, not peer)
        assertEquals(5, schema.size());
        assertEquals(ConventionalInputs.PURPOSE, schema.get(0).name());
        assertEquals(SchemaGroup.CONVENTIONAL, schema.get(0).group());
        assertTrue(schema.get(0).required());
        // optional conventional
        TemplateSchema pns = schema.stream().filter(s -> s.name().equals(ConventionalInputs.PROJECT_NAME_SHORT)).findFirst().orElseThrow();
        assertFalse(pns.required());
        // peer-observed
        TemplateSchema extra = schema.get(4);
        assertEquals("extra_tag", extra.name());
        assertEquals(SchemaGroup.TEMPLATE, extra.group());
        assertFalse(extra.required());
    }

    @Test
    void resourceFromModule_emitsModuleVariablesInDeclarationOrder() {
        Module m = new Module(Path.of("/r/modules/vm"), "vm", List.of(
            new Variable("name", "string", "the name", Optional.empty()),
            new Variable("count", "number", "", Optional.of("1"))
        ));
        InfraGraph g = new InfraGraph(
            List.of(), List.of(m), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of());

        List<TemplateSchema> schema = SchemaDeriver.derive(
            new ResourceTemplate.LocalModuleTemplate(m),
            new WizardMode.ResourceFromModule(),
            g);

        assertEquals(2, schema.size());
        assertEquals("name", schema.get(0).name());
        assertTrue(schema.get(0).required());
        assertEquals(SchemaGroup.TEMPLATE, schema.get(0).group());
        assertEquals("count", schema.get(1).name());
        assertFalse(schema.get(1).required());
        assertEquals("1", schema.get(1).defaultLiteral().orElseThrow());
    }

    @Test
    void includeFromModule_emitsModuleVariables_likeResourceFromModule() {
        Module m = new Module(Path.of("/r/modules/kv"), "kv", List.of(
            new Variable("location", "string", "", Optional.empty())));
        InfraGraph g = new InfraGraph(
            List.of(), List.of(m), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of());

        List<TemplateSchema> schema = SchemaDeriver.derive(
            new ResourceTemplate.LocalModuleTemplate(m),
            new WizardMode.IncludeFromModule(),
            g);

        assertEquals(1, schema.size());
        assertEquals("location", schema.get(0).name());
    }

    @Test
    void resourceFromExternalModule_emitsNoTemplateRows() {
        ExternalModule ext = new ExternalModule("git::https://example/x");
        InfraGraph g = new InfraGraph(
            List.of(), List.of(), List.of(ext), List.of(),
            List.of(), List.of(), List.of(), List.of());

        List<TemplateSchema> schema = SchemaDeriver.derive(
            new ResourceTemplate.ExternalModuleTemplate(ext),
            new WizardMode.ResourceFromModule(),
            g);

        assertEquals(0, schema.size(), "No template rows when external module has no introspectable variables");
    }

    private static InputValue.StringValue str(String s) { return new InputValue.StringValue(s); }

    private static Unit unit(String path, CommonHcl include, Map<String, InputValue> inputs) {
        return new Unit(
            Path.of(path), Path.of(path).getParent().getFileName().toString(),
            Optional.of("${include.common.locals.base_source_url}"), Optional.empty(),
            inputs, Optional.empty(),
            List.of(),
            List.of(new IncludeBlock("common", "...", Optional.of(include.file()))),
            List.of(), "");
    }
}

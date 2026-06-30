package solutions.onz.toolbox.gruntface.hcl;

import solutions.onz.toolbox.gruntface.model.InputValue;
import solutions.onz.toolbox.gruntface.model.Unit;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Hcl4jHclServiceTest {

    private final HclService service = new Hcl4jHclService();

    @Test
    void parsesSimpleUnit() throws Exception {
        Unit u = service.parseUnit(Path.of("src/test/resources/fixtures/hcl/simple-unit.hcl"));

        assertEquals("../../../modules/vpc", u.sourceRef().orElseThrow());
        assertTrue(u.inputsRange().isPresent());
        assertTrue(u.dependencies().isEmpty());
        assertInstanceOf(InputValue.StringValue.class, u.inputs().get("cidr_block"));
        assertEquals("10.0.0.0/16", ((InputValue.StringValue) u.inputs().get("cidr_block")).value());
        assertInstanceOf(InputValue.BoolValue.class, u.inputs().get("enable_dns"));
        assertTrue(((InputValue.BoolValue) u.inputs().get("enable_dns")).value());
    }

    @Test
    void parsesDependencies() throws Exception {
        Unit u = service.parseUnit(Path.of("src/test/resources/fixtures/hcl/with-dependencies.hcl"));

        assertEquals(2, u.dependencies().size());
        assertEquals("vpc", u.dependencies().get(0).name());
        assertEquals("../vpc", u.dependencies().get(0).configPath());
        assertEquals("subnets", u.dependencies().get(1).name());
        assertInstanceOf(InputValue.NumberValue.class, u.inputs().get("node_count"));
        assertEquals("3", ((InputValue.NumberValue) u.inputs().get("node_count")).literal());
    }

    @Test
    void capturesUnresolvableExpressionsAsRawHcl() throws Exception {
        Unit u = service.parseUnit(Path.of("src/test/resources/fixtures/hcl/with-expressions.hcl"));

        InputValue vpc = u.inputs().get("vpc_id");
        assertInstanceOf(InputValue.RawHcl.class, vpc);
        InputValue cidrs = u.inputs().get("cidrs");
        assertInstanceOf(InputValue.RawHcl.class, cidrs);
    }

    @Test
    void preservesClosingBracketsInMultilineExpressions() throws Exception {
        Unit u = service.parseUnit(Path.of("src/test/resources/fixtures/hcl/with-nested-expressions.hcl"));

        InputValue config = u.inputs().get("config");
        assertInstanceOf(InputValue.RawHcl.class, config);
        String configHcl = ((InputValue.RawHcl) config).hcl();
        assertTrue(configHcl.startsWith("{"),
            "expected raw HCL to start with {, got: " + configHcl);
        assertTrue(configHcl.endsWith("}"),
            "expected raw HCL to end with } (closing bracket dropped?), got: " + configHcl);
        assertTrue(configHcl.contains("timeout = 30"),
            "expected nested body preserved, got: " + configHcl);
        assertTrue(configHcl.contains("local.something"),
            "expected nested expression preserved, got: " + configHcl);

        InputValue many = u.inputs().get("many");
        assertInstanceOf(InputValue.RawHcl.class, many);
        String manyHcl = ((InputValue.RawHcl) many).hcl();
        assertTrue(manyHcl.startsWith("{"), "expected raw HCL to start with {, got: " + manyHcl);
        assertTrue(manyHcl.endsWith("}"), "expected raw HCL to end with } (closing bracket dropped?), got: " + manyHcl);
        assertTrue(manyHcl.contains("[\"e\", \"f\"]"),
            "expected nested list preserved, got: " + manyHcl);

        // Following input must still be parsed correctly (proves we didn't run off the end).
        InputValue trailing = u.inputs().get("trailing");
        assertInstanceOf(InputValue.StringValue.class, trailing);
        assertEquals("ok", ((InputValue.StringValue) trailing).value());
    }

    @Test
    void preservesOriginalTextAndName() throws Exception {
        Path p = Path.of("src/test/resources/fixtures/hcl/simple-unit.hcl");
        Unit u = service.parseUnit(p);
        assertEquals(p.toAbsolutePath(), u.file().toAbsolutePath());
        assertEquals("hcl", u.name());
        assertFalse(u.originalText().isEmpty());
    }

    @Test
    void parsesCommonExtractsStringLocals() throws Exception {
        var c = service.parseCommon(Path.of("src/test/resources/fixtures/hcl/common-key-vault.hcl"));
        assertEquals("common-key-vault", c.name());
        assertEquals("git@github.com/PavelOnz/azure-terraform-modules.git//root-modules/key-vault",
            c.baseSourceUrl().orElseThrow());
        // root_path is a function call, not a literal — should NOT be in locals
        assertFalse(c.locals().containsKey("root_path"));
        // some_function is a function call — should NOT be in locals either
        assertFalse(c.locals().containsKey("some_function"));
    }

    @Test
    void parsesModuleVariables() throws Exception {
        var module = service.parseModule(Path.of("src/test/resources/fixtures/sample-project/modules/vpc"));

        assertEquals("vpc", module.name());
        assertEquals(3, module.variables().size());

        var name = module.variables().get(0);
        assertEquals("name", name.name());
        assertEquals("string", name.typeExpr());
        assertEquals("Network name", name.description());
        assertTrue(name.defaultLiteral().isEmpty());

        var cidr = module.variables().get(1);
        assertEquals("cidr_block", cidr.name());
        assertEquals("\"10.0.0.0/16\"", cidr.defaultLiteral().orElseThrow());

        var dns = module.variables().get(2);
        assertEquals("enable_dns", dns.name());
        assertEquals("bool", dns.typeExpr());
        assertEquals("true", dns.defaultLiteral().orElseThrow());
    }

    @Test
    void parseHierarchyLocals_extractsLiteralsAndBasenameIdiom() throws Exception {
        var locals = service.parseHierarchyLocals(
            Path.of("src/test/resources/fixtures/hierarchy/gwc/location.hcl"));

        assertEquals("germanywestcentral", locals.get("location"));
        assertEquals("gwc", locals.get("location_short")); // basename(get_terragrunt_dir()) -> file's dir name
        assertFalse(locals.containsKey("tags"), "non-literal entries must be skipped");
    }

    @Test
    void parseHierarchyLocals_noLocalsBlock_returnsEmpty() throws Exception {
        var locals = service.parseHierarchyLocals(
            Path.of("src/test/resources/fixtures/hierarchy/no-locals.hcl"));
        assertTrue(locals.isEmpty());
    }

    @Test
    void parsesMultilineTypesAndDefaults() throws Exception {
        var module = service.parseModule(Path.of("src/test/resources/fixtures/multiline-types"));

        assertEquals(3, module.variables().size());

        var simple = module.variables().get(0);
        assertEquals("simple", simple.name());
        assertEquals("string", simple.typeExpr());

        var config = module.variables().get(1);
        assertEquals("config", config.name());
        assertEquals(
            "object({\n    name = string\n    age  = number\n  })",
            config.typeExpr()
        );

        var tags = module.variables().get(2);
        assertEquals("tags", tags.name());
        assertEquals(
            "map(object({\n    key   = string\n    value = string\n  }))",
            tags.typeExpr()
        );
        assertEquals(
            "{\n    env = {\n      key   = \"env\"\n      value = \"prod\"\n    }\n  }",
            tags.defaultLiteral().orElseThrow()
        );
    }
}

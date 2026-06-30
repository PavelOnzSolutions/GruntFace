package solutions.onz.toolbox.gruntface.create;

import org.junit.jupiter.api.Test;
import solutions.onz.toolbox.gruntface.model.*;
import solutions.onz.toolbox.gruntface.model.Module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HclEmitterTest {

    @Test
    void resourceFromInclude_emitsExpectedHcl() throws IOException {
        CommonHcl include = new CommonHcl(
            Path.of("/repo/_common/key-vault.hcl"), "key-vault",
            Optional.of("git@github.com/PavelOnz/azure-terraform-modules.git//root-modules/key-vault"),
            Map.of());
        LinkedHashMap<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put(ConventionalInputs.PURPOSE, new InputValue.StringValue("secrets"));
        inputs.put(ConventionalInputs.LOCATION_SHORT, new InputValue.StringValue("gwc"));
        inputs.put(ConventionalInputs.ENVIRONMENT, new InputValue.StringValue("prod"));

        CreateRequest req = new CreateRequest(
            new WizardMode.ResourceFromInclude(),
            new ResourceTemplate.IncludeTemplate(include),
            Path.of("/repo/applications/x/gwc/prod"),
            "key-vault-secrets",
            new LinkedHashMap<>(),
            inputs);

        String out = HclEmitter.emit(req, Path.of("/repo"));
        assertEquals(loadFixture("resource-from-include/expected.hcl"), out);
    }

    @Test
    void resourceFromLocalModule_emitsExpectedHcl() throws IOException {
        Module m = new Module(Path.of("/repo/modules/key-vault"), "key-vault", List.of());

        LinkedHashMap<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put("name", new InputValue.StringValue("kv-secrets"));
        inputs.put("location", new InputValue.StringValue("westeurope"));

        CreateRequest req = new CreateRequest(
            new WizardMode.ResourceFromModule(),
            new ResourceTemplate.LocalModuleTemplate(m),
            Path.of("/repo/applications/x/gwc/prod"),
            "key-vault-secrets",
            new LinkedHashMap<>(),
            inputs);

        String out = HclEmitter.emit(req, Path.of("/repo"));
        assertEquals(loadFixture("resource-from-local-module/expected.hcl"), out);
    }

    @Test
    void resourceFromExternalModule_emitsExpectedHcl() throws IOException {
        ExternalModule ext = new ExternalModule("git::https://example.com/modules/kv.git?ref=v1.2.3");

        LinkedHashMap<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put("name", new InputValue.StringValue("kv-secrets"));

        CreateRequest req = new CreateRequest(
            new WizardMode.ResourceFromModule(),
            new ResourceTemplate.ExternalModuleTemplate(ext),
            Path.of("/repo/applications/x/gwc/prod"),
            "key-vault-secrets",
            new LinkedHashMap<>(),
            inputs);

        String out = HclEmitter.emit(req, Path.of("/repo"));
        assertEquals(loadFixture("resource-from-external-module/expected.hcl"), out);
    }

    @Test
    void includeFromLocalModule_emitsExpectedHcl() throws IOException {
        Module m = new Module(Path.of("/repo/modules/key-vault"), "key-vault", List.of());
        LinkedHashMap<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put("shared_tag", new InputValue.StringValue("posolutions"));
        CreateRequest req = new CreateRequest(
            new WizardMode.IncludeFromModule(),
            new ResourceTemplate.LocalModuleTemplate(m),
            Path.of("/repo/_common"),
            "key-vault",
            new LinkedHashMap<>(),
            inputs);
        String out = HclEmitter.emit(req, Path.of("/repo"));
        assertEquals(loadFixture("include-from-local-module/expected.hcl"), out);
    }

    @Test
    void includeFromExternalModule_emitsExpectedHcl() throws IOException {
        ExternalModule ext = new ExternalModule("git::https://example.com/modules/kv.git?ref=v1.2.3");
        LinkedHashMap<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put("shared_tag", new InputValue.StringValue("posolutions"));
        CreateRequest req = new CreateRequest(
            new WizardMode.IncludeFromModule(),
            new ResourceTemplate.ExternalModuleTemplate(ext),
            Path.of("/repo/_common"),
            "key-vault",
            new LinkedHashMap<>(),
            inputs);
        String out = HclEmitter.emit(req, Path.of("/repo"));
        assertEquals(loadFixture("include-from-external-module/expected.hcl"), out);
    }

    @Test
    void resourceFromExternalModule_escapesQuotesAndBackslashesInSourceRef() throws IOException {
        ExternalModule ext = new ExternalModule("git::https://host/repo.git?ref=\"v1\"");
        LinkedHashMap<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put("name", new InputValue.StringValue("x"));
        CreateRequest req = new CreateRequest(
            new WizardMode.ResourceFromModule(),
            new ResourceTemplate.ExternalModuleTemplate(ext),
            Path.of("/repo/a"),
            "x",
            new LinkedHashMap<>(),
            inputs);
        String out = HclEmitter.emit(req, Path.of("/repo"));
        // The source line must contain the escaped form, not the raw quotes:
        assertTrue(out.contains("source = \"git::https://host/repo.git?ref=\\\"v1\\\"\""),
            "expected escaped quotes; got: " + out);
    }

    @Test
    void resourceFromInclude_withDependencies_emitsDepBlocks() throws IOException {
        CommonHcl include = new CommonHcl(
            Path.of("/repo/_common/container-app.hcl"), "container-app",
            Optional.of("git@example/container-app"),
            Map.of());

        LinkedHashMap<String, DependencyDecl> deps = new LinkedHashMap<>();
        deps.put("container_app_env",
            new DependencyDecl("container_app_env", "../container-app-environment"));
        deps.put("managed_identity_apps",
            new DependencyDecl("managed_identity_apps", "../managed-identity-apps"));

        LinkedHashMap<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put(ConventionalInputs.PURPOSE, new InputValue.StringValue("powerauth"));
        inputs.put("resource_group_name",
            DependencyReference.build("container_app_env", "outputs.resource_group_name", false));
        inputs.put("user_assigned_identity_ids",
            DependencyReference.build("managed_identity_apps", "outputs.user_assigned_identity.id", true));

        CreateRequest req = new CreateRequest(
            new WizardMode.ResourceFromInclude(),
            new ResourceTemplate.IncludeTemplate(include),
            Path.of("/repo/applications/x/gwc/prod"),
            "container-app-powerauth",
            deps,
            inputs);

        String out = HclEmitter.emit(req, Path.of("/repo"));
        assertEquals(loadFixture("resource-from-include-with-deps/expected.hcl"), out);
    }

    @Test
    void resourceFromLocalModule_withDependencies_emitsDepBlocks() throws IOException {
        Module m = new Module(Path.of("/repo/modules/key-vault"), "key-vault", List.of());

        LinkedHashMap<String, DependencyDecl> deps = new LinkedHashMap<>();
        deps.put("uami", new DependencyDecl("uami", "../managed-identity-apps"));

        LinkedHashMap<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put("name", new InputValue.StringValue("kv-secrets"));
        inputs.put("uami_principal_id",
            DependencyReference.build("uami", "outputs.principal_id", false));

        CreateRequest req = new CreateRequest(
            new WizardMode.ResourceFromModule(),
            new ResourceTemplate.LocalModuleTemplate(m),
            Path.of("/repo/applications/x/gwc/prod"),
            "key-vault-secrets",
            deps,
            inputs);

        String out = HclEmitter.emit(req, Path.of("/repo"));
        assertEquals(loadFixture("resource-from-module-with-deps/expected.hcl"), out);
    }

    @Test
    void includeFromModule_dependenciesMustBeEmpty() {
        Module m = new Module(Path.of("/repo/modules/key-vault"), "key-vault", List.of());
        LinkedHashMap<String, DependencyDecl> deps = new LinkedHashMap<>();
        deps.put("uami", new DependencyDecl("uami", "../managed-identity-apps"));
        LinkedHashMap<String, InputValue> inputs = new LinkedHashMap<>();

        CreateRequest req = new CreateRequest(
            new WizardMode.IncludeFromModule(),
            new ResourceTemplate.LocalModuleTemplate(m),
            Path.of("/repo/_common"),
            "key-vault",
            deps,
            inputs);

        assertThrows(IllegalArgumentException.class,
            () -> HclEmitter.emit(req, Path.of("/repo")));
    }

    static String loadFixture(String relative) throws IOException {
        Path p = Paths.get("src/test/resources/fixtures/create", relative);
        // Normalise line endings so the test is platform-independent.
        return Files.readString(p).replace("\r\n", "\n");
    }
}

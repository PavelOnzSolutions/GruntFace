package solutions.onz.toolbox.gruntface.create;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import solutions.onz.toolbox.gruntface.model.*;
import solutions.onz.toolbox.gruntface.model.Module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CreateServiceTest {

    @Test
    void plan_targetIsParentDirSlashFolderNameSlashTerragruntHcl_forResource(@TempDir Path tmp) {
        CreateService svc = new CreateService();
        CreatePlan plan = svc.plan(resourceFromIncludeRequest(tmp), tmp);
        assertEquals(tmp.resolve("a/gwc/prod/x/terragrunt.hcl"), plan.targetFile());
        assertTrue(plan.conflictsWith().isEmpty());
        assertTrue(plan.contentToWrite().contains("inputs = {"));
    }

    @Test
    void plan_targetIsParentDirSlashFolderNameDotHcl_forInclude(@TempDir Path tmp) throws IOException {
        CreateService svc = new CreateService();
        Module m = new Module(tmp.resolve("modules/kv"), "kv", List.of());
        Files.createDirectories(m.dir());
        LinkedHashMap<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put("shared_tag", new InputValue.StringValue("foo"));
        CreateRequest req = new CreateRequest(
            new WizardMode.IncludeFromModule(),
            new ResourceTemplate.LocalModuleTemplate(m),
            tmp.resolve("_common"),
            "kv-common",
            new LinkedHashMap<>(),
            inputs);
        CreatePlan plan = svc.plan(req, tmp);
        assertEquals(tmp.resolve("_common/kv-common.hcl"), plan.targetFile());
    }

    @Test
    void plan_conflictDetected_whenTargetExists(@TempDir Path tmp) throws IOException {
        CreateService svc = new CreateService();
        CreateRequest req = resourceFromIncludeRequest(tmp);
        Path target = tmp.resolve("a/gwc/prod/x/terragrunt.hcl");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "existing");
        CreatePlan plan = svc.plan(req, tmp);
        assertTrue(plan.conflictsWith().isPresent());
        assertEquals(target, plan.conflictsWith().get());
    }

    @Test
    void commit_writesFileAtomically_andMkdirs(@TempDir Path tmp) throws IOException {
        CreateService svc = new CreateService();
        CreateRequest req = resourceFromIncludeRequest(tmp);
        CreatePlan plan = svc.plan(req, tmp);
        svc.commit(plan);
        assertTrue(Files.exists(plan.targetFile()));
        assertEquals(plan.contentToWrite(), Files.readString(plan.targetFile()));
    }

    @Test
    void commit_failsWhenFileAlreadyExists(@TempDir Path tmp) throws IOException {
        CreateService svc = new CreateService();
        CreateRequest req = resourceFromIncludeRequest(tmp);
        Path target = tmp.resolve("a/gwc/prod/x/terragrunt.hcl");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "existing");
        CreatePlan plan = svc.plan(req, tmp);
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> svc.commit(plan));
    }

    private CreateRequest resourceFromIncludeRequest(Path tmp) {
        CommonHcl include = new CommonHcl(
            tmp.resolve("_common/kv.hcl"), "kv",
            Optional.of("git@x/kv"), Map.of());
        LinkedHashMap<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put(ConventionalInputs.PURPOSE, new InputValue.StringValue("x"));
        inputs.put(ConventionalInputs.LOCATION_SHORT, new InputValue.StringValue("gwc"));
        inputs.put(ConventionalInputs.ENVIRONMENT, new InputValue.StringValue("prod"));
        return new CreateRequest(
            new WizardMode.ResourceFromInclude(),
            new ResourceTemplate.IncludeTemplate(include),
            tmp.resolve("a/gwc/prod"),
            "x",
            new LinkedHashMap<>(),
            inputs);
    }
}

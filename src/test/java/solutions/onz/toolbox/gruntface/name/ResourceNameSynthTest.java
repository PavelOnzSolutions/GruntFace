package solutions.onz.toolbox.gruntface.name;

import org.junit.jupiter.api.Test;
import solutions.onz.toolbox.gruntface.azure.AzureResourceCatalog;
import solutions.onz.toolbox.gruntface.azure.AzureResourceInferrer;
import solutions.onz.toolbox.gruntface.model.*;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ResourceNameSynthTest {

    private static Unit unit(String name, Map<String, InputValue> inputs) {
        return new Unit(
            Path.of("C:/proj/platform/management/gwc/prod").resolve(name).resolve("terragrunt.hcl"),
            name,
            Optional.of("github.com/terraform-modules.git//root-modules/key-vault?ref=v1"),
            Optional.empty(),
            inputs, Optional.empty(),
            List.of(), List.of(), List.of(), ""
        );
    }

    @Test
    void pass1_uses_explicit_name_input() {
        Map<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put("name", new InputValue.StringValue("kv-explicit"));
        Unit u = unit("key-vault", inputs);

        ResolvedName r = new ResourceNameSynth().synthesise(u, EvaluationContext.empty(),
            AzureResourceInferrer.infer(u));

        assertEquals("kv-explicit", r.text());
        assertEquals(ResolvedName.Confidence.LITERAL, r.confidence());
    }

    @Test
    void pass2_assembles_name_from_convention() {
        Map<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put("purpose", new InputValue.StringValue("mgmt"));
        Unit u = unit("key-vault", inputs);
        EvaluationContext ctx = new EvaluationContext(
            Map.of("location_short", "gwc", "environment", "prod"),
            Map.of("common", Map.of("location_short", "gwc", "environment", "prod")),
            inputs
        );

        ResolvedName r = new ResourceNameSynth().synthesise(u, ctx,
            AzureResourceInferrer.infer(u));

        assertEquals("kv-mgmt-gwc-prod", r.text());
        assertEquals(ResolvedName.Confidence.EVALUATED, r.confidence());
    }

    @Test
    void pass3_falls_back_to_folder_name() {
        Unit u = unit("key-vault", Map.of());
        ResolvedName r = new ResourceNameSynth().synthesise(u, EvaluationContext.empty(),
            AzureResourceInferrer.infer(u));
        assertEquals("key-vault", r.text());
        assertEquals(ResolvedName.Confidence.FALLBACK, r.confidence());
    }
}

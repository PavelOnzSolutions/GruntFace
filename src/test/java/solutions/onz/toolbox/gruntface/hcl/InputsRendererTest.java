package solutions.onz.toolbox.gruntface.hcl;

import solutions.onz.toolbox.gruntface.model.InputValue;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InputsRendererTest {

    @Test
    void rendersPrimitivesAligned() {
        Map<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put("name", new InputValue.StringValue("dev-vpc"));
        inputs.put("cidr_block", new InputValue.StringValue("10.0.0.0/16"));
        inputs.put("enable_dns", new InputValue.BoolValue(true));
        inputs.put("node_count", new InputValue.NumberValue("3"));

        String rendered = InputsRenderer.render(inputs, List.of("name", "cidr_block", "enable_dns", "node_count"));

        assertTrue(rendered.startsWith("inputs = {\n"));
        assertTrue(rendered.endsWith("}"));
        assertTrue(rendered.contains("name       = \"dev-vpc\""));
        assertTrue(rendered.contains("cidr_block = \"10.0.0.0/16\""));
        assertTrue(rendered.contains("enable_dns = true"));
        assertTrue(rendered.contains("node_count = 3"));
    }

    @Test
    void preservesRawHcl() {
        Map<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put("vpc_id", new InputValue.RawHcl("dependency.vpc.outputs.id"));
        inputs.put("cidrs", new InputValue.RawHcl("[\"10.0.1.0/24\", \"10.0.2.0/24\"]"));

        String rendered = InputsRenderer.render(inputs, List.of("vpc_id", "cidrs"));
        assertTrue(rendered.contains("vpc_id = dependency.vpc.outputs.id"));
        assertTrue(rendered.contains("cidrs  = [\"10.0.1.0/24\", \"10.0.2.0/24\"]"));
    }

    @Test
    void declaredOrderFirstExtrasAppended() {
        Map<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put("zzz_extra", new InputValue.StringValue("e"));
        inputs.put("a", new InputValue.StringValue("a"));

        String rendered = InputsRenderer.render(inputs, List.of("a"));
        int aIdx = rendered.indexOf("a ");
        int extraIdx = rendered.indexOf("zzz_extra");
        assertTrue(aIdx < extraIdx, "declared key 'a' should come before extra 'zzz_extra'");
    }

    @Test
    void escapesStringSpecialChars() {
        Map<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put("note", new InputValue.StringValue("line1\nline2 \"quoted\""));

        String rendered = InputsRenderer.render(inputs, List.of("note"));
        assertTrue(rendered.contains("\"line1\\nline2 \\\"quoted\\\"\""));
    }
}

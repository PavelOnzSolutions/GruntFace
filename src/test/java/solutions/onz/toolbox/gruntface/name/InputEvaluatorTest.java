package solutions.onz.toolbox.gruntface.name;

import org.junit.jupiter.api.Test;
import solutions.onz.toolbox.gruntface.model.InputValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InputEvaluatorTest {

    @Test
    void evaluates_plain_string_literal_from_inputs() {
        Map<String, InputValue> inputs = new LinkedHashMap<>();
        inputs.put("purpose", new InputValue.StringValue("monitor-alerts"));
        EvaluationContext ctx = new EvaluationContext(Map.of(), Map.of(), inputs);

        Optional<String> r = new InputEvaluator().eval("var.purpose", ctx);

        assertEquals(Optional.of("monitor-alerts"), r);
    }

    @Test
    void evaluates_local_lookup() {
        EvaluationContext ctx = new EvaluationContext(
            Map.of("location_short", "gwc"),
            Map.of("common", Map.of("location_short", "gwc")),
            Map.of()
        );

        assertEquals(Optional.of("gwc"), new InputEvaluator().eval("local.location_short", ctx));
        assertEquals(Optional.of("gwc"),
            new InputEvaluator().eval("include.common.locals.location_short", ctx));
    }

    @Test
    void unknown_reference_is_empty() {
        EvaluationContext ctx = EvaluationContext.empty();
        assertTrue(new InputEvaluator().eval("var.missing", ctx).isEmpty());
        assertTrue(new InputEvaluator().eval("local.missing", ctx).isEmpty());
    }

    @Test
    void unsupported_expression_is_empty() {
        EvaluationContext ctx = EvaluationContext.empty();
        assertTrue(new InputEvaluator().eval("merge(local.a, local.b)", ctx).isEmpty());
        assertTrue(new InputEvaluator().eval("[1, 2, 3]", ctx).isEmpty());
    }

    @Test
    void interpolates_multiple_segments_with_literal_glue() {
        EvaluationContext ctx = new EvaluationContext(
            Map.of("location_short", "gwc", "environment", "prod"),
            Map.of("common",
                Map.of("location_short", "gwc", "environment", "prod")),
            Map.of("purpose", new InputValue.StringValue("monitor-alerts"))
        );

        Optional<String> r = new InputEvaluator().eval(
            "\"rg-${var.purpose}-${include.common.locals.location_short}-${local.environment}\"",
            ctx
        );

        assertEquals(Optional.of("rg-monitor-alerts-gwc-prod"), r);
    }

    @Test
    void interpolation_with_missing_segment_is_empty() {
        EvaluationContext ctx = new EvaluationContext(
            Map.of("environment", "prod"), Map.of(), Map.of()
        );
        Optional<String> r = new InputEvaluator().eval(
            "\"rg-${local.location_short}-${local.environment}\"", ctx);
        assertTrue(r.isEmpty());
    }
}

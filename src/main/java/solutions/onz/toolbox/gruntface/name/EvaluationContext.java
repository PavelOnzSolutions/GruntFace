package solutions.onz.toolbox.gruntface.name;

import solutions.onz.toolbox.gruntface.model.InputValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable view of the data the {@link InputEvaluator} consults.
 *
 * <ul>
 *   <li>{@code locals} — string-literal-only locals harvested from the unit's first include's
 *       {@code CommonHcl}. Keyed by local name (e.g. {@code location_short}).</li>
 *   <li>{@code includeLocals} — same data, keyed by {@code include-name -> local-name -> value}
 *       to support both {@code local.x} and {@code include.<name>.locals.x} references.</li>
 *   <li>{@code inputs} — the unit's own literal {@code inputs} entries.</li>
 * </ul>
 */
public record EvaluationContext(
    Map<String, String> locals,
    Map<String, Map<String, String>> includeLocals,
    Map<String, InputValue> inputs
) {
    public EvaluationContext {
        locals = Map.copyOf(locals);
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> e : includeLocals.entrySet()) {
            copy.put(e.getKey(), Map.copyOf(e.getValue()));
        }
        includeLocals = Map.copyOf(copy);
        inputs = Map.copyOf(inputs);
    }

    public static EvaluationContext empty() {
        return new EvaluationContext(Map.of(), Map.of(), Map.of());
    }
}

package solutions.onz.toolbox.gruntface.name;

import solutions.onz.toolbox.gruntface.azure.AzureResourceInferrer;
import solutions.onz.toolbox.gruntface.model.InputValue;
import solutions.onz.toolbox.gruntface.model.Unit;

import java.util.List;
import java.util.Optional;

import static solutions.onz.toolbox.gruntface.name.ResolvedName.Confidence.*;

/**
 * Three-pass synth:
 * <ol>
 *   <li>Look for an explicit {@code name} / {@code resource_name} / {@code <kind>_name} input.</li>
 *   <li>If the Azure resource type is known, assemble {@code <prefix>-<purpose>-<location_short>-<environment>}
 *       from evaluated inputs and locals.</li>
 *   <li>Otherwise fall back to the unit's folder name with {@code FALLBACK} confidence.</li>
 * </ol>
 */
public class ResourceNameSynth {

    private final InputEvaluator evaluator = new InputEvaluator();

    public ResolvedName synthesise(Unit u, EvaluationContext ctx, AzureResourceInferrer.Match match) {
        Optional<ResolvedName> p1 = pass1ExplicitName(u, ctx, match);
        if (p1.isPresent()) return p1.get();

        Optional<ResolvedName> p2 = pass2Convention(u, ctx, match);
        if (p2.isPresent()) return p2.get();

        return new ResolvedName(u.name(), FALLBACK);
    }

    private Optional<ResolvedName> pass1ExplicitName(
            Unit u, EvaluationContext ctx, AzureResourceInferrer.Match match) {
        List<String> candidates = new java.util.ArrayList<>();
        candidates.add("name");
        candidates.add("resource_name");
        if (match.confidence() != AzureResourceInferrer.Confidence.UNKNOWN) {
            String kind = match.resource().id().replace('-', '_');
            candidates.add(kind + "_name");
        }
        for (String key : candidates) {
            InputValue iv = u.inputs().get(key);
            if (iv == null) continue;
            if (iv instanceof InputValue.StringValue s && !s.value().contains("${")) {
                return Optional.of(new ResolvedName(s.value(), LITERAL));
            }
            String exprText = expressionTextOf(iv);
            if (exprText == null) continue;
            Optional<String> evaluated = evaluator.eval(exprText, ctx);
            if (evaluated.isPresent()) {
                return Optional.of(new ResolvedName(evaluated.get(), EVALUATED));
            }
        }
        return Optional.empty();
    }

    private Optional<ResolvedName> pass2Convention(
            Unit u, EvaluationContext ctx, AzureResourceInferrer.Match match) {
        if (match.confidence() == AzureResourceInferrer.Confidence.UNKNOWN) return Optional.empty();
        Optional<String> prefix = AzureResourceNaming.prefixFor(match.resource().id());
        if (prefix.isEmpty()) return Optional.empty();

        Optional<String> purpose = evalInput(u, ctx, "purpose");
        Optional<String> locShort = evalInput(u, ctx, "location_short")
            .or(() -> Optional.ofNullable(ctx.locals().get("location_short")));
        Optional<String> env = evalInput(u, ctx, "environment")
            .or(() -> Optional.ofNullable(ctx.locals().get("environment")));
        Optional<String> projectShort = evalInput(u, ctx, "project_name_short")
            .or(() -> Optional.ofNullable(ctx.locals().get("project_name_short")));

        if (purpose.isEmpty() || locShort.isEmpty() || env.isEmpty()) return Optional.empty();

        StringBuilder sb = new StringBuilder(prefix.get());
        projectShort.ifPresent(ps -> sb.append('-').append(ps));
        sb.append('-').append(purpose.get())
          .append('-').append(locShort.get())
          .append('-').append(env.get());
        return Optional.of(new ResolvedName(sb.toString(), EVALUATED));
    }

    private Optional<String> evalInput(Unit u, EvaluationContext ctx, String name) {
        InputValue iv = u.inputs().get(name);
        if (iv == null) return Optional.empty();
        return switch (iv) {
            case InputValue.StringValue s when !s.value().contains("${") -> Optional.of(s.value());
            case InputValue.NumberValue n -> Optional.of(n.literal());
            case InputValue.BoolValue b -> Optional.of(Boolean.toString(b.value()));
            default -> {
                String text = expressionTextOf(iv);
                yield text == null ? Optional.empty() : evaluator.eval(text, ctx);
            }
        };
    }

    private static String expressionTextOf(InputValue iv) {
        return switch (iv) {
            case InputValue.RawHcl r -> r.hcl();
            case InputValue.StringValue s -> "\"" + s.value() + "\"";
            default -> null;
        };
    }
}

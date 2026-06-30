package solutions.onz.toolbox.gruntface.create;

import solutions.onz.toolbox.gruntface.model.InputValue;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Round-trips between an on-disk {@link InputValue.RawHcl} carrying
 * {@code dependency.<name>.<suffix>} (optionally wrapped in {@code [ ... ]}) and a structured
 * {@link Match}. The on-disk representation of a dependency reference is intentionally just
 * {@code RawHcl} so the existing {@code InputsRenderer} emits it correctly without changes.
 */
public final class DependencyReference {

    private static final Pattern PATTERN = Pattern.compile(
        "^(\\s*\\[\\s*)?dependency\\.([A-Za-z_]\\w*)\\.([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)*)(\\s*\\]\\s*)?$"
    );

    public record Match(String depName, String outputsPath, boolean wrappedInList) {}

    private DependencyReference() {}

    public static Optional<Match> parse(InputValue v) {
        if (!(v instanceof InputValue.RawHcl raw)) return Optional.empty();
        Matcher m = PATTERN.matcher(raw.hcl());
        if (!m.matches()) return Optional.empty();
        boolean leftBracket = m.group(1) != null;
        boolean rightBracket = m.group(4) != null;
        if (leftBracket != rightBracket) return Optional.empty();   // mismatched brackets
        return Optional.of(new Match(m.group(2), m.group(3), leftBracket));
    }

    public static InputValue.RawHcl build(String depName, String outputsPath, boolean wrapInList) {
        String core = "dependency." + depName + "." + outputsPath;
        return new InputValue.RawHcl(wrapInList ? "[" + core + "]" : core);
    }
}

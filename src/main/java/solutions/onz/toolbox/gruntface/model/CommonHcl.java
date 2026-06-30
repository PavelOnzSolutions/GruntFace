package solutions.onz.toolbox.gruntface.model;

import java.nio.file.Path;
import java.util.Map;

/**
 * A shared Terragrunt configuration file (typically under `_common/`) that's pulled in via
 * `include "<name>" { ... }` blocks by one or more units.
 *
 * `locals` only contains entries whose values are simple string literals. Anything else
 * (function calls, references, merges) is omitted — locals are used to resolve unit-level
 * interpolations like `${include.common.locals.base_source_url}`.
 */
public record CommonHcl(
    Path file,
    String name,                            // file name without `.hcl` suffix
    java.util.Optional<String> baseSourceUrl,
    Map<String, String> locals
) {
    public CommonHcl {
        locals = Map.copyOf(locals);
    }
}

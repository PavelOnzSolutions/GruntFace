package solutions.onz.toolbox.gruntface.create;

import java.util.Optional;

public record TemplateSchema(
    String name,
    String typeExpr,            // "string", "bool", "number", "list(string)", "object({...})", or "" if unknown
    String description,         // may be empty
    boolean required,
    SchemaGroup group,
    Optional<String> defaultLiteral
) {}

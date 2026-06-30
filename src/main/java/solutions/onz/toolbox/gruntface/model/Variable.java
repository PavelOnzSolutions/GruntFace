package solutions.onz.toolbox.gruntface.model;

import java.util.Optional;

public record Variable(
    String name,
    String typeExpr,
    String description,
    Optional<String> defaultLiteral
) {}

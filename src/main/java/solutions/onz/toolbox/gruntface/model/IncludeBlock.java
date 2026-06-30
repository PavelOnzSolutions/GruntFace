package solutions.onz.toolbox.gruntface.model;

/**
 * A Terragrunt `include "<name>" { path = ... }` block as it appears in the file.
 * The path expression is captured raw; resolution happens in the discovery layer.
 */
public record IncludeBlock(
    String name,
    String pathExpr,        // raw HCL expression text, e.g. find_in_parent_folders("root.hcl")
    java.util.Optional<java.nio.file.Path> resolvedPath
) {}

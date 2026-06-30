package solutions.onz.toolbox.gruntface.create;

/**
 * A declared dependency that will be emitted as a {@code dependency "<name>" { ... }} block
 * in a new Terragrunt unit's HCL file.
 *
 * @param name        HCL identifier ({@code [A-Za-z_]\w*}), used as the block label and as
 *                    the prefix in references such as {@code dependency.<name>.outputs.<key>}.
 * @param configPath  Relative path from the new unit's directory to the dependency target
 *                    (e.g. {@code "../container-app-environment"}).
 */
public record DependencyDecl(String name, String configPath) {
    public DependencyDecl {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("dependency name must not be blank");
        }
        if (configPath == null || configPath.isBlank()) {
            throw new IllegalArgumentException("dependency configPath must not be blank");
        }
    }
}

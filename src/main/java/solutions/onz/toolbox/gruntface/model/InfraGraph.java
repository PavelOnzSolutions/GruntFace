package solutions.onz.toolbox.gruntface.model;

import java.util.List;

public record InfraGraph(
    List<Unit> units,
    List<Module> modules,
    List<ExternalModule> externals,
    List<CommonHcl> commons,
    List<UsesEdge> usesEdges,
    List<DependsOnEdge> dependsEdges,
    List<IncludesEdge> includesEdges,
    List<List<Unit>> cycles
) {
    public InfraGraph {
        units = List.copyOf(units);
        modules = List.copyOf(modules);
        externals = List.copyOf(externals);
        commons = List.copyOf(commons);
        usesEdges = List.copyOf(usesEdges);
        dependsEdges = List.copyOf(dependsEdges);
        includesEdges = List.copyOf(includesEdges);
        cycles = List.copyOf(cycles);
    }

    public record UsesEdge(Unit from, Object to) {}
    public record DependsOnEdge(Unit from, Unit to) {}
    public record IncludesEdge(Unit from, CommonHcl to) {}
}

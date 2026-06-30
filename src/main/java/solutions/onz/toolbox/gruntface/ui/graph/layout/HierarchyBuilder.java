package solutions.onz.toolbox.gruntface.ui.graph.layout;

import solutions.onz.toolbox.gruntface.model.Unit;

import java.nio.file.Path;
import java.util.*;

/**
 * Infers region and environment names from a Unit's filesystem path. Convention:
 * units sit at {@code <terragrunt-root>/<product>/<region>/<env>/<resource>/terragrunt.hcl}.
 * Units that don't fit that shape (e.g. under {@code _common}) become "uncategorised".
 *
 * Region and env are recognised by being the third-from-last and second-from-last directory
 * segments under the root respectively. We deliberately do not maintain a closed allow-list
 * of region/env names — anything that fits the positional pattern is accepted.
 */
public final class HierarchyBuilder {

    private HierarchyBuilder() {}

    public record Result(Optional<String> region, Optional<String> env) {}

    public record Tree(
        List<String> regionIds,
        List<String> envIds,
        Map<String, String> envParentRegionId,   // env id → region id
        Map<Path, String> containerOfUnit,        // unit file path → container id
        Map<String, String> labels                // container id → display label
    ) {
        public Optional<String> containerOf(Unit u) {
            return Optional.ofNullable(containerOfUnit.get(u.file()));
        }
    }

    /**
     * Classifies the given {@code Unit} based on its file path relative to the specified {@code terragruntRoot}.
     * Determines the region and environment inferred from the file path structure.
     *
     * The file path must follow the structure: <something>/<region>/<env>/<resource>. If the structure
     * does not meet these criteria or if the path cannot be relativized, empty results are returned.
     *
     * @param u the {@link Unit} whose file path is to be classified
     * @param terragruntRoot the root path against which the {@link Unit}'s file path is relativized
     * @return a {@link Result} containing the inferred region and environment, or empty values if classification fails
     */
    public static Result classify(Unit u, Path terragruntRoot) {
        Path rel = relativise(u.file().getParent(), terragruntRoot);
        if (rel == null) return new Result(Optional.empty(), Optional.empty());
        int n = rel.getNameCount();
        // Region/env are always at n-3 / n-2 (resource at n-1). The minimum shape under
        // the root is <region>/<env>/<resource> — i.e. opening the project at any depth
        // works, as long as the unit still sits under a <region>/<env>/<resource> path.
        if (n < 3) return new Result(Optional.empty(), Optional.empty());
        String region = rel.getName(n - 3).toString();
        String env = rel.getName(n - 2).toString();
        return new Result(Optional.of(region), Optional.of(env));
    }

    /**
     * Builds a hierarchical {@link Tree} representation based on the provided list of {@link Unit}s
     * and their file paths relative to the specified terragrunt root directory.
     *
     * The hierarchy is constructed by classifying each unit's file path to determine its region
     * and environment. Units are grouped into containers representing the regions and environments
     * they belong to. If a unit cannot be classified into a specific region or environment, it is
     * placed in an "uncategorised" container.
     *
     * @param units the list of {@link Unit}s to be processed into the hierarchical tree structure
     * @param terragruntRoot the root directory used as the base for classifying the {@link Unit}s' file paths
     * @return a {@link Tree} containing the hierarchical structure of regions, environments, and their relationships
     */
    public static Tree build(List<Unit> units, Path terragruntRoot) {
        List<String> regions = new ArrayList<>();
        List<String> envs = new ArrayList<>();
        Map<String, String> envParent = new LinkedHashMap<>();
        Map<Path, String> containerOf = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();

        boolean hasUncategorised = false;
        for (Unit u : units) {
            Result r = classify(u, terragruntRoot);
            if (r.region().isEmpty() || r.env().isEmpty()) {
                containerOf.put(u.file(), "uncategorised");
                hasUncategorised = true;
                continue;
            }
            String regionId = "region:" + r.region().get();
            String envId = "env:" + r.region().get() + "/" + r.env().get();
            if (!regions.contains(regionId)) {
                regions.add(regionId);
                labels.put(regionId, r.region().get());
            }
            if (!envs.contains(envId)) {
                envs.add(envId);
                labels.put(envId, r.env().get());
                envParent.put(envId, regionId);
            }
            containerOf.put(u.file(), envId);
        }
        if (hasUncategorised) labels.put("uncategorised", "uncategorised");
        return new Tree(
            List.copyOf(regions),
            List.copyOf(envs),
            Map.copyOf(envParent),
            Map.copyOf(containerOf),
            Map.copyOf(labels)
        );
    }

    private static Path relativise(Path dir, Path root) {
        try {
            return root.toAbsolutePath().normalize().relativize(dir.toAbsolutePath().normalize());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

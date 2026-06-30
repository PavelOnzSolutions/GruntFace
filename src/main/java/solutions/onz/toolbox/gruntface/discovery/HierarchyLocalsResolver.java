package solutions.onz.toolbox.gruntface.discovery;

import solutions.onz.toolbox.gruntface.hcl.HclService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Resolves Terragrunt "hierarchy" locals for a unit. Walks from the unit's directory upward,
 * harvesting simple locals (string literals and {@code basename(get_terragrunt_dir())}) from
 * every {@code *.hcl} file at each level via {@link HclService#parseHierarchyLocals}. Definitions
 * nearer the unit win. The walk stops after the directory containing {@code root.hcl} — the anchor
 * {@code find_in_parent_folders} uses — and is bounded by the filesystem root.
 */
public final class HierarchyLocalsResolver {

    private final HclService hcl;

    public HierarchyLocalsResolver(HclService hcl) {
        this.hcl = hcl;
    }

    public Map<String, String> resolve(Path unitFile) {
        Map<String, String> out = new LinkedHashMap<>();
        Path dir = unitFile.toAbsolutePath().normalize().getParent();
        while (dir != null) {
            boolean reachedRoot = harvestDir(dir, out);
            if (reachedRoot) break;
            dir = dir.getParent();
        }
        return out;
    }

    /** Harvest all {@code *.hcl} in {@code dir} (nearest-wins). Returns true if {@code dir} holds root.hcl. */
    private boolean harvestDir(Path dir, Map<String, String> out) {
        List<Path> hclFiles;
        try (Stream<Path> s = Files.list(dir)) {
            hclFiles = s.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".hcl"))
                        .sorted()
                        .toList();
        } catch (IOException e) {
            return false;
        }
        boolean reachedRoot = false;
        for (Path f : hclFiles) {
            if (f.getFileName().toString().equals("root.hcl")) reachedRoot = true;
            try {
                for (Map.Entry<String, String> e : hcl.parseHierarchyLocals(f).entrySet()) {
                    out.putIfAbsent(e.getKey(), e.getValue());
                }
            } catch (IOException ignored) {
                // Unreadable file: skip it, keep walking.
            }
        }
        return reachedRoot;
    }
}

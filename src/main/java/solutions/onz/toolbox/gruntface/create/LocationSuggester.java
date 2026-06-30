package solutions.onz.toolbox.gruntface.create;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class LocationSuggester {

    public record Suggestion(
        Optional<String> locationShort,
        Optional<String> environment,
        Optional<String> purpose
    ) {
        public static Suggestion empty() { return new Suggestion(Optional.empty(), Optional.empty(), Optional.empty()); }
    }

    private static final Set<String> KNOWN_REGIONS = Set.of(
        "gwc", "weu", "neu", "eus", "wus", "ne", "we", "cus", "scus", "eas"
    );
    private static final Set<String> KNOWN_ENVS = Set.of(
        "prod", "preprod", "dev", "test", "staging", "stage", "qa", "uat", "pre", "prd"
    );

    private LocationSuggester() {}

    public static Suggestion suggest(Path parentDir, Path terragruntRoot) {
        List<String> segments = relativeSegments(parentDir, terragruntRoot);
        Optional<String> region = Optional.empty();
        Optional<String> env = Optional.empty();
        for (int i = 0; i < segments.size(); i++) {
            String seg = segments.get(i);
            if (region.isEmpty() && KNOWN_REGIONS.contains(seg)) {
                region = Optional.of(seg);
                if (i + 1 < segments.size() && KNOWN_ENVS.contains(segments.get(i + 1))) {
                    env = Optional.of(segments.get(i + 1));
                }
            }
        }
        Optional<String> purpose = segments.isEmpty()
            ? Optional.empty()
            : Optional.of(segments.get(segments.size() - 1));
        return new Suggestion(region, env, purpose);
    }

    private static List<String> relativeSegments(Path parentDir, Path terragruntRoot) {
        Path normP = parentDir.toAbsolutePath().normalize();
        Path normR = terragruntRoot.toAbsolutePath().normalize();
        if (!normP.startsWith(normR)) {
            // Fall back to all segments of the parent dir.
            return segments(normP);
        }
        return segments(normR.relativize(normP));
    }

    private static List<String> segments(Path p) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < p.getNameCount(); i++) out.add(p.getName(i).toString());
        return out;
    }
}

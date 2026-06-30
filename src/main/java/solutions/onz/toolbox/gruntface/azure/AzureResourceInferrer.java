package solutions.onz.toolbox.gruntface.azure;

import solutions.onz.toolbox.gruntface.model.Unit;

import java.util.Optional;

public final class AzureResourceInferrer {

    public enum Confidence { MATCHED, GUESSED, UNKNOWN }

    public record Match(AzureResource resource, Confidence confidence) {}

    private AzureResourceInferrer() {}

    public static Match infer(Unit unit) {
        Optional<String> sourceRef = unit.sourceRef();
        if (sourceRef.isPresent() && !sourceRef.get().isBlank()) {
            String token = normalise(lastSegment(sourceRef.get()));
            AzureResource hit = matchBySuffix(token);
            if (hit != null) return new Match(hit, Confidence.MATCHED);
        }
        String name = unit.name();
        if (name != null && !name.isBlank()) {
            String token = normalise(name);
            AzureResource hit = matchBySuffix(token);
            if (hit != null) return new Match(hit, Confidence.GUESSED);
        }
        return new Match(AzureResourceCatalog.generic(), Confidence.UNKNOWN);
    }

    /**
     * Best-effort match by a bare name or source-ref string — used for Module / ExternalModule
     * cards that aren't backed by a full {@link Unit}.
     */
    public static Match inferByName(String nameOrSourceRef) {
        if (nameOrSourceRef == null || nameOrSourceRef.isBlank()) {
            return new Match(AzureResourceCatalog.generic(), Confidence.UNKNOWN);
        }
        String token = normalise(lastSegment(nameOrSourceRef));
        AzureResource hit = matchBySuffix(token);
        if (hit != null) return new Match(hit, Confidence.MATCHED);
        return new Match(AzureResourceCatalog.generic(), Confidence.UNKNOWN);
    }

    static String lastSegment(String sourceRef) {
        String s = sourceRef;
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        if (s.endsWith(".git")) s = s.substring(0, s.length() - 4);
        // For tfr:// registry refs strip trailing provider segment (e.g. "azurerm")
        if (s.startsWith("tfr://")) {
            String path = s.replaceFirst("^tfr://[^/]*/", "");
            String[] parts = path.split("/");
            // path is like "Azure/cosmosdb/azurerm" — pick the module name (last non-provider part)
            if (parts.length >= 2) return parts[parts.length - 2];
            if (parts.length == 1) return parts[0];
        }
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        return s;
    }

    static String normalise(String raw) {
        String s = raw.toLowerCase();
        s = s.replace('_', '-').replace('/', '-');
        StringBuilder sb = new StringBuilder(s.length());
        char prev = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '-' && prev == '-') continue;
            sb.append(c);
            prev = c;
        }
        String out = sb.toString();
        while (out.startsWith("-")) out = out.substring(1);
        while (out.endsWith("-")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private static AzureResource matchBySuffix(String token) {
        // Find the longest keyword that matches a dash-delimited segment sequence within token.
        // A keyword matches if: token equals kw, token starts with kw+"-", token ends with "-"+kw,
        // or token contains "-"+kw+"-". The longest (most specific) match wins.
        AzureResource best = null;
        int bestLen = -1;
        for (AzureResource r : AzureResourceCatalog.all()) {
            for (String kw : r.keywords()) {
                boolean matches = token.equals(kw)
                    || token.startsWith(kw + "-")
                    || token.endsWith("-" + kw)
                    || token.contains("-" + kw + "-");
                if (matches && kw.length() > bestLen) {
                    bestLen = kw.length();
                    best = r;
                }
            }
        }
        return best;
    }
}

package solutions.onz.toolbox.gruntface.azure;

import java.util.List;

public record AzureResource(
    String id,
    String displayName,
    String iconResourcePath,
    List<String> keywords
) {
    public AzureResource {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName required");
        if (iconResourcePath == null || iconResourcePath.isBlank()) throw new IllegalArgumentException("iconResourcePath required");
        keywords = List.copyOf(keywords);
    }
}

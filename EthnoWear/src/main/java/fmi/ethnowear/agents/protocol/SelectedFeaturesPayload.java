package fmi.ethnowear.agents.protocol;

import java.util.List;

public record SelectedFeaturesPayload(
        List<String> ornaments,
        List<String> colors,
        List<String> techniques,
        String motif,
        String regionHint,
        String regionalEmbroideryHint
) {
}

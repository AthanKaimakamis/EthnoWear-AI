package fmi.ethnowear.api.dto.reference;

import java.util.List;
import java.util.Map;

public record ReferenceResponse(
        String language,
        List<ReferenceItemDto> regions,
        List<ReferenceItemDto> regionGroups,
        List<ReferenceItemDto> ornaments,
        List<ReferenceItemDto> ornamentTypes,
        List<ReferenceItemDto> colors,
        List<ReferenceItemDto> techniques,
        List<ReferenceItemDto> techniqueTypes,
        List<ReferenceItemDto> motifs,
        List<ReferenceItemDto> regionalEmbroideryTypes,
        Map<String, List<String>> regionsByRegionGroup,
        Map<String, String> regionByRegionalEmbroidery,
        Map<String, List<String>> ornamentsByRegion,
        Map<String, List<String>> techniquesByRegion,
        Map<String, List<String>> ornamentsByType,
        Map<String, List<String>> techniquesByType
) {
}

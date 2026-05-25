package fmi.ethnowear.api.dto.reference;

import java.util.List;

public record ReferenceResponse(
        String language,
        List<ReferenceItemDto> regions,
        List<ReferenceItemDto> regionGroups,
        List<ReferenceItemDto> ornaments,
        List<ReferenceItemDto> colors,
        List<ReferenceItemDto> techniques,
        List<ReferenceItemDto> motifs,
        List<ReferenceItemDto> regionalEmbroideryTypes
) {
}

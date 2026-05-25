package fmi.ethnowear.api.dto.reference;

import java.util.List;

public record ReferenceItemDto(
        String iri,
        String localName,
        String label,
        List<String> altLabels,
        String comment
) {
}

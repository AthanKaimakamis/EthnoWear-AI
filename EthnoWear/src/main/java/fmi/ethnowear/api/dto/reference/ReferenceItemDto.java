package fmi.ethnowear.api.dto.reference;

import java.util.List;
import java.util.Map;

public record ReferenceItemDto(
        String iri,
        String localName,
        String label,
        List<String> altLabels,
        String comment,
        Map<String, String> labels,
        Map<String, List<String>> altLabelsByLanguage,
        Map<String, String> comments
) {
}

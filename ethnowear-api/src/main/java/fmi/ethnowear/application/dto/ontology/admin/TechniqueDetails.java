package fmi.ethnowear.application.dto.ontology.admin;

import java.util.List;
import java.util.Set;

public record TechniqueDetails(
        String iri,
        String localName,
        Set<String> typeLocalNames,
        String labelBg,
        String labelEn,
        List<String> altLabelsBg,
        List<String> altLabelsEn,
        String commentBg,
        String commentEn,
        Set<String> characteristicRegionLocalNames
) {
}

package fmi.ethnowear.api.dto.catalogue;

import fmi.ethnowear.application.enums.FeatureType;

import java.util.List;
import java.util.Map;

public record EntityOntologyDetails(
        FeatureType entityType,
        String iri,
        String localName,
        String label,
        List<String> altLabels,
        String comment,
        String language,
        List<CategoryLinkDetails> categories,
        Map<FeatureType, List<EntityLinkDetails>> relatedEntities
) {
}

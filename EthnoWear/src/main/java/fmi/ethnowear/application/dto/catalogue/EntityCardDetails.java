package fmi.ethnowear.application.dto.catalogue;

import fmi.ethnowear.domain.model.ontology.FeatureType;

import java.util.List;

public record EntityCardDetails(
        FeatureType entityType,
        String iri,
        String localName,
        String label,
        String comment,
        List<CategoryLinkDetails> categories
) {
}

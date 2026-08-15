package fmi.ethnowear.application.dto.catalogue;

import fmi.ethnowear.domain.model.ontology.FeatureType;

public record EntityLinkDetails(
        FeatureType entityType,
        String iri,
        String localName,
        String label
) {
}

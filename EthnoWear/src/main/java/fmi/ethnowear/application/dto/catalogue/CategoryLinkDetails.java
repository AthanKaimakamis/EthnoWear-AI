package fmi.ethnowear.application.dto.catalogue;

import fmi.ethnowear.domain.model.ontology.FeatureType;

public record CategoryLinkDetails(
        FeatureType targetEntityType,
        String iri,
        String localName,
        String label
) {
}

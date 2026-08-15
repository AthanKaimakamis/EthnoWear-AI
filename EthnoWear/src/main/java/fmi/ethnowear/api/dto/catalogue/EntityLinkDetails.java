package fmi.ethnowear.api.dto.catalogue;

import fmi.ethnowear.application.enums.FeatureType;

public record EntityLinkDetails(
        FeatureType entityType,
        String iri,
        String localName,
        String label
) {
}

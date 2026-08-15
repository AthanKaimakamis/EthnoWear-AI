package fmi.ethnowear.api.dto.catalogue;

import fmi.ethnowear.application.enums.FeatureType;

public record CategoryLinkDetails(
        FeatureType targetEntityType,
        String iri,
        String localName,
        String label
) {
}

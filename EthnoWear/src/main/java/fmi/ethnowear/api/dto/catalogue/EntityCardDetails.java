package fmi.ethnowear.api.dto.catalogue;

import fmi.ethnowear.application.enums.FeatureType;

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

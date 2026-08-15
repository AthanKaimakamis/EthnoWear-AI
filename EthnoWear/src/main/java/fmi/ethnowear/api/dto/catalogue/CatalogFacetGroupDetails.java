package fmi.ethnowear.api.dto.catalogue;

import fmi.ethnowear.application.enums.CatalogFacetType;
import fmi.ethnowear.application.enums.FeatureType;

import java.util.List;

public record CatalogFacetGroupDetails(
        CatalogFacetType facetType,
        FeatureType entityType,
        List<CatalogFacetValueDetails> values
) {
}

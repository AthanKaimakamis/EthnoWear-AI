package fmi.ethnowear.application.dto.catalogue;

import fmi.ethnowear.domain.model.catalogue.CatalogFacetType;
import fmi.ethnowear.domain.model.ontology.FeatureType;

import java.util.List;

public record CatalogFacetGroupDetails(
        CatalogFacetType facetType,
        FeatureType entityType,
        List<CatalogFacetValueDetails> values
) {
}

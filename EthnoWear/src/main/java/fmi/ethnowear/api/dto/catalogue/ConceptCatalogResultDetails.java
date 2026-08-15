package fmi.ethnowear.api.dto.catalogue;

import java.util.List;

public record ConceptCatalogResultDetails(
        List<EntityCardDetails> items,
        PageMetadataDetails page,
        List<CatalogFacetGroupDetails> facets
) {
}

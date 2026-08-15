package fmi.ethnowear.api.dto.catalogue;

public record CatalogFacetValueDetails(
        String iri,
        String localName,
        String label,
        long count,
        boolean selected
) {
}

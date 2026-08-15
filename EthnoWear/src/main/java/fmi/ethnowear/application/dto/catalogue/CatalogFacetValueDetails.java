package fmi.ethnowear.application.dto.catalogue;

public record CatalogFacetValueDetails(
        String iri,
        String localName,
        String label,
        long count,
        boolean selected
) {
}

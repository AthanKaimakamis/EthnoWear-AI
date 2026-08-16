import type { CatalogFacetType, ConceptCatalogResultDetails, OntologyFeatureType } from '../../../types/catalogue'
import type { ReferenceResource } from '../../../types/reference'

export function catalogueFacetOptions(
    catalogue: ConceptCatalogResultDetails | null,
    facetType: CatalogFacetType,
    entityType: OntologyFeatureType,
): ReferenceResource[] {
    return catalogue?.facets
        .find(facet => facet.facetType === facetType && facet.entityType === entityType)
        ?.values
        .filter(value => value.selected || value.count > 0)
        .map(value => ({ iri: value.iri, localName: value.localName, label: value.label })) ?? []
}

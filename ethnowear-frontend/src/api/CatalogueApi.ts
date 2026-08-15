import { apiRequest } from './http'
import type { PageRequest } from '../types/api'
import type {
    ConceptCatalogQuery,
    ConceptCatalogResultDetails,
    EntityDetailDetails,
    OntologyFeatureType,
} from '../types/catalogue'

export function searchCatalogue(query: ConceptCatalogQuery, page: PageRequest = {}, signal?: AbortSignal) {
    return apiRequest<ConceptCatalogResultDetails>('/api/catalogue/search', {
        method: 'POST',
        query: page,
        body: query,
        signal,
    })
}

export function getEntityDetails(
    entityType: OntologyFeatureType,
    localName: string,
    language = 'bg',
    page: PageRequest = {},
    signal?: AbortSignal,
) {
    return apiRequest<EntityDetailDetails>(
        `/api/catalogue/${entityType}/${encodeURIComponent(localName)}`,
        {
            query: { language, ...page },
            signal,
        },
    )
}

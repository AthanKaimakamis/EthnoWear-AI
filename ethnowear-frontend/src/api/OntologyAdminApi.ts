import { apiRequest } from './http.ts'
import type { OntologyEntity, OntologyEntityInput, OntologyEntityType } from '../types/ontologyAdmin.ts'

const basePath = '/api/admin/ontology'

export function listOntologyEntities(type: OntologyEntityType, signal?: AbortSignal) {
    return apiRequest<OntologyEntity[]>(`${basePath}/${type}`, { signal })
}

export function createOntologyEntity(type: OntologyEntityType, input: OntologyEntityInput) {
    return apiRequest<OntologyEntity>(`${basePath}/${type}`, { method: 'POST', body: input })
}

export function updateOntologyEntity(
    type: OntologyEntityType,
    localName: string,
    input: OntologyEntityInput,
) {
    return apiRequest<OntologyEntity>(`${basePath}/${type}/${encodeURIComponent(localName)}`, {
        method: 'PUT',
        body: input,
    })
}

export function deleteOntologyEntity(type: OntologyEntityType, localName: string) {
    return apiRequest<void>(`${basePath}/${type}/${encodeURIComponent(localName)}`, { method: 'DELETE' })
}

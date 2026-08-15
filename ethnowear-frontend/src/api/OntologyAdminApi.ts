import { apiRequest } from './http.ts'
import type { OntologyEntity, OntologyEntityInput, OntologyEntityType } from '../types/ontologyAdmin.ts'

const basePath = '/api/admin/ontology'

type CharacteristicRegionEntityType = Extract<OntologyEntityType, 'ornaments' | 'techniques'>

function entityPath(type: OntologyEntityType, localName?: string) {
    const typePath = `${basePath}/${type}`
    return localName === undefined ? typePath : `${typePath}/${encodeURIComponent(localName)}`
}

export function listOntologyEntities(type: OntologyEntityType, signal?: AbortSignal) {
    return apiRequest<OntologyEntity[]>(entityPath(type), { signal })
}

export function getOntologyEntity(
    type: OntologyEntityType,
    localName: string,
    signal?: AbortSignal,
) {
    return apiRequest<OntologyEntity>(entityPath(type, localName), { signal })
}

export function createOntologyEntity(type: OntologyEntityType, input: OntologyEntityInput) {
    return apiRequest<OntologyEntity>(entityPath(type), { method: 'POST', body: input })
}

export function updateOntologyEntity(
    type: OntologyEntityType,
    localName: string,
    input: OntologyEntityInput,
) {
    return apiRequest<OntologyEntity>(entityPath(type, localName), {
        method: 'PUT',
        body: input,
    })
}

export function deleteOntologyEntity(type: OntologyEntityType, localName: string) {
    return apiRequest<void>(entityPath(type, localName), { method: 'DELETE' })
}

export function addCharacteristicRegion(
    type: CharacteristicRegionEntityType,
    localName: string,
    regionLocalName: string,
) {
    return apiRequest<OntologyEntity>(
        `${entityPath(type, localName)}/characteristic-regions/${encodeURIComponent(regionLocalName)}`,
        { method: 'PUT' },
    )
}

export function removeCharacteristicRegion(
    type: CharacteristicRegionEntityType,
    localName: string,
    regionLocalName: string,
) {
    return apiRequest<OntologyEntity>(
        `${entityPath(type, localName)}/characteristic-regions/${encodeURIComponent(regionLocalName)}`,
        { method: 'DELETE' },
    )
}

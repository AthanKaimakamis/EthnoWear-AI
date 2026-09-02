import { adminAuthorizationHeaders, apiRequest, apiUrl, ApiError, handleAdminResponseStatus } from './http.ts'
import type { PageRequest, PageResponse } from '../types/api.ts'
import type { OntologyEntity, OntologyEntityInput, OntologyEntityType, OntologyVersion } from '../types/ontologyAdmin.ts'

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

function changeReasonHeaders(changeReason?: string) {
    const reason = changeReason?.trim()
    return reason ? { 'X-Ontology-Change-Reason': reason } : undefined
}

export function createOntologyEntity(type: OntologyEntityType, input: OntologyEntityInput, changeReason?: string) {
    return apiRequest<OntologyEntity>(entityPath(type), { method: 'POST', body: input, headers: changeReasonHeaders(changeReason) })
}

export function updateOntologyEntity(
    type: OntologyEntityType,
    localName: string,
    input: OntologyEntityInput,
    changeReason?: string,
) {
    return apiRequest<OntologyEntity>(entityPath(type, localName), {
        method: 'PUT',
        body: input,
        headers: changeReasonHeaders(changeReason),
    })
}

export const ontologyVersionQueryKeys = {
    all: ['admin', 'ontology', 'versions'] as const,
    list: (page: number, size: number) => ['admin', 'ontology', 'versions', 'list', page, size] as const,
    detail: (id: number) => ['admin', 'ontology', 'versions', 'detail', id] as const,
    latest: () => ['admin', 'ontology', 'versions', 'latest'] as const,
    content: (id: number | 'latest') => ['admin', 'ontology', 'versions', 'content', id] as const,
}

export function listOntologyVersions(page: PageRequest = {}, signal?: AbortSignal) {
    return apiRequest<PageResponse<OntologyVersion>>(`${basePath}/versions`, { query: page, signal })
}

export function getOntologyVersion(versionId: number, signal?: AbortSignal) {
    return apiRequest<OntologyVersion>(`${basePath}/versions/${versionId}`, { signal })
}

export function getLatestOntologyVersion(signal?: AbortSignal) {
    return apiRequest<OntologyVersion>(`${basePath}/versions/latest`, { signal })
}

export async function getOntologyVersionContent(versionId: number | 'latest', signal?: AbortSignal) {
    const response = await fetch(apiUrl(`${basePath}/versions/${versionId}/content`), {
        headers: adminAuthorizationHeaders({ Accept: 'application/rdf+xml, text/turtle, application/owl+xml, text/plain, */*' }),
        signal,
    })
    if (!response.ok) {
        handleAdminResponseStatus(response.status)
        const details = response.headers.get('content-type')?.includes('application/json') ? await response.json() : await response.text()
        throw new ApiError(`Request failed with status ${response.status}`, response.status, details)
    }
    return response.blob()
}

export function restoreOntologyVersion(versionId: number, reason: string) {
    return apiRequest<OntologyVersion>(`${basePath}/versions/${versionId}/restore`, {
        method: 'POST',
        body: { reason: reason.trim() },
    })
}

export function deleteOntologyEntity(type: OntologyEntityType, localName: string) {
    return apiRequest<void>(entityPath(type, localName), { method: 'DELETE' })
}

export function synchronizeDerivedRegionTypes() {
    return apiRequest<{ created: number }>(`${basePath}/regions/synchronize-derived-types`, {
        method: 'POST',
    })
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

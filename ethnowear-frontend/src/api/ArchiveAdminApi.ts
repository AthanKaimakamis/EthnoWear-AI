import { createCrudApi } from './CrudApi'
import type {
    ArchiveItemDetails,
    ArchiveItemDetailDetails,
    ArchiveEntryDetails,
    ArchiveEntryWriteDto,
    ArchiveItemFeatureDetails,
    ArchiveItemFeatureWriteDto,
    ArchiveItemMediaDetails,
    ArchiveItemMediaWriteDto,
    ArchiveItemWriteDto,
    KnowledgeChunkDetails,
    KnowledgeChunkWriteDto,
    MediaAssetDetails,
    MediaAssetWriteDto,
    MediaEntityLinkDetails,
    MediaEntityLinkWriteDto,
    MediaUploadRequest,
    MediaFeatureAnnotationDetails,
    MediaFeatureAnnotationWriteDto,
    SourceDetails,
    SourceReferenceDetails,
    SourceReferenceWriteDto,
    SourceWriteDto,
    PublicationReadinessDetails,
    PublicationReadinessApiResponse,
} from '../types/archive'
import { apiRequest, apiUrl, ApiError } from './http'
import { getAdminAuthorization } from '../app/adminAuthStore'

export const archiveItemsApi = createCrudApi<ArchiveItemWriteDto, ArchiveItemDetails>(
    '/api/admin/archive-items',
)

export const archiveItemFeaturesApi = createCrudApi<
    ArchiveItemFeatureWriteDto,
    ArchiveItemFeatureDetails
>('/api/admin/archive-item-features')

export const archiveItemMediaApi = createCrudApi<
    ArchiveItemMediaWriteDto,
    ArchiveItemMediaDetails
>('/api/admin/archive-item-media')

export const knowledgeChunksApi = createCrudApi<KnowledgeChunkWriteDto, KnowledgeChunkDetails>(
    '/api/admin/knowledge-chunks',
)

export const mediaAssetsApi = createCrudApi<MediaAssetWriteDto, MediaAssetDetails>(
    '/api/admin/media-assets',
)

export const mediaEntityLinksApi = createCrudApi<MediaEntityLinkWriteDto, MediaEntityLinkDetails>(
    '/api/admin/media-entity-links',
)

export async function uploadMediaAsset(file: File, metadata: MediaUploadRequest) {
    const body = new FormData()
    body.append('file', file)
    body.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }))
    const authorization = getAdminAuthorization()
    const response = await fetch(apiUrl('/api/admin/media-assets/upload'), {
        method: 'POST',
        headers: authorization ? { Authorization: authorization } : undefined,
        body,
    })
    const data = response.headers.get('content-type')?.includes('application/json')
        ? await response.json()
        : await response.text()
    if (!response.ok) throw new ApiError(`Request failed with status ${response.status}`, response.status, data)
    return data as MediaAssetDetails
}

export const mediaFeatureAnnotationsApi = createCrudApi<
    MediaFeatureAnnotationWriteDto,
    MediaFeatureAnnotationDetails
>('/api/admin/media-feature-annotations')

export const sourcesApi = createCrudApi<SourceWriteDto, SourceDetails>('/api/admin/sources')

export const sourceReferencesApi = createCrudApi<
    SourceReferenceWriteDto,
    SourceReferenceDetails
>('/api/admin/source-references')

// Preserve the original archive-item functions while new code adopts the typed client object.
export const listArchiveItems = archiveItemsApi.findAll
export const getArchiveItem = archiveItemsApi.findById
export const createArchiveItem = archiveItemsApi.create
export const updateArchiveItem = archiveItemsApi.update
export const deleteArchiveItem = archiveItemsApi.remove

export function createFullArchiveEntry(input: ArchiveEntryWriteDto) {
    return apiRequest<ArchiveEntryDetails>('/api/admin/archive-items/full', {
        method: 'POST',
        body: input,
    })
}

export function updateFullArchiveEntry(id: number, input: ArchiveEntryWriteDto) {
    return apiRequest<ArchiveEntryDetails>(`/api/admin/archive-items/${id}/full`, {
        method: 'PUT',
        body: input,
    })
}

export function getAdminArchiveItemDetail(id: number, signal?: AbortSignal) {
    return apiRequest<ArchiveItemDetailDetails>(`/api/admin/archive-items/${id}/detail`, { signal })
}

export function getPublicationReadiness(id: number, signal?: AbortSignal) {
    return apiRequest<PublicationReadinessApiResponse | PublicationReadinessDetails>(
        `/api/admin/archive-items/${id}/publication-readiness`,
        { signal },
    ).then(normalizePublicationReadiness)
}

export function normalizePublicationReadiness(
    response: PublicationReadinessApiResponse | PublicationReadinessDetails,
): PublicationReadinessDetails {
    if ('requirements' in response) return response
    return {
        archiveItemId: response.archiveItemId,
        publicationStatus: response.publicationStatus,
        ready: response.ready,
        requirements: response.checks.map(check => ({
            key: check.requirement,
            satisfied: check.satisfied,
            severity: check.blocking ? 'ERROR' : 'WARNING',
            message: check.message ?? null,
            field: check.field ?? null,
        })),
    }
}

export type PublicationCommand = 'submit' | 'publish' | 'return-to-draft' | 'archive'

export function runPublicationCommand(id: number, command: PublicationCommand) {
    return apiRequest<ArchiveItemDetails>(`/api/admin/archive-items/${id}/${command}`, {
        method: 'POST',
    })
}

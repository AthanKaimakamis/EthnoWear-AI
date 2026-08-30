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
    EntitySourceCitationDetails,
    DocumentMediaLinkDetails,
} from '../types/archive'
import {
    adminAuthorizationHeaders,
    apiRequest,
    apiUrl,
    ApiError,
    handleAdminResponseStatus,
} from './http'

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

export function findDocumentMediaLinks(mediaAssetIds: number[], signal?: AbortSignal) {
    if (mediaAssetIds.length === 0) return Promise.resolve([] as DocumentMediaLinkDetails[])
    return apiRequest<DocumentMediaLinkDetails[]>('/api/admin/media-assets/document-links', {
        query: { mediaAssetIds: mediaAssetIds.join(',') },
        signal,
    })
}

export const mediaEntityLinksApi = createCrudApi<MediaEntityLinkWriteDto, MediaEntityLinkDetails>(
    '/api/admin/media-entity-links',
)

export async function uploadMediaAsset(file: File, metadata: MediaUploadRequest) {
    const body = new FormData()
    body.append('file', file)
    body.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }))
    const response = await fetch(apiUrl('/api/admin/media-assets/upload'), {
        method: 'POST',
        headers: adminAuthorizationHeaders({ Accept: 'application/json' }),
        body,
    })
    const data = response.headers.get('content-type')?.includes('application/json')
        ? await response.json()
        : await response.text()
    if (!response.ok) {
        handleAdminResponseStatus(response.status)
        throw new ApiError(`Request failed with status ${response.status}`, response.status, data)
    }
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
    return apiRequest<ArchiveEntryDetails>('/api/admin/archive-entries', {
        method: 'POST',
        body: input,
    })
}

export function updateFullArchiveEntry(id: number, input: ArchiveEntryWriteDto) {
    return apiRequest<ArchiveEntryDetails>(`/api/admin/archive-entries/${id}`, {
        method: 'PUT',
        body: input,
    })
}

export async function getAdminArchiveItemDetail(id: number, signal?: AbortSignal): Promise<ArchiveItemDetailDetails> {
    const entry = await apiRequest<ArchiveEntryDetails>(`/api/admin/archive-entries/${id}`, { signal })
    const { archiveItem, features, media: mediaLinks } = entry
    const reference = await sourceReferencesApi.findById(archiveItem.sourceReferenceId, signal)
    const [source, assets] = await Promise.all([
        sourcesApi.findById(reference.sourceId, signal),
        Promise.all(mediaLinks.map(link => mediaAssetsApi.findById(link.mediaAssetId, signal))),
    ])

    return {
        archiveItem,
        source: sourceCitation(reference, source),
        features,
        media: mediaLinks.map((media, index) => ({ media, asset: assets[index], annotations: [] })),
    }
}

function sourceCitation(reference: SourceReferenceDetails, source: SourceDetails): EntitySourceCitationDetails {
    return {
        ...reference,
        sourceReferenceId: reference.id,
        sourceId: source.id,
        title: source.title,
        author: source.author,
        publisher: source.publisher,
        year: source.year,
        sourceType: source.sourceType,
        sourceLanguage: source.language,
        filePath: source.filePath,
        url: source.url,
        isbn: source.isbn,
        trusted: source.trusted,
    }
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

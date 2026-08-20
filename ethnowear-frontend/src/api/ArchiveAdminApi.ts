import { createCrudApi, type CrudApi } from './CrudApi'
import type { IdentifiableDto } from '../types/api'
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
    return createArchiveEntry(input)
}

export function updateFullArchiveEntry(id: number, input: ArchiveEntryWriteDto) {
    return updateArchiveEntryAggregate(id, input)
}

export async function getAdminArchiveItemDetail(id: number, signal?: AbortSignal): Promise<ArchiveItemDetailDetails> {
    const archiveItem = await archiveItemsApi.findById(id, signal)
    const [allFeatures, allMedia, reference] = await Promise.all([
        findAllRecords(archiveItemFeaturesApi, signal),
        findAllRecords(archiveItemMediaApi, signal),
        sourceReferencesApi.findById(archiveItem.sourceReferenceId, signal),
    ])
    const features = allFeatures.filter(feature => feature.archiveItemId === id)
    const mediaLinks = allMedia.filter(link => link.archiveItemId === id)
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

async function createArchiveEntry(input: ArchiveEntryWriteDto): Promise<ArchiveEntryDetails> {
    const archiveItem = await archiveItemsApi.create(input.archiveItem)
    try {
        const features = await createFeatures(archiveItem.id, input.features)
        const media = await createMedia(archiveItem.id, input.media)
        return { archiveItem, features, media }
    } catch (error) {
        await cleanupCreatedEntry(archiveItem.id)
        throw error
    }
}

async function updateArchiveEntryAggregate(id: number, input: ArchiveEntryWriteDto): Promise<ArchiveEntryDetails> {
    const archiveItem = await archiveItemsApi.update(id, input.archiveItem)
    const [existingFeatures, existingMedia] = await Promise.all([
        findAllRecords(archiveItemFeaturesApi).then(records => records.filter(record => record.archiveItemId === id)),
        findAllRecords(archiveItemMediaApi).then(records => records.filter(record => record.archiveItemId === id)),
    ])
    const features = await syncFeatures(id, existingFeatures, input.features)
    const media = await syncMedia(id, existingMedia, input.media)
    return { archiveItem, features, media }
}

async function createFeatures(id: number, features: ArchiveEntryWriteDto['features']) {
    const created: ArchiveItemFeatureDetails[] = []
    for (const feature of features) {
        created.push(await archiveItemFeaturesApi.create({ ...feature, archiveItemId: id }))
    }
    return created
}

async function createMedia(id: number, media: ArchiveEntryWriteDto['media']) {
    const created: ArchiveItemMediaDetails[] = []
    for (const link of media) {
        created.push(await archiveItemMediaApi.create({ ...link, archiveItemId: id }))
    }
    return created
}

async function syncFeatures(
    id: number,
    existing: ArchiveItemFeatureDetails[],
    desired: ArchiveEntryWriteDto['features'],
) {
    const existingByKey = new Map(existing.map(feature => [featureKey(feature), feature]))
    const result: ArchiveItemFeatureDetails[] = []

    for (const feature of desired) {
        const key = featureKey(feature)
        const current = existingByKey.get(key)
        const write = { ...feature, archiveItemId: id }
        result.push(current
            ? await archiveItemFeaturesApi.update(current.id, write)
            : await archiveItemFeaturesApi.create(write))
        existingByKey.delete(key)
    }
    for (const removed of existingByKey.values()) await archiveItemFeaturesApi.remove(removed.id)
    return result
}

async function syncMedia(
    id: number,
    existing: ArchiveItemMediaDetails[],
    desired: ArchiveEntryWriteDto['media'],
) {
    const existingByAsset = new Map(existing.map(link => [link.mediaAssetId, link]))
    const result: ArchiveItemMediaDetails[] = []

    for (const link of desired) {
        const current = existingByAsset.get(link.mediaAssetId)
        const write = { ...link, archiveItemId: id }
        result.push(current
            ? await archiveItemMediaApi.update(current.id, write)
            : await archiveItemMediaApi.create(write))
        existingByAsset.delete(link.mediaAssetId)
    }
    for (const removed of existingByAsset.values()) await archiveItemMediaApi.remove(removed.id)
    return result
}

async function cleanupCreatedEntry(id: number) {
    try {
        const [features, media] = await Promise.all([
            findAllRecords(archiveItemFeaturesApi),
            findAllRecords(archiveItemMediaApi),
        ])
        for (const link of media.filter(candidate => candidate.archiveItemId === id)) {
            await archiveItemMediaApi.remove(link.id)
        }
        for (const feature of features.filter(candidate => candidate.archiveItemId === id)) {
            await archiveItemFeaturesApi.remove(feature.id)
        }
        await archiveItemsApi.remove(id)
    } catch {
        // Cleanup is best-effort because the backend has no aggregate transaction.
    }
}

async function findAllRecords<D extends IdentifiableDto>(
    api: Pick<CrudApi<unknown, D>, 'findAll'>,
    signal?: AbortSignal,
) {
    const records: D[] = []
    let page = 0
    let last = false
    while (!last) {
        const response = await api.findAll({ page, size: 200 }, signal)
        records.push(...response.content)
        last = response.last || page + 1 >= response.totalPages
        page += 1
    }
    return records
}

function featureKey(feature: Pick<ArchiveItemFeatureWriteDto, 'featureType' | 'ontologyIri' | 'ontologyLocalName'>) {
    return `${feature.featureType}\u0000${feature.ontologyIri}\u0000${feature.ontologyLocalName}`
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

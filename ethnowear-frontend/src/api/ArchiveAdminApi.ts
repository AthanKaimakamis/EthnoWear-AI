import { createCrudApi } from './CrudApi'
import type {
    ArchiveItemDetails,
    ArchiveItemFeatureDetails,
    ArchiveItemFeatureWriteDto,
    ArchiveItemMediaDetails,
    ArchiveItemMediaWriteDto,
    ArchiveItemWriteDto,
    KnowledgeChunkDetails,
    KnowledgeChunkWriteDto,
    MediaAssetDetails,
    MediaAssetWriteDto,
    MediaFeatureAnnotationDetails,
    MediaFeatureAnnotationWriteDto,
    SourceDetails,
    SourceReferenceDetails,
    SourceReferenceWriteDto,
    SourceWriteDto,
} from '../types/archive'

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

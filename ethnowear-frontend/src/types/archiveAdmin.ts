import type { CrudApi } from '../api/CrudApi'
import type { PageResponse } from './api'
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
} from './archive'

export type ArchiveAdminResource =
    | 'archive-items'
    | 'sources'
    | 'source-references'
    | 'archive-item-features'
    | 'media-assets'
    | 'archive-item-media'
    | 'media-feature-annotations'
    | 'knowledge-chunks'

export type ArchiveAdminRecord =
    | ArchiveItemDetails
    | SourceDetails
    | SourceReferenceDetails
    | ArchiveItemFeatureDetails
    | MediaAssetDetails
    | ArchiveItemMediaDetails
    | MediaFeatureAnnotationDetails
    | KnowledgeChunkDetails

export type ArchiveAdminWriteDto =
    | ArchiveItemWriteDto
    | SourceWriteDto
    | SourceReferenceWriteDto
    | ArchiveItemFeatureWriteDto
    | MediaAssetWriteDto
    | ArchiveItemMediaWriteDto
    | MediaFeatureAnnotationWriteDto
    | KnowledgeChunkWriteDto

export type ArchiveAdminApi = {
    findAll: (signal?: AbortSignal) => Promise<PageResponse<ArchiveAdminRecord>>
    create: (input: ArchiveAdminWriteDto) => Promise<ArchiveAdminRecord>
    update: (id: number, input: ArchiveAdminWriteDto) => Promise<ArchiveAdminRecord>
    remove: (id: number) => Promise<void>
}

export function archiveAdminApi<W, D extends ArchiveAdminRecord>(api: CrudApi<W, D>): ArchiveAdminApi {
    return {
        findAll: signal => api.findAll({ size: 100, sort: 'id,asc' }, signal),
        create: input => api.create(input as W),
        update: (id, input) => api.update(id, input as W),
        remove: api.remove,
    }
}

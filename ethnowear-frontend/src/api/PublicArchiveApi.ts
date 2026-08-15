import { apiRequest, apiUrl } from './http'
import type {
    ArchiveItemDetailDetails,
    RegionalEmbroideryArchiveOverviewDetails,
} from '../types/archive'
import type { Language } from '../types/reference'

export function getRegionalEmbroideryArchive(
    language: Language = 'bg',
    previewSize = 4,
    signal?: AbortSignal,
) {
    return apiRequest<RegionalEmbroideryArchiveOverviewDetails>(
        '/api/archive/regional-embroideries',
        { query: { language, previewSize }, signal },
    )
}

export function getArchiveItemDetails(id: number, signal?: AbortSignal) {
    return apiRequest<ArchiveItemDetailDetails>(`/api/archive/items/${id}`, { signal })
}

export function archiveMediaUrl(mediaAssetId: number) {
    return apiUrl(`/api/archive/media/${mediaAssetId}`)
}

import type { ArchiveItemWriteDto } from '../../../types/archive'
import type { MediaDraft } from './ArchiveEditorSections'

export function archiveMediaDefaults(item: ArchiveItemWriteDto, media: MediaDraft[]): ArchiveItemWriteDto {
    const citations = new Set(media.map(link => link.asset.sourceReferenceId))
    const citation = citations.size === 1 ? [...citations][0] : null
    return { ...item, sourceReferenceId: item.sourceReferenceId || citation || 0 }
}

export function primaryCaption(media: MediaDraft[], language: 'bg' | 'en') {
    const primary = media.find(link => link.role === 'PRIMARY') ?? media[0]
    return (language === 'bg' ? primary?.captionBg : primary?.captionEn)?.trim() || ''
}

import type { ArchiveEntryWriteDto, ArchiveItemFeatureDetails, ArchiveItemWriteDto } from '../../../types/archive'
import type { FeatureSelections, MediaDraft } from './ArchiveEditorSections'

export function buildArchiveEntryPayload(
    item: ArchiveItemWriteDto,
    features: FeatureSelections,
    savedFeatures: ArchiveItemFeatureDetails[],
    media: MediaDraft[],
): ArchiveEntryWriteDto {
    return {
        archiveItem: {
            collectionId: item.collectionId, inventoryNumber: item.inventoryNumber,
            archiveType: item.archiveType, periodText: item.periodText, originText: item.originText,
            currentLocation: item.currentLocation, trustedLevel: item.trustedLevel,
            ontologyRegionIri: item.ontologyRegionIri, ontologyRegionLocalName: item.ontologyRegionLocalName,
            ontologyRegionalEmbroideryIri: item.ontologyRegionalEmbroideryIri,
            ontologyRegionalEmbroideryLocalName: item.ontologyRegionalEmbroideryLocalName,
            ontologyRegionalMotifIri: item.ontologyRegionalMotifIri,
            ontologyRegionalMotifLocalName: item.ontologyRegionalMotifLocalName,
            sourceReferenceId: item.sourceReferenceId || null,
            titleBg: nullText(item.titleBg), titleEn: nullText(item.titleEn),
            descriptionBg: nullText(item.descriptionBg), descriptionEn: nullText(item.descriptionEn),
        },
        features: [
            // Hidden legacy observations survive editing until an explicit migration.
            ...savedFeatures.filter(feature => feature.featureType === 'MOTIF').map(feature => ({ ...feature })),
            ...Object.entries(features).flatMap(([type, resources]) => resources.map(resource => {
                const featureType = type as keyof FeatureSelections
                const saved = savedFeatures.find(feature => feature.featureType === featureType &&
                    feature.ontologyIri === resource.iri && feature.ontologyLocalName === resource.localName)
                return saved ? { ...saved } : {
                    id: undefined,
                    featureType, ontologyIri: resource.iri, ontologyLocalName: resource.localName,
                    confidence: null, validated: true, notes: null, sourceReferenceId: item.sourceReferenceId || null,
                }
            })),
        ].map(({ id, featureType, ontologyIri, ontologyLocalName, confidence, validated, notes, sourceReferenceId }) => ({
            id, featureType, ontologyIri, ontologyLocalName, confidence, validated, notes, sourceReferenceId,
        })),
        media: media.map(link => ({
            id: link.id, mediaAssetId: link.asset.id, role: link.role,
            captionBg: nullText(link.captionBg), captionEn: nullText(link.captionEn),
        })),
    }
}

function nullText(value: string | null | undefined) {
    return value?.trim() || null
}

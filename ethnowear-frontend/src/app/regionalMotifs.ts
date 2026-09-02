import type { ArchiveItemWriteDto } from '../types/archive'
import type { ReferenceData } from '../types/reference'

export function regionalMotifsForRegion(refs: ReferenceData, regionLocalName: string | null) {
    return (refs.regionalMotifTypes ?? []).filter(resource => refs.regionByRegionalMotif?.[resource.localName] === regionLocalName)
}

export function validateArchiveClassification(item: ArchiveItemWriteDto, reference: ReferenceData | null) {
    const needsRegion = ['ORNAMENT_EXAMPLE', 'TECHNIQUE_EXAMPLE', 'MOTIF_EXAMPLE', 'EMBROIDERY_SAMPLE'].includes(item.archiveType)
    if (needsRegion && !item.ontologyRegionLocalName) return 'curator.validation.region'
    if (item.archiveType === 'MOTIF_EXAMPLE') {
        if (!item.ontologyRegionalMotifLocalName) return 'curator.validation.regionalMotif'
        if (reference?.regionByRegionalMotif[item.ontologyRegionalMotifLocalName] !== item.ontologyRegionLocalName) {
            return 'curator.validation.regionalMotifRegion'
        }
    }
    if (item.archiveType === 'EMBROIDERY_SAMPLE' && !item.ontologyRegionalEmbroideryLocalName) {
        return 'curator.validation.regionalEmbroidery'
    }
    return null
}

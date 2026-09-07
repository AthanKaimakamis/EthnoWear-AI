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
    }
    if (item.archiveType === 'EMBROIDERY_SAMPLE' && !item.ontologyRegionalEmbroideryLocalName) {
        return 'curator.validation.regionalEmbroidery'
    }
    if (item.ontologyRegionalMotifLocalName &&
        reference?.regionByRegionalMotif[item.ontologyRegionalMotifLocalName] !== item.ontologyRegionLocalName) {
        return 'curator.validation.regionalMotifRegion'
    }
    if (item.ontologyRegionalEmbroideryLocalName &&
        reference?.regionByRegionalEmbroidery[item.ontologyRegionalEmbroideryLocalName] !== item.ontologyRegionLocalName) {
        return 'curator.validation.regionalEmbroideryRegion'
    }
    return null
}

export function changeArchiveItemField<K extends keyof ArchiveItemWriteDto>(
    item: ArchiveItemWriteDto, key: K, value: ArchiveItemWriteDto[K], reference: ReferenceData | null,
): ArchiveItemWriteDto {
    const next = { ...item, [key]: value }
    if (key === 'archiveType') {
        if (value !== 'EMBROIDERY_SAMPLE' && value !== 'MOTIF_EXAMPLE') {
            next.ontologyRegionalEmbroideryIri = null
            next.ontologyRegionalEmbroideryLocalName = null
        }
        if (value !== 'MOTIF_EXAMPLE') {
            next.ontologyRegionalMotifIri = null
            next.ontologyRegionalMotifLocalName = null
        }
    }
    if (key === 'ontologyRegionLocalName' || key === 'archiveType') {
        if (!next.ontologyRegionLocalName || reference?.regionByRegionalEmbroidery[next.ontologyRegionalEmbroideryLocalName ?? ''] !== next.ontologyRegionLocalName) {
            next.ontologyRegionalEmbroideryIri = null
            next.ontologyRegionalEmbroideryLocalName = null
        }
        if (!next.ontologyRegionLocalName || reference?.regionByRegionalMotif[next.ontologyRegionalMotifLocalName ?? ''] !== next.ontologyRegionLocalName) {
            next.ontologyRegionalMotifIri = null
            next.ontologyRegionalMotifLocalName = null
        }
    }
    if (reference && (key === 'ontologyRegionLocalName' || key === 'archiveType')) {
        const region = reference.regions.find(resource => resource.localName === next.ontologyRegionLocalName)
        next.ontologyRegionIri = region?.iri ?? null
        if (next.archiveType === 'EMBROIDERY_SAMPLE') {
            const matches = reference.regionalEmbroideryTypes.filter(resource =>
                reference.regionByRegionalEmbroidery[resource.localName] === next.ontologyRegionLocalName)
            next.ontologyRegionalEmbroideryIri = matches.length === 1 ? matches[0].iri : null
            next.ontologyRegionalEmbroideryLocalName = matches.length === 1 ? matches[0].localName : null
        }
        if (next.archiveType === 'MOTIF_EXAMPLE') {
            const matches = regionalMotifsForRegion(reference, next.ontologyRegionLocalName)
            next.ontologyRegionalMotifIri = matches.length === 1 ? matches[0].iri : null
            next.ontologyRegionalMotifLocalName = matches.length === 1 ? matches[0].localName : null
        }
    }
    return next
}

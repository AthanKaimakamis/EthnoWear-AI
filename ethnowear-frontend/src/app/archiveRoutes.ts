import type { OntologyFeatureType } from '../types/catalogue'

const routeSegmentByType: Record<OntologyFeatureType, string> = {
    REGIONAL_EMBROIDERY: 'embroideries',
    REGIONAL_MOTIF: 'motifs',
    REGION: 'regions',
    MOTIF: 'motif-concepts',
    ORNAMENT: 'ornaments',
    TECHNIQUE: 'techniques',
    COLOR: 'colors',
}

const typeByRouteSegment = Object.fromEntries(
    Object.entries(routeSegmentByType).map(([type, segment]) => [segment, type]),
) as Record<string, OntologyFeatureType>

export function conceptPath(entityType: OntologyFeatureType, localName: string) {
    return `/archive/${routeSegmentByType[entityType]}/${encodeURIComponent(localName)}`
}

export function conceptCollectionPath(entityType: OntologyFeatureType) {
    return `/archive/${routeSegmentByType[entityType]}`
}

export function archiveItemPath(id: number) {
    return `/archive/items/${id}`
}

export function featureTypeFromRouteSegment(segment: string | undefined) {
    return segment ? typeByRouteSegment[segment] ?? null : null
}

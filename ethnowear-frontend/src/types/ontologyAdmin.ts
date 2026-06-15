export type OntologyEntityType =
    | 'ornaments'
    | 'techniques'
    | 'motifs'
    | 'regions'
    | 'regional-embroideries'

export type OntologyEntity = {
    iri: string
    localName: string
    labelBg: string | null
    labelEn: string | null
    altLabelsBg: string[]
    altLabelsEn: string[]
    commentBg: string | null
    commentEn: string | null
    typeLocalNames?: string[]
    characteristicRegionLocalNames?: string[]
    regionGroupLocalName?: string | null
    regionLocalName?: string | null
    ornamentLocalNames?: string[]
    techniqueLocalNames?: string[]
    motifLocalNames?: string[]
}

export type OntologyEntityInput = Omit<OntologyEntity, 'iri'>

export type OntologyEntityType =
    | 'ornaments'
    | 'techniques'
    | 'motifs'
    | 'regions'
    | 'regional-embroideries'
    | 'regional-motifs'

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

export type OntologyVersionStatus = 'STAGED' | 'ACTIVE' | 'SUPERSEDED' | 'FAILED'

export type OntologyVersion = {
    id: number
    versionNumber: number
    createdAt: string
    createdByUserId: number | null
    createdByUsername: string | null
    changeReason: string
    contentHash: string
    fileName: string
    ontologyNamespace: string
    valid: boolean
    validationMessage: string | null
    previousVersionId: number | null
    previousVersionNumber: number | null
    restoredFromVersionId: number | null
    restoredFromVersionNumber: number | null
    status: OntologyVersionStatus
}

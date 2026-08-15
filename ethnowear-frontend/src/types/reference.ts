export type Language = "bg" | "en"

export type ReferenceResource = {
    iri: string
    localName: string
    label: string
    altLabels?: string[]
    comment?: string
    labels?: Partial<Record<Language, string>>
    altLabelsByLanguage?: Partial<Record<Language, string[]>>
    comments?: Partial<Record<Language, string>>
    imageUrl?: string | null
}

export type ReferenceData = {
    language: string
    regions: ReferenceResource[]
    regionGroups: ReferenceResource[]
    ornaments: ReferenceResource[]
    ornamentTypes: ReferenceResource[]
    colors: ReferenceResource[]
    techniques: ReferenceResource[]
    techniqueTypes: ReferenceResource[]
    motifs: ReferenceResource[]
    regionalEmbroideryTypes: ReferenceResource[]
    regionsByRegionGroup: Record<string, string[]>
    regionByRegionalEmbroidery: Record<string, string>
    ornamentsByRegion: Record<string, string[]>
    techniquesByRegion: Record<string, string[]>
    ornamentsByType: Record<string, string[]>
    techniquesByType: Record<string, string[]>
}

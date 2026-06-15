import type { ReferenceResource } from './reference'

export type EmbroideryFilters = {
    regionalEmbroideryLocalNames: string[]
    regionGroupLocalNames: string[]
    regionLocalNames: string[]
    ornamentTypeLocalNames: string[]
    ornamentLocalNames: string[]
    techniqueLocalNames: string[]
    // motifLocalName: string[]
}

export const emptyEmbroideryFilters: EmbroideryFilters = {
    regionalEmbroideryLocalNames: [],
    regionGroupLocalNames: [],
    regionLocalNames: [],
    ornamentTypeLocalNames: [],
    ornamentLocalNames: [],
    techniqueLocalNames: [],
}

export type DisabledEmbroideryFilters = Partial<EmbroideryFilters>

export type EmbroideryFilterOptions = {
    regionalEmbroideries: ReferenceResource[]
    regionGroups: ReferenceResource[]
    regions: ReferenceResource[]
    ornamentTypes: ReferenceResource[]
    ornaments: ReferenceResource[]
    techniques: ReferenceResource[]
}

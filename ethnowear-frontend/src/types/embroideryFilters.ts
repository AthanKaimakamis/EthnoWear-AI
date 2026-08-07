import type { ReferenceResource } from './reference'

export type FilterCombinationMode = 'and' | 'or'

export type EmbroideryFilters = {
    regionGroupLocalNames: string[]
    regionLocalNames: string[]
    ornamentTypeLocalNames: string[]
    ornamentLocalNames: string[]
    techniqueLocalNames: string[]
    // motifLocalName: string[]
}

export const emptyEmbroideryFilters: EmbroideryFilters = {
    regionGroupLocalNames: [],
    regionLocalNames: [],
    ornamentTypeLocalNames: [],
    ornamentLocalNames: [],
    techniqueLocalNames: [],
}

export type DisabledEmbroideryFilters = Partial<EmbroideryFilters>

export type EmbroideryFilterOptions = {
    regionGroups: ReferenceResource[]
    regions: ReferenceResource[]
    ornamentTypes: ReferenceResource[]
    ornaments: ReferenceResource[]
    techniques: ReferenceResource[]
}

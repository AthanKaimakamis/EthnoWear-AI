import { apiRequest } from "./http";
import type { Language, ReferenceData, ReferenceResource } from "../types/reference.ts";

export function getFullReference(language: Language = 'bg') {
    return apiRequest<ReferenceData>('/api/reference/full', {
        query: { language }
    })
}

export function getRegions(language: Language = 'bg') {
    return apiRequest<ReferenceResource[]>('/api/reference/regions', {
        query: { language }
    })
}

export function getRegionGroups(language: Language = 'bg') {
    return apiRequest<ReferenceResource[]>('/api/reference/region-groups', {
        query: { language }
    })
}

export function getOrnaments(language: Language = 'bg') {
    return apiRequest<ReferenceResource[]>('/api/reference/ornaments', {
        query: { language }
    })
}

export function getColors(language: Language = 'bg') {
    return apiRequest<ReferenceResource[]>('/api/reference/colors', {
        query: { language }
    })
}

export function getTechniques(language: Language = 'bg') {
    return apiRequest<ReferenceResource[]>('/api/reference/techniques', {
        query: { language }
    })
}

export function getMotifs(language: Language = 'bg') {
    return apiRequest<ReferenceResource[]>('/api/reference/motifs', {
        query: { language }
    })
}

export function getRegionalEmbroideryTypes(language: Language = 'bg') {
    return apiRequest<ReferenceResource[]>('/api/reference/regional-embroidery-types',{
        query: { language }
    })
}

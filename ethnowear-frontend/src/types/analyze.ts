import type { Language } from "./reference.ts";

export type AnalyzeRequest = {
    selectedOrnaments: string[]
    selectedColors: string[]
    selectedTechniques: string[]
    selectedMotif?: string | null
    selectedRegion?: string | null
    selectedRegionalEmbroidery?: string | null
    language?: Language
}

export type Evidence = {
    featureType: string
    selectedFeature: string
    matchedProperty: string
    weight: number
    explanation: string
}

export type Candidate = {
    localName: string
    label?: string | null
    score: number
    evidence: Evidence[]
}

export type AnalyzeResponse = {
    explanation: string
    candidates: Candidate[]
}
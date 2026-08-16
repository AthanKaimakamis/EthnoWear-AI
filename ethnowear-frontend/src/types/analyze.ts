import type { Language } from "./reference.ts";

export type AnalyzeRequest = {
    ornaments: string[]
    colors: string[]
    techniques: string[]
    motif?: string | null
    region?: string | null
    regionalEmbroidery?: string | null
    language?: Language
}

export type Evidence = {
    featureType: string
    selectedFeature: string
    selectedFeatureLabel: string | null
    matchedProperty: string
    matchedPropertyLabel: string | null
    weight: number
}

export type Candidate = {
    id: string
    label: string | null
    type: string
    score: number
    evidence: Evidence[]
}

export type AnalyzeResponse = {
    conversationId: string
    topCandidate: Candidate | null
    candidates: Candidate[]
    explanation: string
    warnings: string[]
}

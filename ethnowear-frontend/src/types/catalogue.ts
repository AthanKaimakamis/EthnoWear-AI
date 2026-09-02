import type { PageResponse } from './api'
import type {
    ArchiveEvidenceDetails,
    EntityContentDetails,
} from './archive'

export type OntologyFeatureType =
    | 'ORNAMENT'
    | 'COLOR'
    | 'TECHNIQUE'
    | 'MOTIF'
    | 'REGION'
    | 'REGIONAL_MOTIF'
    | 'REGIONAL_EMBROIDERY'

export type FilterCombinationMode = 'AND' | 'OR'

export type CatalogFacetType =
    | 'CATEGORY'
    | 'RELATED_ENTITY'
    | 'RELATED_CATEGORY'

export type CategoryLinkDetails = {
    targetEntityType: OntologyFeatureType
    iri: string
    localName: string
    label: string
}

export type EntityLinkDetails = {
    entityType: OntologyFeatureType
    iri: string
    localName: string
    label: string
}

export type EntityCardDetails = {
    entityType: OntologyFeatureType
    iri: string
    localName: string
    label: string
    comment: string | null
    categories: CategoryLinkDetails[]
    evidenceCount: number
    representativeMediaAssetId: number | null
}

export type CatalogFacetValueDetails = {
    iri: string
    localName: string
    label: string
    count: number
    selected: boolean
}

export type CatalogFacetGroupDetails = {
    facetType: CatalogFacetType
    entityType: OntologyFeatureType
    values: CatalogFacetValueDetails[]
}

export type PageMetadataDetails = {
    number: number
    size: number
    totalElements: number
    totalPages: number
    first: boolean
    last: boolean
}

export type ConceptCatalogQuery = {
    entityType: OntologyFeatureType
    language: string
    searchText?: string | null
    categoryLocalNames?: string[]
    relatedEntityLocalNames?: Partial<Record<OntologyFeatureType, string[]>>
    relatedCategoryLocalNames?: Partial<Record<OntologyFeatureType, string[]>>
    combinationMode?: FilterCombinationMode
}

export type ConceptCatalogResultDetails = {
    items: EntityCardDetails[]
    page: PageMetadataDetails
    facets: CatalogFacetGroupDetails[]
}

export type EntityOntologyDetails = {
    entityType: OntologyFeatureType
    iri: string
    localName: string
    label: string
    altLabels: string[]
    comment: string | null
    language: string
    categories: CategoryLinkDetails[]
    relatedEntities: Partial<Record<OntologyFeatureType, EntityLinkDetails[]>>
}

export type EntityDetailDetails = {
    ontology: EntityOntologyDetails
    content: EntityContentDetails
    evidence: PageResponse<ArchiveEvidenceDetails>
}

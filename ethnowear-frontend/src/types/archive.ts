import type { EntityCardDetails, OntologyFeatureType } from './catalogue'

export const ARCHIVE_TYPES = [
    'ORNAMENT_EXAMPLE',
    'MOTIF_EXAMPLE',
    'TECHNIQUE_EXAMPLE',
    'EMBROIDERY_SAMPLE',
    'CLOTHING_ITEM',
    'PHOTO_REFERENCE',
    'TEXT_REFERENCE',
] as const

export type ArchiveType = typeof ARCHIVE_TYPES[number]

export type TrustedLevel = 'VERIFIED' | 'LIKELY' | 'UNVERIFIED'
export type PublicationStatus = 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'ARCHIVED'
export type MediaType = 'IMAGE' | 'PDF' | 'THUMBNAIL' | 'SCAN' | 'OTHER'
export type MediaRole = 'PRIMARY' | 'DETAIL' | 'SOURCE_SCAN' | 'THUMBNAIL' | 'OTHER'
export type MediaFeatureAnnotationType =
    | 'VISIBLE_IN_IMAGE'
    | 'PRIMARY_SUBJECT'
    | 'DETAIL_VIEW'
    | 'CROP_REGION'

export type KnowledgeChunkType =
    | 'GENERAL'
    | 'REGION'
    | 'ORNAMENT'
    | 'TECHNIQUE'
    | 'MOTIF'
    | 'COLOR'
    | 'REGIONAL_EMBROIDERY'
    | 'SOURCE_EXCERPT'

export type SourceType =
    | 'BOOK'
    | 'SCANNED_BOOK'
    | 'WEBSITE'
    | 'MUSEUM_CATALOG'
    | 'ARTICLE'
    | 'FIELD_NOTE'

export type ArchiveItemWriteDto = {
    sourceReferenceId: number
    collectionId: string | null
    inventoryNumber: string | null
    titleBg: string | null
    titleEn: string | null
    descriptionBg: string | null
    descriptionEn: string | null
    archiveType: ArchiveType
    periodText: string | null
    originText: string | null
    currentLocation: string | null
    trustedLevel: TrustedLevel
    ontologyRegionIri: string | null
    ontologyRegionLocalName: string | null
    ontologyRegionalEmbroideryIri: string | null
    ontologyRegionalEmbroideryLocalName: string | null
}

export type ArchiveItemDetails = ArchiveItemWriteDto & {
    id: number
    publicationStatus: PublicationStatus
    submittedAt: string | null
    publishedAt: string | null
    archivedAt: string | null
    createdAt: string
    updatedAt: string
}

export type ArchiveItemFeatureWriteDto = {
    archiveItemId: number
    featureType: OntologyFeatureType
    ontologyIri: string
    ontologyLocalName: string
    confidence: number | null
    validated: boolean
    notes: string | null
    sourceReferenceId: number | null
}

export type ArchiveItemFeatureDetails = ArchiveItemFeatureWriteDto & {
    id: number
    createdAt: string
    updatedAt: string
}

export type SourceWriteDto = {
    title: string
    author: string | null
    publisher: string | null
    year: number | null
    sourceType: SourceType
    language: string | null
    filePath: string | null
    url: string | null
    isbn: string | null
    notes: string | null
    trusted: boolean
}

export type SourceDetails = SourceWriteDto & {
    id: number
    createdAt: string
    updatedAt: string
}

export type SourceReferenceDataDto = {
    chapter: string | null
    pageFrom: number | null
    pageTo: number | null
    figureNumber: string | null
    sectionTitle: string | null
    catalogNumber: string | null
    referenceUrl: string | null
    accessedDate: string | null
    locator: string | null
    note: string | null
}

export type SourceReferenceWriteDto = SourceReferenceDataDto & {
    sourceId: number
}

export type SourceReferenceDetails = SourceReferenceWriteDto & {
    id: number
    createdAt: string
    updatedAt: string
}

export type MediaAssetWriteDto = {
    sourceReferenceId: number | null
    fileName: string | null
    filePath: string | null
    storageUrl: string | null
    mimeType: string | null
    mediaType: MediaType
    width: number | null
    height: number | null
    sizeBytes: number | null
    checksum: string | null
}

export type MediaAssetDetails = MediaAssetWriteDto & {
    id: number
    thumbnailPath: string | null
    description: string | null
    createdAt: string
    updatedAt: string
    documentFigure: DocumentFigureMediaLinkDetails | null
}

export type DocumentFigureMediaLinkDetails = {
    documentId: number
    documentPageId: number
    pageSequence: number
    figureId: number
    caption: string | null
    printedFigureNumber: string | null
    sourceReferenceId: number | null
    reviewState: 'PENDING' | 'APPROVED' | 'REJECTED' | 'OUTDATED'
}

export type DocumentMediaLinkDetails = {
    documentPageMediaId: number
    mediaAssetId: number
    documentPageId: number
    documentId: number
    sourceReferenceId: number | null
    documentSourceId: number | null
}

export type MediaUploadRequest = {
    sourceReferenceId: number | null
    mediaType: MediaType
    category: 'archive' | 'documents' | 'entities'
    description: string | null
}

export type MediaEntityLinkWriteDto = {
    mediaAssetId: number
    entityType: OntologyFeatureType
    ontologyIri: string
    ontologyLocalName: string
    description: string | null
}

export type MediaEntityLinkDetails = MediaEntityLinkWriteDto & {
    id: number
    createdAt: string
    updatedAt: string
}

export type ArchiveItemMediaWriteDto = {
    archiveItemId: number
    mediaAssetId: number
    role: MediaRole
    captionBg: string | null
    captionEn: string | null
}

export type ArchiveItemMediaDetails = ArchiveItemMediaWriteDto & {
    id: number
    createdAt: string
    updatedAt: string
}

export type MediaFeatureAnnotationWriteDto = {
    archiveItemMediaId: number
    archiveItemFeatureId: number
    annotationType: MediaFeatureAnnotationType
    x: number | null
    y: number | null
    width: number | null
    height: number | null
    note: string | null
}

export type MediaFeatureAnnotationDetails = MediaFeatureAnnotationWriteDto & {
    id: number
    createdAt: string
    updatedAt: string
}

export type KnowledgeChunkWriteDto = {
    chunkType: KnowledgeChunkType
    ontologyIri: string | null
    ontologyLocalName: string | null
    language: string
    content: string
    sourceReferenceId: number | null
    embeddingModel: string | null
    embeddingId: string | null
}

export type KnowledgeChunkDetails = KnowledgeChunkWriteDto & {
    id: number
    createdAt: string
    updatedAt: string
}

export type ArchiveEvidenceFeatureDetails = {
    id: number
    featureType: OntologyFeatureType
    ontologyIri: string
    ontologyLocalName: string
    confidence: number | null
    notes: string | null
    sourceReferenceId: number | null
}

export type ArchiveEvidenceMediaDetails = {
    archiveItemMediaId: number
    mediaAssetId: number
    role: MediaRole
    fileName: string | null
    filePath: string | null
    storageUrl: string | null
    mimeType: string | null
    mediaType: MediaType
    captionBg: string | null
    captionEn: string | null
}

export type ArchiveEvidenceDetails = {
    archiveItemId: number
    archiveType: ArchiveType
    titleBg: string | null
    titleEn: string | null
    periodText: string | null
    originText: string | null
    currentLocation: string | null
    trustedLevel: TrustedLevel
    directlyLinked: boolean
    matchingFeatures: ArchiveEvidenceFeatureDetails[]
    previewMedia: ArchiveEvidenceMediaDetails | null
}

export type EntityKnowledgeChunkDetails = {
    id: number
    chunkType: KnowledgeChunkType
    language: string
    content: string
    sourceReferenceId: number | null
}

export type EntityMediaAnnotationDetails = {
    annotationId: number
    archiveItemId: number
    archiveItemMediaId: number
    mediaAssetId: number
    role: MediaRole
    mediaType: MediaType
    fileName: string | null
    filePath: string | null
    storageUrl: string | null
    mimeType: string | null
    captionBg: string | null
    captionEn: string | null
    annotationType: MediaFeatureAnnotationType
    x: number | null
    y: number | null
    width: number | null
    height: number | null
    note: string | null
}

export type EntitySourceCitationDetails = SourceReferenceDataDto & {
    sourceReferenceId: number
    sourceId: number
    title: string
    author: string | null
    publisher: string | null
    year: number | null
    sourceType: SourceType
    sourceLanguage: string | null
    filePath: string | null
    url: string | null
    isbn: string | null
    trusted: boolean
}

export type EntityContentDetails = {
    entityType: OntologyFeatureType
    ontologyIri: string
    knowledgeChunks: EntityKnowledgeChunkDetails[]
    mediaAnnotations: EntityMediaAnnotationDetails[]
    sources: EntitySourceCitationDetails[]
}

export type ArchiveItemMediaContentDetails = {
    media: ArchiveItemMediaDetails
    asset: MediaAssetDetails
    annotations: MediaFeatureAnnotationDetails[]
}

export type ArchiveItemDetailDetails = {
    archiveItem: ArchiveItemDetails
    source: EntitySourceCitationDetails
    features: ArchiveItemFeatureDetails[]
    media: ArchiveItemMediaContentDetails[]
}

export type RegionalEmbroideryArchiveSectionDetails = {
    regionalEmbroidery: EntityCardDetails
    totalItems: number
    previewItems: ArchiveEvidenceDetails[]
}

export type RegionalEmbroideryArchiveOverviewDetails = {
    language: string
    sections: RegionalEmbroideryArchiveSectionDetails[]
}

export type ArchiveEntryDetails = {
    archiveItem: ArchiveItemDetails
    features: ArchiveItemFeatureDetails[]
    media: ArchiveItemMediaDetails[]
}

export type ArchiveEntryFeatureWriteDto = Omit<ArchiveItemFeatureWriteDto, 'archiveItemId'> & {
    id?: number
}
export type ArchiveEntryMediaWriteDto = Omit<ArchiveItemMediaWriteDto, 'archiveItemId'> & {
    id?: number
}

export type ArchiveEntryWriteDto = {
    archiveItem: ArchiveItemWriteDto
    features: ArchiveEntryFeatureWriteDto[]
    media: ArchiveEntryMediaWriteDto[]
}

export type PublicationRequirementKey =
    | 'LOCALIZED_TITLE'
    | 'SOURCE_REFERENCE'
    | 'ONTOLOGY_CLASSIFICATION'
    | 'FEATURES_VALIDATED'
    | 'MEDIA_ATTACHED'
    | 'PRIMARY_MEDIA'
    | string

export type PublicationValidationSeverity = 'ERROR' | 'WARNING' | 'INFO'

export type PublicationReadinessRequirement = {
    key: PublicationRequirementKey
    satisfied: boolean
    severity: PublicationValidationSeverity
    message: string | null
    field: string | null
}

export type PublicationReadinessDetails = {
    archiveItemId: number
    publicationStatus: PublicationStatus
    ready: boolean
    requirements: PublicationReadinessRequirement[]
}

export type PublicationReadinessApiCheck = {
    requirement: PublicationRequirementKey
    satisfied: boolean
    blocking: boolean
    message?: string | null
    field?: string | null
}

export type PublicationReadinessApiResponse = {
    archiveItemId: number
    publicationStatus: PublicationStatus
    ready: boolean
    checks: PublicationReadinessApiCheck[]
}

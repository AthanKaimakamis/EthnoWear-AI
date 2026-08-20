import type { PageRequest, PageResponse } from './api'

export type DocumentType = 'PDF_DOCUMENT' | 'SCANNED_BOOK' | 'PAGE_IMAGE_SET' | 'STANDALONE_CAPTURE' | 'UNKNOWN_FRAGMENT_SET'
export type ProvenanceStatus = 'KNOWN_SOURCE' | 'PARTIAL_SOURCE' | 'UNKNOWN_SOURCE'
export type ProvenanceTrustState = 'UNKNOWN' | 'UNTRUSTED' | 'PARTIAL' | 'TRUSTED' | 'VERIFIED'
export type ProcessingState = 'UPLOADED' | 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export type ReviewState = 'NOT_READY' | 'REVIEW_REQUIRED' | 'IN_REVIEW' | 'APPROVED' | 'REJECTED'
export type TranscriptionApprovalState = 'NOT_REQUIRED' | 'PENDING' | 'APPROVED' | 'REJECTED'
export type IndexingState = 'NOT_ELIGIBLE' | 'PENDING' | 'INDEXED' | 'FAILED' | 'OUTDATED'
export type EvidenceState = 'ACTIVE' | 'SUPERSEDED' | 'MERGED' | 'RETIRED'
export type DocumentPageKind = 'DOCUMENT_PAGE' | 'STANDALONE_IMAGE' | 'UNKNOWN_FRAGMENT'
export type DocumentPageRole = 'NORMAL' | 'MISSING_PAGE' | 'SUPPLEMENTAL_PAGE'
export type DocumentJobType = 'PAGE_EXTRACTION' | 'OCR' | 'OCR_QUALITY_ASSESSMENT' | 'CHUNK_GENERATION' | 'INDEX_CHUNK' | 'REINDEX_DOCUMENT' | 'REMOVE_VECTOR' | 'GENERATE_THUMBNAIL'
export type DocumentJobStatus = 'QUEUED' | 'CLAIMED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'RETRY_WAIT' | 'CANCEL_REQUESTED' | 'CANCELLED' | 'TIMED_OUT' | 'DEAD'
export type RenditionType = 'ORIGINAL_UPLOAD' | 'PDF_PAGE_RENDER' | 'PHONE_PHOTO' | 'PREPROCESSED_OCR_INPUT' | 'CROPPED' | 'DESKEWED' | 'BINARIZED' | 'SEARCHABLE_PDF_PAGE' | 'THUMBNAIL' | 'REPLACEMENT_SCAN'
export type QualityStatus = 'HIGH_QUALITY' | 'MINOR_REVIEW' | 'REVIEW_REQUIRED' | 'POOR_QUALITY' | 'PROCESSING_FAILED' | 'INCOMPLETE'

export type DocumentQuery = PageRequest & {
    searchText?: string
    documentType?: DocumentType
    provenanceStatus?: ProvenanceStatus
    provenanceTrustState?: ProvenanceTrustState
    processingState?: ProcessingState
    reviewState?: ReviewState
    indexingState?: IndexingState
    language?: string
    sourceId?: number
}

export type DocumentProgressSummary = {
    totalPages: number
    completedProcessingPages: number
    failedProcessingPages: number
    reviewRequiredPages: number
    approvedTranscriptionPages: number
    indexedPages: number
}

export type DocumentSummary = {
    id: number
    sourceId: number | null
    sourceTitle: string | null
    originalMediaAssetId: number | null
    documentType: DocumentType
    provenanceStatus: ProvenanceStatus
    title: string
    author: string | null
    publisher: string | null
    publicationYear: number | null
    language: string | null
    pageCount: number
    processingState: ProcessingState
    reviewState: ReviewState
    provenanceTrustState: ProvenanceTrustState
    indexingState: IndexingState
    progress: DocumentProgressSummary
    createdAt: string
    updatedAt: string
}

export type DocumentProgress = {
    totalPages: number
    processing: Record<string, number>
    review: Record<string, number>
    transcriptionApproval: Record<string, number>
    indexing: Record<string, number>
}

export type DocumentIndexingStatus = {
    documentId: number
    documentState: IndexingState
    totalChunks: number
    chunkCounts: Record<string, number>
}

export type DocumentSource = {
    id: number
    title: string
    author: string | null
    publisher: string | null
    publicationYear: number | null
    sourceType: string
    language: string | null
    url: string | null
    isbn: string | null
    notes: string | null
    trusted: boolean
}

export type DocumentProcessingJob = {
    id: number
    jobType: DocumentJobType
    status: DocumentJobStatus
    documentId: number
    documentPageId: number | null
    inputMediaAssetId: number | null
    knowledgeChunkId: number | null
    priority: number
    attemptCount: number
    maxAttempts: number
    availableAt: string | null
    claimedAt: string | null
    startedAt: string | null
    finishedAt: string | null
    timeoutAt: string | null
    processorName: string | null
    processorVersion: string | null
    toolName: string | null
    toolVersion: string | null
    errorCode: string | null
    safeErrorMessage: string | null
    cancellationReason: string | null
    createdAt: string
    updatedAt: string
}

export type BoundedJobHistory = { items: DocumentProcessingJob[]; hasMore: boolean }

export type DocumentDetails = {
    summary: DocumentSummary
    source: DocumentSource | null
    notes: string | null
    mergedIntoDocumentId: number | null
    progress: DocumentProgress
    indexingStatus: DocumentIndexingStatus
    recentJobs: BoundedJobHistory
}

export type DocumentPageSummary = {
    id: number
    documentId: number
    sourceReferenceId: number | null
    pageKind: DocumentPageKind
    pageRole: DocumentPageRole
    pageSequence: number
    pdfPageIndex: number | null
    printedPageNumber: string | null
    printedPageSort: number | null
    pageLabel: string | null
    provenanceStatus: ProvenanceStatus
    processingState: ProcessingState
    reviewState: ReviewState
    transcriptionApprovalState: TranscriptionApprovalState
    provenanceTrustState: ProvenanceTrustState
    indexingState: IndexingState
    evidenceState: EvidenceState
    ocrConfidence: number | null
    hasRawOcrText: boolean
    hasCorrectedText: boolean
    previewMediaAssetId: number | null
    createdAt: string
    updatedAt: string
}

export type DocumentPageMedia = {
    id: number
    mediaAssetId: number
    fileName: string
    mimeType: string
    renditionType: RenditionType
    original: boolean
    preferredOcrInput: boolean
    derivativeOfDocumentPageMediaId: number | null
    producingJobId: number | null
    displayOrder: number
    width: number | null
    height: number | null
    dpi: number | null
    colorMode: string | null
    notes: string | null
    createdAt: string
    updatedAt: string
}

export type DocumentPageDetails = {
    summary: DocumentPageSummary
    rawOcrText: string | null
    correctedText: string | null
    ocrEngine: string | null
    ocrEngineVersion: string | null
    ocrLanguage: string | null
    reviewer: string | null
    reviewedAt: string | null
    reviewNotes: string | null
    canonicalDocumentPageId: number | null
    provenanceNote: string | null
    provenanceReviewedBy: string | null
    provenanceReviewedAt: string | null
    media: DocumentPageMedia[]
}

export type DocumentBibliographicInput = {
    sourceId: number | null
    title: string
    author: string | null
    publisher: string | null
    publicationYear: number | null
    language: string | null
    notes: string | null
}

export type PageProvenanceInput = {
    sourceReferenceId: number | null
    provenanceStatus: ProvenanceStatus
    provenanceTrustState: ProvenanceTrustState
    note: string | null
    recordedBy: string
    reason: string
}

export type PdfDocumentUploadCommand = {
    metadata: DocumentBibliographicInput
    documentType: DocumentType
    provenanceStatus: ProvenanceStatus
    provenanceTrustState: ProvenanceTrustState
    mediaDescription: string | null
}

export type StandaloneCaptureUploadCommand = {
    metadata: DocumentBibliographicInput
    provenance: PageProvenanceInput
    printedPageNumber: string | null
    printedPageSort: number | null
    pageLabel: string | null
    queueOcr: boolean
    mediaDescription: string | null
}

export type DocumentUploadDetails = {
    documentId: number
    mediaAssetId: number
    documentPageId: number | null
    documentPageMediaId: number | null
    processingJobId: number | null
}

export type DocumentMetadataUpdateCommand = Omit<DocumentBibliographicInput, 'sourceId'>

export type DocumentPageOcrResult = {
    id: number
    documentPageId: number
    documentPageMediaId: number
    processingJobId: number | null
    rawText: string
    ocrEngine: string | null
    ocrEngineVersion: string | null
    ocrLanguage: string | null
    ocrConfidence: number | null
    current: boolean
    createdAt: string
    updatedAt: string
}

export type DocumentPageReview = {
    id: number
    documentPageId: number
    reviewAction: 'REVIEW_STARTED' | 'CORRECTED_TEXT_SAVED' | 'APPROVED' | 'REJECTED' | 'APPROVAL_REVOKED'
    reviewer: string
    correctedTextSnapshot: string | null
    previousReviewState: ReviewState | null
    newReviewState: ReviewState
    previousApprovalState: TranscriptionApprovalState | null
    newApprovalState: TranscriptionApprovalState
    reason: string | null
    createdAt: string
}

export type DocumentPageProvenanceEvent = {
    id: number
    documentPageId: number
    eventType: 'SOURCE_IDENTIFIED' | 'SOURCE_REFERENCE_CHANGED' | 'TRUST_CHANGED' | 'LINKED_TO_CANONICAL_PAGE' | 'MERGED' | 'LINK_REVERSED'
    previousSourceReferenceId: number | null
    newSourceReferenceId: number | null
    previousProvenanceStatus: ProvenanceStatus | null
    newProvenanceStatus: ProvenanceStatus | null
    previousTrustState: ProvenanceTrustState | null
    newTrustState: ProvenanceTrustState | null
    previousCanonicalDocumentPageId: number | null
    newCanonicalDocumentPageId: number | null
    reviewedBy: string
    reason: string
    createdAt: string
}

export type DocumentPageQualitySignal = {
    id: number
    assessmentId: number
    signalType: string
    signalOrdinal: number
    signalValueDecimal: number | null
    signalValueText: string | null
    severity: 'INFO' | 'WARNING' | 'ERROR'
    weight: number | null
    message: string
    createdAt: string
    updatedAt: string
}

export type DocumentPageQualityAssessment = {
    id: number
    documentPageId: number
    documentPageMediaId: number | null
    processingJobId: number | null
    documentPageOcrResultId: number | null
    assessmentType: string
    assessorType: 'DETERMINISTIC' | 'OCR_ENGINE' | 'VISION_MODEL' | 'HUMAN'
    assessorName: string | null
    assessorVersion: string | null
    scoreVersion: string | null
    qualityStatus: QualityStatus
    overallScore: number | null
    summary: string | null
    limitations: string | null
    current: boolean
    signals: DocumentPageQualitySignal[]
    createdAt: string
    updatedAt: string
}

export type DocumentPageResponse<T> = PageResponse<T>

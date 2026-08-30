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
export type DocumentJobType = 'PAGE_EXTRACTION' | 'OCR' | 'EXTRACT_PAGE_FIGURES' | 'OCR_QUALITY_ASSESSMENT' | 'VISION_OCR_ASSESSMENT' | 'CHUNK_GENERATION' | 'INDEX_CHUNK' | 'REINDEX_DOCUMENT' | 'REMOVE_VECTOR' | 'GENERATE_THUMBNAIL' | 'MEDIA_CLEANUP'
export type DocumentJobStatus = 'QUEUED' | 'CLAIMED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'RETRY_WAIT' | 'CANCEL_REQUESTED' | 'CANCELLED' | 'TIMED_OUT' | 'DEAD'
export type RenditionType = 'ORIGINAL_UPLOAD' | 'PDF_PAGE_RENDER' | 'PHONE_PHOTO' | 'PREPROCESSED_OCR_INPUT' | 'CROPPED' | 'DESKEWED' | 'BINARIZED' | 'SEARCHABLE_PDF_PAGE' | 'THUMBNAIL' | 'REPLACEMENT_SCAN'
export type QualityStatus = 'HIGH_QUALITY' | 'MINOR_REVIEW' | 'REVIEW_REQUIRED' | 'POOR_QUALITY' | 'PROCESSING_FAILED' | 'INCOMPLETE'
export type DocumentPageWorkflowStepType = 'IMAGE_EXTRACTION' | 'OCR' | 'OCR_QUALITY_ASSESSMENT' | 'VISION_OCR_ASSESSMENT' | 'HUMAN_REVIEW'
export type DocumentPageWorkflowStepStatus = 'NOT_STARTED' | 'QUEUED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'BLOCKED'

export type ProcessingJobCapabilities = {
    retryable: boolean
    cloneable: boolean
    cancellable: boolean
    deletable: boolean
}

export type ProcessingJobRetirement = {
    retired: boolean
    retiredAt: string | null
    retiredBy: string | null
    reason: string | null
}

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
    defaultSourceReferenceId: number | null
    originalMediaAssetId: number | null
    thumbnailMediaAssetId: number | null
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
    previousJobId: number | null
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
    capabilities: ProcessingJobCapabilities
    retirement: ProcessingJobRetirement
    versionToken: string | null
}

export type ProcessingJobSummary = {
    id: number
    previousJobId: number | null
    jobType: DocumentJobType
    status: DocumentJobStatus
    purpose: {
        type: DocumentJobType
        code: string
    }
    target: {
        type: 'DOCUMENT' | 'DOCUMENT_PAGE' | 'KNOWLEDGE_CHUNK' | 'MEDIA_ASSET'
        documentId: number | null
        documentPageId: number | null
        inputMediaAssetId: number | null
        knowledgeChunkId: number | null
    }
    priority: number
    progress: {
        attemptCount: number
        maxAttempts: number
        attemptsRemaining: number
        retryable: boolean
        cancellable: boolean
        terminal: boolean
    }
    timestamps: {
        availableAt: string | null
        claimedAt: string | null
        startedAt: string | null
        finishedAt: string | null
        timeoutAt: string | null
        createdAt: string
        updatedAt: string
    }
    error: { code: string | null; message: string | null } | null
    cancellationReason: string | null
    document: { id: number; title: string; language: string | null }
    page: { id: number; pageSequence: number; pdfPageIndex: number | null; printedPageNumber: string | null; pageLabel: string | null } | null
    inputMediaAssetId: number | null
    knowledgeChunkId: number | null
    attempts: ProcessingJobAttempt[]
    result: ProcessingJobResult
    capabilities: ProcessingJobCapabilities
    retirement: ProcessingJobRetirement
    versionToken: string | null
}

export type DocumentPageWorkflowStep = {
    step: DocumentPageWorkflowStepType
    status: DocumentPageWorkflowStepStatus
    jobId: number | null
    jobStatus: DocumentJobStatus | null
    message: string | null
}

export type DocumentPageWorkflowProgress = {
    documentId: number
    pageId: number
    completedSteps: number
    totalSteps: number
    steps: DocumentPageWorkflowStep[]
}

export type ProcessingJobAttempt = {
    id: number
    executionNumber: number
    attemptNumber: number
    status: DocumentJobStatus
    worker: string | null
    claimedAt: string | null
    startedAt: string | null
    finishedAt: string | null
    processorName: string | null
    processorVersion: string | null
    toolName: string | null
    toolVersion: string | null
    error: { code: string | null; message: string | null } | null
    cancellationReason: string | null
}

export type DocumentPageQualitySummary = {
    assessmentId: number
    score: number | null
    percentage: number | null
    qualityLevel: QualityStatus
    passedChecks: number
    failedChecks: number
    assessedAt: string
}

export type ProcessingJobResult = {
    producedMediaAssetIds: number[]
    ocrResultId: number | null
    qualityAssessment: DocumentPageQualitySummary | null
}

export type ProcessingJobQuery = PageRequest & {
    searchText?: string
    jobType?: DocumentJobType
    status?: DocumentJobStatus
    documentId?: number
    documentPageId?: number
}

export type ProcessingJobCounts = {
    total: number
    active: number
    retryable: number
    byStatus: Partial<Record<DocumentJobStatus, number>>
}

export type ProcessingJobBulkRetryResult = {
    jobs: DocumentProcessingJob[]
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
    quality: DocumentPageQualitySummary | null
    createdAt: string
    updatedAt: string
    retirement: {
        retired: boolean
        retiredAt: string | null
        retiredBy: string | null
        reason: string | null
    }
    versionToken: string
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

export type FigureReviewState = 'PENDING' | 'APPROVED' | 'REJECTED' | 'OUTDATED'

export type DocumentPageFigure = {
    id: number
    documentPageId: number
    documentPageMediaId: number
    mediaAssetId: number
    sourceReferenceId: number | null
    figureCandidateId: number | null
    figureOrdinal: number
    printedFigureNumber: string | null
    normalizedX: number
    normalizedY: number
    normalizedWidth: number
    normalizedHeight: number
    rawCaptionText: string | null
    correctedCaptionText: string | null
    reviewState: FigureReviewState
    reviewedBy: string | null
    reviewedAt: string | null
    reviewReason: string | null
    detectionConfidence: number | null
    processingJobId: number
    producingAttempt: number
    createdAt: string
    updatedAt: string
    version: string
}

export type DocumentFigurePageGroup = {
    page: DocumentPageSummary
    figures: DocumentPageFigure[]
}

export type FigureCaptionUpdateCommand = {
    correctedCaptionText: string | null
    printedFigureNumber: string | null
    sourceReferenceId: number | null
}

export type DocumentBibliographicInput = {
    sourceId: number | null
    defaultSourceReferenceId: number | null
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

export type PageSourceProvenanceChangeCommand = {
    sourceReferenceId: number | null
    provenanceStatus: ProvenanceStatus
    provenanceTrustState: ProvenanceTrustState
    note: string | null
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

export type DocumentPageMetadataUpdateCommand = {
    printedPageNumber: string | null
    printedPageSort: number | null
    pageLabel: string | null
}

export type PageProvenanceTrustChangeCommand = {
    provenanceTrustState: ProvenanceTrustState
    reason: string
}

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
    reviewAction: 'REVIEW_STARTED' | 'CORRECTED_TEXT_SAVED' | 'RESET_FROM_CURRENT_OCR' | 'APPROVED' | 'REJECTED' | 'APPROVAL_REVOKED'
    reviewer: string
    correctedTextSnapshot: string | null
    previousReviewState: ReviewState | null
    newReviewState: ReviewState
    previousApprovalState: TranscriptionApprovalState | null
    newApprovalState: TranscriptionApprovalState
    reason: string | null
    createdAt: string
}

export type ChunkGenerationBlocker = {
    documentPageId: number
    code: string
    message: string
}

export type ChunkGenerationEligibility = {
    documentId: number
    eligible: boolean
    eligiblePageCount: number
    blockers: ChunkGenerationBlocker[]
}

export type GeneratedChunkCitation = {
    documentPageId: number
    pageOrder: number
    startCharOffset: number
    endCharOffset: number
    startsOnPage: boolean
    endsOnPage: boolean
    printedPageNumber: string | null
    pdfPageIndex: number | null
    label: string | null
}

export type GeneratedKnowledgeChunk = {
    id: number
    documentId: number
    sourceReferenceId: number | null
    chunkType: string
    language: string | null
    content: string
    sourceTextType: string
    chunkOrdinal: number
    chunkingStrategy: string
    chunkingVersion: string
    reviewState: ReviewState
    transcriptionApprovalState: TranscriptionApprovalState
    provenanceTrustState: ProvenanceTrustState
    indexingState: IndexingState
    supersededByKnowledgeChunkId: number | null
    current: boolean
    createdAt: string
    citations: GeneratedChunkCitation[]
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
    percentage: number | null
    summary: string | null
    limitations: string | null
    current: boolean
    signals: DocumentPageQualitySignal[]
    passedChecks: DocumentPageQualitySignal[]
    failedChecks: DocumentPageQualitySignal[]
    createdAt: string
    updatedAt: string
}

export type TextSuggestionIssue = {
    issueType: string
    explanationBg: string
    originalText: string | null
    originalContext: string | null
    suggestedText: string | null
    suggestedContext: string | null
    startOffset: number | null
    endOffset: number | null
    safelyApplicable: boolean
    confidence: number | null
}

export type TextSuggestionUncertainPassage = {
    excerpt: string
    reason: string
    confidence: number | null
}

export type DocumentPageTextSuggestion = {
    id: number
    documentPageId: number
    documentPageMediaId: number
    documentPageOcrResultId: number
    processingJobId: number
    suggestedText: string
    modelName: string
    modelVersion: string | null
    promptVersion: string
    requiresReview: boolean
    editableTextHash: string
    issues: TextSuggestionIssue[]
    uncertainPassages: TextSuggestionUncertainPassage[]
    applied: boolean
    requiresHumanAttention: boolean
    createdAt: string
}

export type MediaCleanupEligibility = {
    documentId: number
    eligible: boolean
    policy: 'KEEP_ORIGINAL_ONLY'
    generatedMediaCount: number
    blockers: string[]
}

export type MediaCleanupSchedule = {
    documentId: number
    policy: 'KEEP_ORIGINAL_ONLY'
    retentionUntil: string
    scheduledMediaCount: number
    job: DocumentProcessingJob
}

export type DocumentPageResponse<T> = PageResponse<T>

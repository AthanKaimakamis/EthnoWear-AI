import type { PageRequest, PageResponse } from '../types/api'
import type { MediaAssetDetails } from '../types/archive'
import type {
    DocumentDetails,
    DocumentIndexingStatus,
    DocumentMetadataUpdateCommand,
    DocumentPageMetadataUpdateCommand,
    PageProvenanceTrustChangeCommand,
    PageSourceProvenanceChangeCommand,
    DocumentPageDetails,
    DocumentPageOcrResult,
    DocumentPageProvenanceEvent,
    DocumentPageQualityAssessment,
    DocumentPageResponse,
    DocumentPageReview,
    DocumentPageSummary,
    DocumentPageWorkflowProgress,
    DocumentProcessingJob,
    ProcessingJobBulkRetryResult,
    ProcessingJobCounts,
    ProcessingJobQuery,
    ProcessingJobSummary,
    DocumentProgress,
    DocumentQuery,
    DocumentSummary,
    DocumentUploadDetails,
    ChunkGenerationEligibility,
    GeneratedKnowledgeChunk,
    PdfDocumentUploadCommand,
    StandaloneCaptureUploadCommand,
    DocumentPageTextSuggestion,
    MediaCleanupEligibility,
    MediaCleanupSchedule,
    DocumentPageFigure,
    DocumentFigurePageGroup,
    FigureCaptionUpdateCommand,
} from '../types/document'
import { compressThumbnail } from '../utils/compressThumbnail'
import {
    adminAuthorizationHeaders,
    apiRequest,
    ApiError,
    apiUrl,
    handleAdminResponseStatus,
} from './http'

export const documentQueryKeys = {
    all: ['admin', 'documents'] as const,
    lists: () => [...documentQueryKeys.all, 'list'] as const,
    list: (query: DocumentQuery) => [...documentQueryKeys.lists(), query] as const,
    detail: (documentId: number) => [...documentQueryKeys.all, 'detail', documentId] as const,
    progress: (documentId: number) => [...documentQueryKeys.detail(documentId), 'progress'] as const,
    pagesRoot: (documentId: number) => [...documentQueryKeys.detail(documentId), 'pages'] as const,
    pages: (documentId: number, page: PageRequest) => [...documentQueryKeys.pagesRoot(documentId), page] as const,
    figuresRoot: (documentId: number) => [...documentQueryKeys.detail(documentId), 'figures'] as const,
    figures: (documentId: number) => [...documentQueryKeys.figuresRoot(documentId), 'all'] as const,
    page: (documentId: number, pageId: number) => [...documentQueryKeys.detail(documentId), 'page', pageId] as const,
    pageFigures: (documentId: number, pageId: number) => [...documentQueryKeys.page(documentId, pageId), 'figures'] as const,
    pageCurrentOcr: (documentId: number, pageId: number) => [...documentQueryKeys.page(documentId, pageId), 'ocr-current'] as const,
    pageQuality: (documentId: number, pageId: number) => [...documentQueryKeys.page(documentId, pageId), 'quality'] as const,
    pageQualityHistory: (documentId: number, pageId: number, page: PageRequest) => [...documentQueryKeys.page(documentId, pageId), 'quality-history', page] as const,
    pageWorkflow: (documentId: number, pageId: number) => [...documentQueryKeys.page(documentId, pageId), 'workflow'] as const,
    pageSuggestion: (documentId: number, pageId: number) => [...documentQueryKeys.page(documentId, pageId), 'text-suggestion'] as const,
    pageSuggestionHistory: (documentId: number, pageId: number, page: PageRequest) => [...documentQueryKeys.page(documentId, pageId), 'text-suggestions', page] as const,
    pageReviewHistory: (documentId: number, pageId: number) => [...documentQueryKeys.page(documentId, pageId), 'reviews'] as const,
    pageOcrHistory: (documentId: number, pageId: number) => [...documentQueryKeys.page(documentId, pageId), 'ocr-history'] as const,
    pageProvenanceHistory: (documentId: number, pageId: number) => [...documentQueryKeys.page(documentId, pageId), 'provenance-history'] as const,
    jobsRoot: (documentId: number) => [...documentQueryKeys.detail(documentId), 'jobs'] as const,
    jobs: (documentId: number, page: PageRequest) => [...documentQueryKeys.jobsRoot(documentId), page] as const,
    indexing: (documentId: number) => [...documentQueryKeys.detail(documentId), 'indexing'] as const,
    chunkEligibility: (documentId: number) => [...documentQueryKeys.detail(documentId), 'chunk-eligibility'] as const,
    chunkJobsRoot: (documentId: number) => [...documentQueryKeys.detail(documentId), 'chunk-jobs'] as const,
    chunkJobs: (documentId: number, page: PageRequest) => [...documentQueryKeys.chunkJobsRoot(documentId), page] as const,
    generatedChunksRoot: (documentId: number) => [...documentQueryKeys.detail(documentId), 'generated-chunks'] as const,
    generatedChunks: (documentId: number, page: PageRequest) => [...documentQueryKeys.generatedChunksRoot(documentId), page] as const,
    cleanupEligibility: (documentId: number) => [...documentQueryKeys.detail(documentId), 'media-cleanup'] as const,
}

export const processingQueryKeys = {
    all: ['admin', 'processing'] as const,
    jobs: (query: ProcessingJobQuery) => [...processingQueryKeys.all, 'jobs', query] as const,
    counts: (query: ProcessingJobQuery) => [...processingQueryKeys.all, 'counts', query] as const,
}

export function listDocuments(query: DocumentQuery, signal?: AbortSignal) {
    return apiRequest<PageResponse<DocumentSummary>>('/api/admin/documents', { query, signal })
}

export function getDocument(documentId: number, signal?: AbortSignal) {
    return apiRequest<DocumentDetails>(`/api/admin/documents/${documentId}`, { signal })
}

export function deleteDocument(documentId: number) {
    return apiRequest<void>(`/api/admin/documents/${documentId}`, {
        method: 'DELETE',
        query: { confirm: true },
    })
}

export function getDocumentProgress(documentId: number, signal?: AbortSignal) {
    return apiRequest<DocumentProgress>(`/api/admin/documents/${documentId}/progress`, { signal })
}

export function getDocumentIndexingStatus(documentId: number, signal?: AbortSignal) {
    return apiRequest<DocumentIndexingStatus>(`/api/admin/documents/${documentId}/indexing-status`, { signal })
}

export function listDocumentPages(documentId: number, page: PageRequest, signal?: AbortSignal) {
    return apiRequest<DocumentPageResponse<DocumentPageSummary>>(`/api/admin/documents/${documentId}/pages`, { query: page, signal })
}

export function getDocumentPage(documentId: number, pageId: number, signal?: AbortSignal) {
    return apiRequest<DocumentPageDetails>(`/api/admin/documents/${documentId}/pages/${pageId}`, { signal })
}

export function getDocumentPageWorkflow(documentId: number, pageId: number, signal?: AbortSignal) {
    return apiRequest<DocumentPageWorkflowProgress>(`/api/admin/documents/${documentId}/pages/${pageId}/workflow`, { signal })
}

export function listPageFigures(pageId: number, signal?: AbortSignal) {
    return apiRequest<DocumentPageFigure[]>(`/api/admin/document-pages/${pageId}/figures`, { signal })
}

export async function listDocumentFigures(documentId: number, signal?: AbortSignal): Promise<DocumentFigurePageGroup[]> {
    const pages = await listDocumentPages(documentId, { page: 0, size: 100, sort: 'pageSequence,asc' }, signal)
    const grouped = await Promise.all(pages.content.map(async page => ({
        page,
        figures: await listPageFigures(page.id, signal),
    })))
    return grouped.filter(group => group.figures.length > 0)
}

export async function getPageFigureContent(pageId: number, figureId: number, signal?: AbortSignal) {
    const response = await fetch(apiUrl(`/api/admin/document-pages/${pageId}/figures/${figureId}/content`), {
        headers: adminAuthorizationHeaders({ Accept: 'image/*' }),
        signal,
    })
    if (!response.ok) {
        handleAdminResponseStatus(response.status)
        const details = response.headers.get('content-type')?.includes('application/json')
            ? await response.json()
            : await response.text()
        throw new ApiError(`Request failed with status ${response.status}`, response.status, details)
    }
    return response.blob()
}

export function updatePageFigure(pageId: number, figureId: number, version: string, command: FigureCaptionUpdateCommand) {
    return apiRequest<DocumentPageFigure>(`/api/admin/document-pages/${pageId}/figures/${figureId}`, {
        method: 'PATCH',
        headers: { 'If-Match': version },
        body: command,
    })
}

export function approvePageFigure(pageId: number, figureId: number, version: string, reason: string) {
    return apiRequest<DocumentPageFigure>(`/api/admin/document-pages/${pageId}/figures/${figureId}/approve`, {
        method: 'POST',
        headers: { 'If-Match': version },
        body: { reason },
    })
}

export function rejectPageFigure(pageId: number, figureId: number, version: string, reason: string) {
    return apiRequest<DocumentPageFigure>(`/api/admin/document-pages/${pageId}/figures/${figureId}/reject`, {
        method: 'POST',
        headers: { 'If-Match': version },
        body: { reason },
    })
}

export function reextractPageFigures(pageId: number) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/document-pages/${pageId}/figures/reextract`, { method: 'POST' })
}

export function deletePageFigure(pageId: number, figureId: number, version: string) {
    return apiRequest<void>(`/api/admin/document-pages/${pageId}/figures/${figureId}`, {
        method: 'DELETE',
        headers: { 'If-Match': version },
    })
}

export function startPageImageExtraction(documentId: number, pageId: number) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/documents/${documentId}/pages/${pageId}/image-extraction`, { method: 'POST' })
}

export function retireDocumentPage(documentId: number, pageId: number, versionToken: string, reason: string) {
    return apiRequest<DocumentPageDetails>(`/api/admin/documents/${documentId}/pages/${pageId}`, {
        method: 'DELETE',
        query: { reason },
        headers: { 'If-Match': versionToken },
    })
}

export function listDocumentJobs(documentId: number, page: PageRequest, signal?: AbortSignal) {
    return apiRequest<PageResponse<DocumentProcessingJob>>(`/api/admin/documents/${documentId}/jobs`, { query: page, signal })
}

export function retryDocumentJob(jobId: number) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/document-processing-jobs/${jobId}/retry`, { method: 'POST' })
}

export function listProcessingJobs(query: ProcessingJobQuery, signal?: AbortSignal) {
    return apiRequest<PageResponse<ProcessingJobSummary>>('/api/admin/processing/jobs', { query, signal })
}

export function getProcessingJobCounts(query: ProcessingJobQuery, signal?: AbortSignal) {
    return apiRequest<ProcessingJobCounts>('/api/admin/processing/counts', { query, signal })
}

export function retryProcessingJobs(jobIds: number[]) {
    return apiRequest<ProcessingJobBulkRetryResult>('/api/admin/processing/jobs/retry', {
        method: 'POST',
        body: { jobIds },
    })
}

export function retryProcessingJob(jobId: number) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/processing/jobs/${jobId}/retry`, { method: 'POST' })
}

export function cancelProcessingJob(jobId: number, reason: string) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/processing/jobs/${jobId}/cancel`, {
        method: 'POST',
        body: { reason },
    })
}

export function createReplacementProcessingJob(jobId: number, versionToken: string) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/processing/jobs/${jobId}/replacement`, {
        method: 'POST',
        headers: { 'If-Match': versionToken },
    })
}

export function retireProcessingJob(jobId: number, versionToken: string, reason: string) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/processing/jobs/${jobId}`, {
        method: 'DELETE',
        query: { reason },
        headers: { 'If-Match': versionToken },
    })
}

export function updateDocumentMetadata(documentId: number, command: DocumentMetadataUpdateCommand) {
    return apiRequest<void>(`/api/admin/documents/${documentId}/metadata`, { method: 'PUT', body: command })
}

export function updateDocumentDefaultSourceReference(documentId: number, sourceReferenceId: number, reason: string) {
    return apiRequest(`/api/admin/documents/${documentId}/default-source-reference`, { method: 'PUT', body: { sourceReferenceId, inheritToUnassignedPages: false, reason } })
}

export function updateDocumentPageMetadata(documentId: number, pageId: number, versionToken: string, command: DocumentPageMetadataUpdateCommand) {
    return apiRequest<void>(`/api/admin/documents/${documentId}/pages/${pageId}/metadata`, {
        method: 'PATCH',
        headers: { 'If-Match': versionToken },
        body: command,
    })
}

export function changePageProvenanceTrust(pageId: number, command: PageProvenanceTrustChangeCommand) {
    return apiRequest<DocumentPageProvenanceEvent>(`/api/admin/document-pages/${pageId}/provenance-events/trust-change`, {
        method: 'POST',
        body: command,
    })
}

export function changePageSourceProvenance(pageId: number, command: PageSourceProvenanceChangeCommand) {
    return apiRequest<DocumentPageProvenanceEvent>(`/api/admin/document-pages/${pageId}/provenance-events/source-change`, {
        method: 'POST',
        body: command,
    })
}

export function queueDocumentChunkGeneration(documentId: number) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/documents/${documentId}/chunk-generation`, { method: 'POST' })
}

export function getChunkGenerationEligibility(documentId: number, signal?: AbortSignal) {
    return apiRequest<ChunkGenerationEligibility>(`/api/admin/documents/${documentId}/chunk-generation/eligibility`, { signal })
}

export function listChunkGenerationJobs(documentId: number, page: PageRequest, signal?: AbortSignal) {
    return apiRequest<PageResponse<DocumentProcessingJob>>(`/api/admin/documents/${documentId}/chunk-generation/jobs`, { query: page, signal })
}

export function listGeneratedChunks(documentId: number, page: PageRequest, signal?: AbortSignal) {
    return apiRequest<PageResponse<GeneratedKnowledgeChunk>>(`/api/admin/documents/${documentId}/generated-chunks`, { query: page, signal })
}

export function queuePageOcr(pageId: number) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/document-pages/${pageId}/ocr`, { method: 'POST' })
}

export function createPageOcrJob(pageId: number) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/document-pages/${pageId}/ocr-jobs`, { method: 'POST' })
}

export function queuePageReprocessing(pageId: number) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/document-pages/${pageId}/reprocess`, { method: 'POST' })
}

export function queuePageQualityAssessment(pageId: number) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/document-pages/${pageId}/quality-assessment`, { method: 'POST' })
}

export function queuePageVisionAssessment(pageId: number) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/document-pages/${pageId}/vision-assessment`, { method: 'POST' })
}

export async function getCurrentPageTextSuggestion(pageId: number, signal?: AbortSignal) {
    try {
        return await apiRequest<DocumentPageTextSuggestion>(`/api/admin/document-pages/${pageId}/text-suggestions/current`, { signal })
    } catch (error) {
        if (error instanceof ApiError && error.status === 404) return null
        throw error
    }
}

export function listPageTextSuggestionHistory(pageId: number, page: PageRequest, signal?: AbortSignal) {
    return apiRequest<PageResponse<DocumentPageTextSuggestion>>(`/api/admin/document-pages/${pageId}/text-suggestions`, { query: page, signal })
}

export function applyPageTextSuggestionIssue(pageId: number, suggestionId: number, issueIndex: number, expectedCorrectedTextHash: string) {
    return apiRequest<DocumentPageReview>(`/api/admin/document-pages/${pageId}/text-suggestions/${suggestionId}/issues/${issueIndex}/apply`, {
        method: 'POST',
        body: { confirmed: true, expectedCorrectedTextHash },
    })
}

export function getMediaCleanupEligibility(documentId: number, signal?: AbortSignal) {
    return apiRequest<MediaCleanupEligibility>(`/api/admin/documents/${documentId}/media-cleanup/eligibility`, { signal })
}

export function scheduleMediaCleanup(documentId: number, retentionDays: number) {
    return apiRequest<MediaCleanupSchedule>(`/api/admin/documents/${documentId}/media-cleanup`, {
        method: 'POST',
        body: { policy: 'KEEP_ORIGINAL_ONLY', retentionDays },
    })
}

export function savePageTranscription(pageId: number, correctedText: string) {
    return apiRequest<DocumentPageReview>(`/api/admin/document-pages/${pageId}/transcription`, { method: 'PATCH', body: { correctedText } })
}

export function resetPageTranscriptionFromCurrentOcr(pageId: number) {
    return apiRequest<DocumentPageReview>(`/api/admin/document-pages/${pageId}/transcription/reset-from-current-ocr`, {
        method: 'POST',
        body: { confirmed: true },
    })
}

export function approvePageTranscription(pageId: number, notes: string | null) {
    return apiRequest<DocumentPageReview>(`/api/admin/document-pages/${pageId}/approve`, {
        method: 'POST',
        body: notes === null ? undefined : { notes },
    })
}

export function rejectPageTranscription(pageId: number, reason: string) {
    return apiRequest<DocumentPageReview>(`/api/admin/document-pages/${pageId}/reject`, { method: 'POST', body: { reason } })
}

export function importManualOcr(pageId: number, documentPageMediaId: number, rawText: string) {
    return apiRequest<DocumentPageOcrResult>(`/api/admin/document-pages/${pageId}/ocr-results`, { method: 'POST', body: { documentPageMediaId, rawText } })
}

export function getCurrentPageOcr(documentId: number, pageId: number, signal?: AbortSignal) {
    return apiRequest<DocumentPageOcrResult>(`/api/admin/documents/${documentId}/pages/${pageId}/ocr/current`, { signal })
}

export function listPageOcrHistory(documentId: number, pageId: number, page: PageRequest, signal?: AbortSignal) {
    return apiRequest<PageResponse<DocumentPageOcrResult>>(`/api/admin/documents/${documentId}/pages/${pageId}/ocr/history`, { query: page, signal })
}

export function listPageReviewHistory(documentId: number, pageId: number, page: PageRequest, signal?: AbortSignal) {
    return apiRequest<PageResponse<DocumentPageReview>>(`/api/admin/documents/${documentId}/pages/${pageId}/reviews`, { query: page, signal })
}

export function listPageProvenanceHistory(documentId: number, pageId: number, page: PageRequest, signal?: AbortSignal) {
    return apiRequest<PageResponse<DocumentPageProvenanceEvent>>(`/api/admin/documents/${documentId}/pages/${pageId}/provenance`, { query: page, signal })
}

export function getCurrentPageQuality(documentId: number, pageId: number, signal?: AbortSignal) {
    return apiRequest<DocumentPageQualityAssessment[]>(`/api/admin/documents/${documentId}/pages/${pageId}/quality/current`, { signal })
}

export function listPageQualityHistory(documentId: number, pageId: number, page: PageRequest, signal?: AbortSignal) {
    return apiRequest<PageResponse<DocumentPageQualityAssessment>>(`/api/admin/documents/${documentId}/pages/${pageId}/quality/history`, { query: page, signal })
}

type UploadProgress = (percentage: number) => void

function uploadDocument<TCommand>(path: string, command: TCommand, file: File, thumbnail?: File | null, onProgress?: UploadProgress) {
    return compressThumbnailIfPresent(thumbnail).then(compressedThumbnail => new Promise<DocumentUploadDetails>((resolve, reject) => {
        const body = new FormData()
        body.append('command', new Blob([JSON.stringify(command)], { type: 'application/json' }))
        body.append('file', file)
        if (compressedThumbnail) body.append('thumbnail', compressedThumbnail)

        const request = new XMLHttpRequest()
        request.open('POST', apiUrl(path))
        request.setRequestHeader('Accept', 'application/json')
        adminAuthorizationHeaders().forEach((value, name) => request.setRequestHeader(name, value))
        request.upload.addEventListener('progress', event => {
            if (event.lengthComputable) onProgress?.(Math.round(event.loaded / event.total * 100))
        })
        request.addEventListener('load', () => {
            const data = request.responseText ? safeJson(request.responseText) : null
            if (request.status >= 200 && request.status < 300) resolve(data as DocumentUploadDetails)
            else {
                handleAdminResponseStatus(request.status)
                reject(new ApiError(`Request failed with status ${request.status}`, request.status, data))
            }
        })
        request.addEventListener('error', () => reject(new Error('Network request failed')))
        request.addEventListener('abort', () => reject(new DOMException('Upload cancelled', 'AbortError')))
        request.send(body)
    }))
}

function compressThumbnailIfPresent(file?: File | null) {
    return file ? compressThumbnail(file) : Promise.resolve(null)
}

function safeJson(value: string) {
    try { return JSON.parse(value) as unknown }
    catch { return value }
}

export function uploadPdfDocument(command: PdfDocumentUploadCommand, file: File, thumbnail?: File | null, onProgress?: UploadProgress) {
    return uploadDocument('/api/admin/documents/upload/pdf', command, file, thumbnail, onProgress)
}

export function uploadStandaloneCapture(command: StandaloneCaptureUploadCommand, file: File, thumbnail?: File | null, onProgress?: UploadProgress) {
    return uploadDocument('/api/admin/documents/upload/standalone-capture', command, file, thumbnail, onProgress)
}

export async function uploadDocumentThumbnail(documentId: number, file: File) {
    const body = new FormData()
    body.append('file', await compressThumbnail(file))
    const response = await fetch(apiUrl(`/api/admin/documents/${documentId}/thumbnail`), {
        method: 'PUT',
        headers: adminAuthorizationHeaders({ Accept: 'application/json' }),
        body,
    })
    const data = response.headers.get('content-type')?.includes('application/json')
        ? await response.json()
        : await response.text()
    if (!response.ok) {
        handleAdminResponseStatus(response.status)
        throw new ApiError(`Request failed with status ${response.status}`, response.status, data)
    }
    return data as MediaAssetDetails
}

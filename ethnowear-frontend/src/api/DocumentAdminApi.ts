import type { PageRequest, PageResponse } from '../types/api'
import type {
    DocumentDetails,
    DocumentIndexingStatus,
    DocumentMetadataUpdateCommand,
    DocumentPageDetails,
    DocumentPageOcrResult,
    DocumentPageProvenanceEvent,
    DocumentPageQualityAssessment,
    DocumentPageResponse,
    DocumentPageReview,
    DocumentPageSummary,
    DocumentProcessingJob,
    DocumentProgress,
    DocumentQuery,
    DocumentSummary,
    DocumentUploadDetails,
    PdfDocumentUploadCommand,
    StandaloneCaptureUploadCommand,
} from '../types/document'
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
    pages: (documentId: number, page: PageRequest) => [...documentQueryKeys.detail(documentId), 'pages', page] as const,
    page: (documentId: number, pageId: number) => [...documentQueryKeys.detail(documentId), 'page', pageId] as const,
    jobs: (documentId: number, page: PageRequest) => [...documentQueryKeys.detail(documentId), 'jobs', page] as const,
    indexing: (documentId: number) => [...documentQueryKeys.detail(documentId), 'indexing'] as const,
}

export function listDocuments(query: DocumentQuery, signal?: AbortSignal) {
    return apiRequest<PageResponse<DocumentSummary>>('/api/admin/documents', { query, signal })
}

export function getDocument(documentId: number, signal?: AbortSignal) {
    return apiRequest<DocumentDetails>(`/api/admin/documents/${documentId}`, { signal })
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

export function listDocumentJobs(documentId: number, page: PageRequest, signal?: AbortSignal) {
    return apiRequest<PageResponse<DocumentProcessingJob>>(`/api/admin/documents/${documentId}/jobs`, { query: page, signal })
}

export function updateDocumentMetadata(documentId: number, command: DocumentMetadataUpdateCommand) {
    return apiRequest<void>(`/api/admin/documents/${documentId}/metadata`, { method: 'PUT', body: command })
}

export function queueDocumentChunkGeneration(documentId: number) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/documents/${documentId}/chunk-generation`, { method: 'POST' })
}

export function queuePageOcr(pageId: number) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/document-pages/${pageId}/ocr`, { method: 'POST' })
}

export function queuePageReprocessing(pageId: number) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/document-pages/${pageId}/reprocess`, { method: 'POST' })
}

export function queuePageQualityAssessment(pageId: number) {
    return apiRequest<DocumentProcessingJob>(`/api/admin/document-pages/${pageId}/quality-assessment`, { method: 'POST' })
}

export function savePageTranscription(pageId: number, correctedText: string) {
    return apiRequest<DocumentPageReview>(`/api/admin/document-pages/${pageId}/transcription`, { method: 'PATCH', body: { correctedText } })
}

export function approvePageTranscription(pageId: number, notes: string | null) {
    return apiRequest<DocumentPageReview>(`/api/admin/document-pages/${pageId}/approve`, { method: 'POST', body: { notes } })
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

type UploadProgress = (percentage: number) => void

function uploadDocument<TCommand>(path: string, command: TCommand, file: File, onProgress?: UploadProgress) {
    return new Promise<DocumentUploadDetails>((resolve, reject) => {
        const body = new FormData()
        body.append('command', new Blob([JSON.stringify(command)], { type: 'application/json' }))
        body.append('file', file)

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
    })
}

function safeJson(value: string) {
    try { return JSON.parse(value) as unknown }
    catch { return value }
}

export function uploadPdfDocument(command: PdfDocumentUploadCommand, file: File, onProgress?: UploadProgress) {
    return uploadDocument('/api/admin/documents/upload/pdf', command, file, onProgress)
}

export function uploadStandaloneCapture(command: StandaloneCaptureUploadCommand, file: File, onProgress?: UploadProgress) {
    return uploadDocument('/api/admin/documents/upload/standalone-capture', command, file, onProgress)
}

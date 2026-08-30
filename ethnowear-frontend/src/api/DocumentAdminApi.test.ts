import { afterEach, describe, expect, it, vi } from 'vitest'
import {
    applyPageTextSuggestionIssue,
    cancelProcessingJob,
    createPageOcrJob,
    createReplacementProcessingJob,
    deleteDocument,
    deletePageFigure,
    getDocumentPageWorkflow,
    getChunkGenerationEligibility,
    listChunkGenerationJobs,
    listGeneratedChunks,
    listDocumentFigures,
    queueDocumentChunkGeneration,
    retireDocumentPage,
    retireProcessingJob,
    retryProcessingJob,
    retryProcessingJobs,
    resetPageTranscriptionFromCurrentOcr,
    startPageImageExtraction,
    getCurrentPageTextSuggestion,
    listPageTextSuggestionHistory,
    listPageQualityHistory,
    getMediaCleanupEligibility,
    queuePageVisionAssessment,
    scheduleMediaCleanup,
    approvePageFigure,
    listPageFigures,
    reextractPageFigures,
    rejectPageFigure,
    updatePageFigure,
} from './DocumentAdminApi'

afterEach(() => vi.unstubAllGlobals())

describe('processing jobs API', () => {
    it('loads figure pages within the backend pagination limit', async () => {
        const page = { id: 11 }
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(new Response(JSON.stringify({ content: [page], totalElements: 1 }), {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
            }))
            .mockResolvedValueOnce(new Response(JSON.stringify([]), {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
            }))
        vi.stubGlobal('fetch', fetchMock)

        await listDocumentFigures(7)

        expect(fetchMock).toHaveBeenNthCalledWith(
            1,
            '/api/admin/documents/7/pages?page=0&size=100&sort=pageSequence%2Casc',
            expect.any(Object),
        )
        expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/admin/document-pages/11/figures', expect.any(Object))
    })

    it('uses optimistic locking for figure edits and review decisions', async () => {
        const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ id: 21 }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
        })))
        vi.stubGlobal('fetch', fetchMock)

        await listPageFigures(11)
        await updatePageFigure(11, 21, 'figure-v1', { correctedCaptionText: 'Caption', printedFigureNumber: 'Fig. 2', sourceReferenceId: 9 })
        await approvePageFigure(11, 21, 'figure-v2', 'Verified against the page')
        await rejectPageFigure(11, 21, 'figure-v3', 'Not a complete figure')
        await deletePageFigure(11, 21, 'figure-v4')
        await reextractPageFigures(11)

        expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
            '/api/admin/document-pages/11/figures',
            '/api/admin/document-pages/11/figures/21',
            '/api/admin/document-pages/11/figures/21/approve',
            '/api/admin/document-pages/11/figures/21/reject',
            '/api/admin/document-pages/11/figures/21',
            '/api/admin/document-pages/11/figures/reextract',
        ])
        expect((fetchMock.mock.calls[1][1]?.headers as Headers).get('If-Match')).toBe('figure-v1')
        expect((fetchMock.mock.calls[2][1]?.headers as Headers).get('If-Match')).toBe('figure-v2')
        expect((fetchMock.mock.calls[3][1]?.headers as Headers).get('If-Match')).toBe('figure-v3')
        expect((fetchMock.mock.calls[4][1]?.headers as Headers).get('If-Match')).toBe('figure-v4')
        expect(fetchMock.mock.calls[2][1]).toEqual(expect.objectContaining({ body: JSON.stringify({ reason: 'Verified against the page' }) }))
    })

    it('explicitly confirms permanent document deletion', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
        vi.stubGlobal('fetch', fetchMock)

        await deleteDocument(20002)

        expect(fetchMock).toHaveBeenCalledWith('/api/admin/documents/20002?confirm=true', expect.objectContaining({ method: 'DELETE' }))
    })

    it('uses the completed chunk-generation management endpoints', async () => {
        const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ content: [], totalElements: 0 }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
        })))
        vi.stubGlobal('fetch', fetchMock)

        await getChunkGenerationEligibility(7)
        await listChunkGenerationJobs(7, { page: 0, size: 10 })
        await listGeneratedChunks(7, { page: 1, size: 10 })
        await queueDocumentChunkGeneration(7)

        expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
            '/api/admin/documents/7/chunk-generation/eligibility',
            '/api/admin/documents/7/chunk-generation/jobs?page=0&size=10',
            '/api/admin/documents/7/generated-chunks?page=1&size=10',
            '/api/admin/documents/7/chunk-generation',
        ])
        expect(fetchMock.mock.calls[3][1]).toEqual(expect.objectContaining({ method: 'POST' }))
    })
    it('retries selected jobs with one bulk request', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ jobs: [] }), {
            status: 202,
            headers: { 'Content-Type': 'application/json' },
        }))
        vi.stubGlobal('fetch', fetchMock)

        await retryProcessingJobs([11, 12, 15])

        expect(fetchMock).toHaveBeenCalledWith('/api/admin/processing/jobs/retry', expect.objectContaining({
            method: 'POST',
            body: JSON.stringify({ jobIds: [11, 12, 15] }),
        }))
    })

    it('uses the processing lifecycle endpoints for individual actions', async () => {
        const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ id: 11 }), {
            status: 202,
            headers: { 'Content-Type': 'application/json' },
        })))
        vi.stubGlobal('fetch', fetchMock)

        await retryProcessingJob(11)
        await cancelProcessingJob(12, 'No longer required')

        expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/admin/processing/jobs/11/retry', expect.objectContaining({ method: 'POST' }))
        expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/admin/processing/jobs/12/cancel', expect.objectContaining({
            method: 'POST',
            body: JSON.stringify({ reason: 'No longer required' }),
        }))
    })

    it('creates a distinct OCR job for a page', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 44 }), {
            status: 202,
            headers: { 'Content-Type': 'application/json' },
        }))
        vi.stubGlobal('fetch', fetchMock)

        await createPageOcrJob(20099)

        expect(fetchMock).toHaveBeenCalledWith('/api/admin/document-pages/20099/ocr-jobs', expect.objectContaining({ method: 'POST' }))
    })

    it('confirms resetting corrected text from the current OCR result', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
        vi.stubGlobal('fetch', fetchMock)

        await resetPageTranscriptionFromCurrentOcr(20099)

        expect(fetchMock).toHaveBeenCalledWith('/api/admin/document-pages/20099/transcription/reset-from-current-ocr', expect.objectContaining({
            method: 'POST',
            body: JSON.stringify({ confirmed: true }),
        }))
    })

    it('loads page workflow and starts page image extraction', async () => {
        const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ steps: [] }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
        })))
        vi.stubGlobal('fetch', fetchMock)

        await getDocumentPageWorkflow(7, 20099)
        await startPageImageExtraction(7, 20099)

        expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/admin/documents/7/pages/20099/workflow', expect.any(Object))
        expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/admin/documents/7/pages/20099/image-extraction', expect.objectContaining({ method: 'POST' }))
    })

    it('sends optimistic-lock headers when retiring a page or job', async () => {
        const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ id: 1 }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
        })))
        vi.stubGlobal('fetch', fetchMock)

        await retireDocumentPage(7, 20099, 'page-version', 'Duplicate page')
        await retireProcessingJob(44, 'job-version', 'Obsolete job')

        expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/admin/documents/7/pages/20099?reason=Duplicate+page', expect.objectContaining({ method: 'DELETE' }))
        expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/admin/processing/jobs/44?reason=Obsolete+job', expect.objectContaining({ method: 'DELETE' }))
        expect((fetchMock.mock.calls[0][1]?.headers as Headers).get('If-Match')).toBe('page-version')
        expect((fetchMock.mock.calls[1][1]?.headers as Headers).get('If-Match')).toBe('job-version')
    })

    it('creates a replacement job without retrying the previous job', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 45 }), {
            status: 202,
            headers: { 'Content-Type': 'application/json' },
        }))
        vi.stubGlobal('fetch', fetchMock)

        await createReplacementProcessingJob(44, 'job-version')

        expect(fetchMock).toHaveBeenCalledOnce()
        expect(fetchMock).toHaveBeenCalledWith('/api/admin/processing/jobs/44/replacement', expect.objectContaining({ method: 'POST' }))
        expect((fetchMock.mock.calls[0][1]?.headers as Headers).get('If-Match')).toBe('job-version')
    })

    it('uses advisory vision endpoints and applies one explicitly confirmed issue', async () => {
        const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ id: 91, suggestedText: 'text' }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
        })))
        vi.stubGlobal('fetch', fetchMock)

        await queuePageVisionAssessment(20099)
        await getCurrentPageTextSuggestion(20099)
        await listPageTextSuggestionHistory(20099, { page: 0, size: 20 })
        await listPageQualityHistory(7, 20099, { page: 0, size: 20 })
        await applyPageTextSuggestionIssue(20099, 51, 2, 'a'.repeat(64))

        expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
            '/api/admin/document-pages/20099/vision-assessment',
            '/api/admin/document-pages/20099/text-suggestions/current',
            '/api/admin/document-pages/20099/text-suggestions?page=0&size=20',
            '/api/admin/documents/7/pages/20099/quality/history?page=0&size=20',
            '/api/admin/document-pages/20099/text-suggestions/51/issues/2/apply',
        ])
        expect(fetchMock.mock.calls.some(call => String(call[0]).endsWith('/approve'))).toBe(false)
        expect(fetchMock.mock.calls[4][1]).toEqual(expect.objectContaining({
            method: 'POST',
            body: JSON.stringify({ confirmed: true, expectedCorrectedTextHash: 'a'.repeat(64) }),
        }))
    })

    it('treats a missing current vision suggestion as an empty optional result', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 404 })))
        await expect(getCurrentPageTextSuggestion(20099)).resolves.toBeNull()
    })

    it('checks and schedules generated-media cleanup while preserving the policy', async () => {
        const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ eligible: true }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
        })))
        vi.stubGlobal('fetch', fetchMock)

        await getMediaCleanupEligibility(7)
        await scheduleMediaCleanup(7, 30)

        expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/admin/documents/7/media-cleanup/eligibility', expect.any(Object))
        expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/admin/documents/7/media-cleanup', expect.objectContaining({
            method: 'POST',
            body: JSON.stringify({ policy: 'KEEP_ORIGINAL_ONLY', retentionDays: 30 }),
        }))
    })
})

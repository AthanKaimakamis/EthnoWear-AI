import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as documentApi from '../../../api/DocumentAdminApi'
import { ApiError } from '../../../api/http'
import i18n from '../../../app/i18n'
import { renderApp } from '../../../test/render'
import type { DocumentJobStatus, DocumentPageQualityAssessment, DocumentPageTextSuggestion, DocumentPageWorkflowProgress, ProcessingJobSummary } from '../../../types/document'
import VisionAssessmentPanel, { VisionAssessmentHistoryPanel } from './VisionAssessmentPanel'

vi.mock('../../../api/DocumentAdminApi', async importOriginal => ({
    ...await importOriginal<typeof import('../../../api/DocumentAdminApi')>(),
    getCurrentPageTextSuggestion: vi.fn(),
    getCurrentPageOcr: vi.fn(),
    listPageTextSuggestionHistory: vi.fn(),
    listPageQualityHistory: vi.fn(),
    listProcessingJobs: vi.fn(),
    queuePageVisionAssessment: vi.fn(),
    applyPageTextSuggestionIssue: vi.fn(),
    retryProcessingJob: vi.fn(),
    createReplacementProcessingJob: vi.fn(),
}))

const completedWorkflow: DocumentPageWorkflowProgress = {
    documentId: 7,
    pageId: 42,
    completedSteps: 4,
    totalSteps: 5,
    steps: [{ step: 'VISION_OCR_ASSESSMENT', status: 'COMPLETED', jobId: 91, jobStatus: 'SUCCEEDED', message: null }],
}

const rejectedQuality = {
    id: 8,
    documentPageId: 42,
    documentPageMediaId: 5,
    processingJobId: 91,
    documentPageOcrResultId: 4,
    assessmentType: 'VISION_TEXT_COMPARISON',
    assessorType: 'VISION_MODEL',
    assessorName: null,
    assessorVersion: null,
    scoreVersion: null,
    qualityStatus: 'REVIEW_REQUIRED',
    overallScore: .5,
    percentage: 50,
    summary: null,
    limitations: null,
    current: true,
    signals: [{ id: 1, assessmentId: 8, signalType: 'VISION_SUGGESTION_REJECTED', signalOrdinal: 1, signalValueDecimal: null, signalValueText: 'number_grounding', severity: 'WARNING', weight: 1, message: 'Rejected', createdAt: '2026-08-26T10:00:00Z', updatedAt: '2026-08-26T10:00:00Z' }],
    passedChecks: [],
    failedChecks: [],
    createdAt: '2026-08-26T10:00:00Z',
    updatedAt: '2026-08-26T10:00:00Z',
} satisfies DocumentPageQualityAssessment

function renderPanel(
    workflow = completedWorkflow,
    visionQuality: DocumentPageQualityAssessment | null = rejectedQuality,
    currentText = 'CURRENT OCR',
    editableText = currentText,
    onEditableTextChange = vi.fn(),
    onOpenTextEditor = vi.fn(),
) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return { ...renderApp(<QueryClientProvider client={client}><VisionAssessmentPanel documentId={7} pageId={42} workflow={workflow} visionQuality={visionQuality ?? undefined} canProcess canApplySuggestions hasUnsavedChanges={editableText !== currentText} currentText={currentText} editableText={editableText} onEditableTextChange={onEditableTextChange} onOpenTextEditor={onOpenTextEditor} /></QueryClientProvider>), client, onEditableTextChange, onOpenTextEditor }
}

function renderHistory() {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return renderApp(<QueryClientProvider client={client}><VisionAssessmentHistoryPanel documentId={7} pageId={42} /></QueryClientProvider>)
}

function job(id: number, status: DocumentJobStatus, errorCode: string | null = null): ProcessingJobSummary {
    const terminal = ['SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT', 'DEAD'].includes(status)
    return {
        id, previousJobId: null, jobType: 'VISION_OCR_ASSESSMENT', status,
        purpose: { type: 'VISION_OCR_ASSESSMENT', code: 'VISION_OCR_ASSESSMENT' },
        target: { type: 'DOCUMENT_PAGE', documentId: 7, documentPageId: 42, inputMediaAssetId: 5, knowledgeChunkId: null },
        priority: 0,
        progress: { attemptCount: 1, maxAttempts: 3, attemptsRemaining: 2, retryable: status === 'FAILED', cancellable: !terminal, terminal },
        timestamps: { availableAt: null, claimedAt: null, startedAt: null, finishedAt: terminal ? '2026-08-26T10:01:00Z' : null, timeoutAt: null, createdAt: `2026-08-26T10:00:${id % 60}.000Z`, updatedAt: '2026-08-26T10:01:00Z' },
        error: errorCode ? { code: errorCode, message: 'RAW BACKEND DETAILS' } : null,
        cancellationReason: null,
        document: { id: 7, title: 'Document', language: 'bg' },
        page: { id: 42, pageSequence: 1, pdfPageIndex: 1, printedPageNumber: null, pageLabel: null },
        inputMediaAssetId: 5, knowledgeChunkId: null, attempts: [],
        result: { producedMediaAssetIds: [], ocrResultId: 4, qualityAssessment: null },
        capabilities: { retryable: status === 'FAILED', cloneable: terminal, cancellable: !terminal, deletable: terminal },
        retirement: { retired: false, retiredAt: null, retiredBy: null, reason: null },
        versionToken: 'v1',
    }
}

function suggestion(id: number, processingJobId: number, overrides: Partial<DocumentPageTextSuggestion> = {}): DocumentPageTextSuggestion {
    return {
        id, documentPageId: 42, documentPageMediaId: 5, documentPageOcrResultId: 4, processingJobId,
        suggestedText: 'UNCHANGED PAGE OCR', modelName: 'model', modelVersion: null, promptVersion: 'vision-ocr-v2',
        requiresReview: true, editableTextHash: 'a'.repeat(64), issues: [], uncertainPassages: [], applied: false, requiresHumanAttention: true,
        createdAt: '2026-08-26T10:00:00Z', ...overrides,
    }
}

function page<T>(content: T[], number = 0, totalPages = 1) {
    return { content, number, size: 20, totalElements: content.length, totalPages, first: number === 0, last: number + 1 >= totalPages, numberOfElements: content.length, empty: content.length === 0 }
}

beforeEach(() => {
    vi.clearAllMocks()
    void i18n.changeLanguage('bg')
    vi.mocked(documentApi.listProcessingJobs).mockResolvedValue(page([]))
    vi.mocked(documentApi.listPageTextSuggestionHistory).mockResolvedValue(page([]))
    vi.mocked(documentApi.listPageQualityHistory).mockResolvedValue(page([]))
    vi.mocked(documentApi.getCurrentPageOcr).mockResolvedValue({ id: 4, documentPageId: 42, documentPageMediaId: 5, processingJobId: 90, rawText: 'CURRENT OCR', ocrEngine: 'Tesseract', ocrEngineVersion: '5', ocrLanguage: 'bg', ocrConfidence: .9, current: true, createdAt: '2026-08-26T10:00:00Z', updatedAt: '2026-08-26T10:00:00Z' })
    vi.mocked(documentApi.queuePageVisionAssessment).mockResolvedValue({ id: 99, status: 'QUEUED' } as never)
    vi.mocked(documentApi.applyPageTextSuggestionIssue).mockResolvedValue({
        id: 81, documentPageId: 42, reviewAction: 'CORRECTED_TEXT_SAVED', reviewer: 'admin', correctedTextSnapshot: 'Това е поправка в текста.', previousReviewState: 'REVIEW_REQUIRED', newReviewState: 'REVIEW_REQUIRED', previousApprovalState: 'PENDING', newApprovalState: 'PENDING', reason: null, createdAt: '2026-08-26T10:02:00Z',
    })
    vi.mocked(documentApi.getCurrentPageTextSuggestion).mockResolvedValue(suggestion(12, 91, {
        issues: [
            { issueType: 'WORD', explanationBg: 'Възможна OCR грешка', originalText: 'грешен откъс', originalContext: null, suggestedText: 'поправен откъс', suggestedContext: null, startOffset: null, endOffset: null, safelyApplicable: false, confidence: .91 },
            { issueType: 'UNCERTAIN', explanationBg: 'Изображението е неясно', originalText: 'неясен откъс', originalContext: null, suggestedText: null, suggestedContext: null, startOffset: null, endOffset: null, safelyApplicable: false, confidence: .62 },
        ],
    }))
})

afterEach(() => vi.useRealTimers())

describe('VisionAssessmentPanel', () => {
    it('shows issue guidance without exposing or applying page-level suggested text', async () => {
        renderPanel()

        expect(await screen.findByText('Предложението на модела беше отхвърлено от защитна проверка.')).toBeInTheDocument()
        expect(await screen.findByText('грешен откъс')).toBeInTheDocument()
        expect(screen.getByRole('textbox', { name: 'Редактирано предложение' })).toHaveValue('поправен откъс')
        expect(screen.getByText('Моделът откри проблем, но не можа да предложи надеждна корекция.')).toBeInTheDocument()
        expect(screen.queryByText('UNCHANGED PAGE OCR')).not.toBeInTheDocument()
        expect(screen.queryByRole('button', { name: /apply/i })).not.toBeInTheDocument()
    })

    it('treats v3 and later prompt versions as structured issue results', async () => {
        vi.mocked(documentApi.getCurrentPageTextSuggestion).mockResolvedValue(suggestion(18, 91, {
            promptVersion: 'vision-ocr-v3',
            suggestedText: 'PAGE LEVEL TEXT MUST NOT BE SHOWN',
            issues: [
                { issueType: 'WORD', explanationBg: 'Първи проблем', originalText: 'едно', originalContext: null, suggestedText: 'първо', suggestedContext: null, startOffset: null, endOffset: null, safelyApplicable: false, confidence: .91 },
                { issueType: 'WORD', explanationBg: 'Втори проблем', originalText: 'две', originalContext: null, suggestedText: 'второ', suggestedContext: null, startOffset: null, endOffset: null, safelyApplicable: false, confidence: .82 },
                { issueType: 'UNCERTAIN', explanationBg: 'Трети проблем', originalText: 'три', originalContext: null, suggestedText: null, suggestedContext: null, startOffset: null, endOffset: null, safelyApplicable: false, confidence: .63 },
            ],
            uncertainPassages: [{ excerpt: 'Неясен откъс от страницата', reason: 'Ниска четливост', confidence: .55 }],
        }))

        renderPanel()

        expect(await screen.findByText('Проблем 1')).toBeInTheDocument()
        expect(screen.getByText('Проблем 2')).toBeInTheDocument()
        expect(screen.getByText('Проблем 3')).toBeInTheDocument()
        expect(screen.getByText('Моделът откри проблем, но не можа да предложи надеждна корекция.')).toBeInTheDocument()
        expect(screen.getByText('Неясен откъс от страницата')).toBeInTheDocument()
        expect(screen.queryByText('PAGE LEVEL TEXT MUST NOT BE SHOWN')).not.toBeInTheDocument()
        expect(screen.queryByText('Предложение от предишната версия')).not.toBeInTheDocument()
    })

    it('localizes the known OCR reason in Bulgarian', async () => {
        vi.mocked(documentApi.getCurrentPageTextSuggestion).mockResolvedValue(suggestion(12, 91, {
            issues: [{
                issueType: 'WORD',
                explanationBg: 'OCR не разпозна думата „на“. Вероятната причина е ниско качество на изображението или грешка при разпознаването.',
                originalText: 'на',
                originalContext: null,
                suggestedText: null,
                suggestedContext: null,
                startOffset: null,
                endOffset: null,
                safelyApplicable: false,
                confidence: .82,
            }],
        }))

        renderPanel()

        expect(await screen.findByText('OCR не разпозна думата „на“. Вероятната причина е ниско качество на изображението или грешка при разпознаването.')).toBeInTheDocument()
        expect(screen.queryByText("OCR missed the 'na' character. Likely due to poor image quality or OCR error.")).not.toBeInTheDocument()
    })

    it('applies a safe correction only to the editable draft', async () => {
        const user = userEvent.setup()
        const onChange = vi.fn()
        const text = 'Това е грешка в текста.'
        vi.mocked(documentApi.getCurrentPageOcr).mockResolvedValue({ ...await documentApi.getCurrentPageOcr(7, 42), rawText: text })
        vi.mocked(documentApi.getCurrentPageTextSuggestion).mockResolvedValue(suggestion(14, 91, {
            issues: [{ issueType: 'OCR_WORD', explanationBg: 'Възможна OCR грешка', originalText: 'грешка', originalContext: 'Това е грешка в текста.', suggestedText: 'поправка', suggestedContext: 'Това е поправка в текста.', startOffset: 7, endOffset: 13, safelyApplicable: true, confidence: .96 }],
        }))
        renderPanel(completedWorkflow, null, text, text, onChange)

        await user.click(await screen.findByRole('button', { name: 'Приложи корекцията' }))
        expect(documentApi.applyPageTextSuggestionIssue).not.toHaveBeenCalled()
        await user.click(await screen.findByRole('button', { name: 'Приложи' }))

        await waitFor(() => expect(documentApi.applyPageTextSuggestionIssue).toHaveBeenCalledWith(42, 14, 0, 'a'.repeat(64)))
        expect(onChange).toHaveBeenCalledWith('Това е поправка в текста.')
    })

    it('allows a reviewer to adjust a proposed replacement before adding it to the draft', async () => {
        const user = userEvent.setup()
        const onChange = vi.fn()
        const text = 'Това е грешка в текста.'
        vi.mocked(documentApi.getCurrentPageOcr).mockResolvedValue({ ...await documentApi.getCurrentPageOcr(7, 42), rawText: text })
        vi.mocked(documentApi.getCurrentPageTextSuggestion).mockResolvedValue(suggestion(14, 91, {
            issues: [{ issueType: 'OCR_WORD', explanationBg: 'Проверете думата', originalText: 'грешка', originalContext: text, suggestedText: 'поправка', suggestedContext: 'Това е поправка в текста.', startOffset: 7, endOffset: 13, safelyApplicable: false, confidence: .8 }],
        }))
        renderPanel(completedWorkflow, null, text, text, onChange)

        const replacement = await screen.findByRole('textbox', { name: 'Редактирано предложение' })
        await user.clear(replacement)
        await user.type(replacement, 'корекция')
        await user.click(screen.getByRole('button', { name: 'Добави в коригирания текст' }))

        expect(onChange).toHaveBeenCalledWith('Това е корекция в текста.')
        expect(documentApi.applyPageTextSuggestionIssue).not.toHaveBeenCalled()
        expect(screen.getByText('Корекцията е добавена в полето за коригиран текст. Тя все още не е запазена или одобрена.')).toBeInTheDocument()
    })

    it('opens a no-replacement issue in the text editor without inventing text', async () => {
        const user = userEvent.setup()
        const openEditor = vi.fn()
        const text = 'Това е неясен откъс.'
        vi.mocked(documentApi.getCurrentPageOcr).mockResolvedValue({ ...await documentApi.getCurrentPageOcr(7, 42), rawText: text })
        vi.mocked(documentApi.getCurrentPageTextSuggestion).mockResolvedValue(suggestion(14, 91, {
            issues: [{ issueType: 'UNCERTAIN', explanationBg: 'Неясен печат', originalText: 'неясен', originalContext: text, suggestedText: null, suggestedContext: null, startOffset: 7, endOffset: 13, safelyApplicable: false, confidence: .6 }],
        }))
        renderPanel(completedWorkflow, null, text, text, vi.fn(), openEditor)

        await user.click(await screen.findByRole('button', { name: 'Отвори в текстовия редактор' }))

        expect(openEditor).toHaveBeenCalledWith(7, 13, 'неясен')
        expect(screen.queryByText('∅')).not.toBeInTheDocument()
    })

    it('preserves the editor and refreshes evidence after a stale apply conflict', async () => {
        const user = userEvent.setup()
        const onChange = vi.fn()
        const text = 'Това е грешка в текста.'
        vi.mocked(documentApi.getCurrentPageOcr).mockResolvedValue({ ...await documentApi.getCurrentPageOcr(7, 42), rawText: text })
        vi.mocked(documentApi.getCurrentPageTextSuggestion).mockResolvedValue(suggestion(14, 91, {
            issues: [{ issueType: 'OCR_WORD', explanationBg: 'Възможна OCR грешка', originalText: 'грешка', originalContext: text, suggestedText: 'поправка', suggestedContext: 'Това е поправка в текста.', startOffset: 7, endOffset: 13, safelyApplicable: true, confidence: .96 }],
        }))
        vi.mocked(documentApi.applyPageTextSuggestionIssue).mockRejectedValue(new ApiError('stale', 409, { code: 'TEXT_SUGGESTION_STALE' }))
        renderPanel(completedWorkflow, null, text, text, onChange)

        await user.click(await screen.findByRole('button', { name: 'Приложи корекцията' }))
        await user.click(await screen.findByRole('button', { name: 'Приложи' }))

        expect(await screen.findByText('Текстът или доказателството са променени. Данните са обновени, а вашият незапазен текст е запазен.')).toBeInTheDocument()
        expect(onChange).not.toHaveBeenCalled()
    })

    it('allows an issue to be ignored without changing the draft', async () => {
        const user = userEvent.setup()
        const onChange = vi.fn()
        renderPanel(completedWorkflow, null, 'CURRENT OCR', 'CURRENT OCR', onChange)

        await user.click((await screen.findAllByRole('button', { name: 'Игнорирай' }))[0])

        expect(screen.getByText('Проблем 1 е игнориран.')).toBeInTheDocument()
        expect(onChange).not.toHaveBeenCalled()
    })

    it('disables application when the assessed OCR snapshot is stale', async () => {
        const text = 'Това е грешка.'
        vi.mocked(documentApi.getCurrentPageOcr).mockResolvedValue({ ...await documentApi.getCurrentPageOcr(7, 42), id: 5, rawText: text })
        vi.mocked(documentApi.getCurrentPageTextSuggestion).mockResolvedValue(suggestion(15, 91, {
            documentPageOcrResultId: 4,
            issues: [{ issueType: 'OCR_WORD', explanationBg: 'Възможна OCR грешка', originalText: 'грешка', originalContext: null, suggestedText: 'поправка', suggestedContext: null, startOffset: 7, endOffset: 13, safelyApplicable: true, confidence: .96 }],
        }))
        renderPanel(completedWorkflow, null, text, text)

        expect(await screen.findByText('Сравнението е направено върху предишен OCR резултат. Предложените корекции не могат да бъдат прилагани.')).toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Приложи корекцията' })).toBeDisabled()
    })

    it('retries a truncated failed job only when the backend marks it retryable', async () => {
        const user = userEvent.setup()
        vi.mocked(documentApi.getCurrentPageTextSuggestion).mockResolvedValue(null)
        vi.mocked(documentApi.listProcessingJobs).mockResolvedValue(page([job(92, 'FAILED', 'VISION_RESPONSE_TRUNCATED')]))
        renderPanel({ ...completedWorkflow, steps: [{ step: 'VISION_OCR_ASSESSMENT', status: 'FAILED', jobId: 92, jobStatus: 'FAILED', message: null }] }, null)

        const retry = await screen.findByRole('button', { name: 'ПОВТОРИ ЗАДАЧАТА' })
        expect(screen.getByText('Визуалният модел достигна лимита на отговора. Задачата може да бъде повторена.')).toBeInTheDocument()
        expect(screen.queryByText('RAW DETAILS')).not.toBeInTheDocument()
        await user.click(retry)

        await waitFor(() => expect(documentApi.retryProcessingJob).toHaveBeenCalledWith(92))
    })

    it('allows a legacy v1 suggestion to be copied into the editable draft only', async () => {
        const user = userEvent.setup()
        const onChange = vi.fn()
        vi.mocked(documentApi.getCurrentPageTextSuggestion).mockResolvedValue({
            id: 13, documentPageId: 42, documentPageMediaId: 5, documentPageOcrResultId: 4, processingJobId: 91,
            suggestedText: 'LEGACY CORRECTED TEXT', modelName: 'legacy', modelVersion: null, promptVersion: 'vision-ocr-v1',
            requiresReview: true, editableTextHash: 'a'.repeat(64), issues: [], uncertainPassages: [], applied: false, requiresHumanAttention: true, createdAt: '2026-08-26T10:00:00Z',
        })

        renderPanel(completedWorkflow, null, 'CURRENT OCR', 'CURRENT OCR', onChange)

        expect(await screen.findByText('Предложение от предишната версия')).toBeInTheDocument()
        expect(screen.getByRole('region', { name: 'Текущ OCR текст' })).toHaveTextContent('CURRENT OCR')
        expect(screen.getByRole('region', { name: 'Предложен текст' })).toHaveTextContent('LEGACY CORRECTED TEXT')
        await user.click(screen.getByRole('button', { name: 'Приложи корекцията' }))
        expect(onChange).toHaveBeenCalledWith('LEGACY CORRECTED TEXT')
        expect(documentApi.applyPageTextSuggestionIssue).not.toHaveBeenCalled()
    })

    it('creates only one request after a rapid double-click', async () => {
        const user = userEvent.setup()
        let resolveRequest!: (value: never) => void
        vi.mocked(documentApi.queuePageVisionAssessment).mockImplementation(() => new Promise(resolve => { resolveRequest = resolve }))
        renderPanel()

        const button = await screen.findByRole('button', { name: 'Стартирай ново ръчно сравнение' })
        await user.dblClick(button)

        expect(documentApi.queuePageVisionAssessment).toHaveBeenCalledTimes(1)
        await act(async () => resolveRequest({ id: 99, status: 'QUEUED' } as never))
    })

    it.each(['QUEUED', 'RETRY_WAIT', 'RUNNING', 'CANCEL_REQUESTED'] as const)('keeps the manual action disabled while the latest job is %s', async status => {
        vi.mocked(documentApi.listProcessingJobs).mockResolvedValue(page([job(99, status)]))
        renderPanel()

        await waitFor(() => expect(screen.getByRole('button', { name: 'Стартирай ново ръчно сравнение' })).toBeDisabled())
        expect(screen.getByText('Ръчно заявеното визуално сравнение се изпълнява.')).toBeInTheDocument()
    })

    it.each(['SUCCEEDED', 'FAILED'] as const)('allows an intentional new manual attempt after %s', async status => {
        const user = userEvent.setup()
        vi.mocked(documentApi.listProcessingJobs).mockResolvedValue(page([job(98, status)]))
        renderPanel()

        const button = await screen.findByRole('button', { name: 'Стартирай ново ръчно сравнение' })
        expect(button).toBeEnabled()
        await user.click(button)

        expect(documentApi.queuePageVisionAssessment).toHaveBeenCalledTimes(1)
    })

    it('recovers a 409 conflict by showing and tracking the existing active job', async () => {
        const user = userEvent.setup()
        vi.mocked(documentApi.queuePageVisionAssessment).mockRejectedValue(new ApiError('conflict', 409, {
            code: 'VISION_JOB_ALREADY_ACTIVE',
            message: 'RAW BACKEND CONFLICT',
            activeJob: { id: 707, status: 'RUNNING', documentPageId: 42, ocrResultId: 4 },
        }))
        renderPanel()

        await user.click(await screen.findByRole('button', { name: 'Стартирай ново ръчно сравнение' }))

        expect(await screen.findByText('Ръчно заявеното визуално сравнение се изпълнява.')).toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Стартирай ново ръчно сравнение' })).toBeDisabled()
        expect(screen.queryByText('RAW BACKEND CONFLICT')).not.toBeInTheDocument()
    })

    it('polls active work and enables a new attempt after the job becomes terminal', async () => {
        vi.useFakeTimers()
        vi.mocked(documentApi.listProcessingJobs)
            .mockResolvedValueOnce(page([job(99, 'RUNNING')]))
            .mockResolvedValue(page([job(99, 'SUCCEEDED')]))
        renderPanel()

        await act(async () => { await vi.advanceTimersByTimeAsync(0) })
        expect(screen.getByRole('button', { name: 'Стартирай ново ръчно сравнение' })).toBeDisabled()

        await act(async () => { await vi.advanceTimersByTimeAsync(4_001) })

        await act(async () => { await Promise.resolve() })
        expect(screen.getByRole('button', { name: 'Стартирай ново ръчно сравнение' })).toBeEnabled()
        expect(vi.mocked(documentApi.listProcessingJobs).mock.calls.length).toBeGreaterThanOrEqual(2)
    })

    it('keeps the latest attempt prominent and preserves earlier attempts in history', async () => {
        vi.mocked(documentApi.listProcessingJobs).mockResolvedValue(page([
            job(103, 'SUCCEEDED'),
            job(102, 'SUCCEEDED'),
            job(101, 'FAILED', 'VISION_RESPONSE_TRUNCATED'),
        ]))
        vi.mocked(documentApi.getCurrentPageTextSuggestion).mockResolvedValue(suggestion(13, 103))
        vi.mocked(documentApi.listPageTextSuggestionHistory).mockResolvedValue(page([
            suggestion(13, 103),
            suggestion(12, 102, { applied: true }),
        ]))
        renderHistory()

        expect(await screen.findByText('Последен опит')).toBeInTheDocument()
        expect(screen.queryByText(/Задача #/)).not.toBeInTheDocument()
        await userEvent.setup().click(screen.getByRole('button', { name: 'Покажи предишните опити (2)' }))
        expect(screen.getByText('Предложението е използвано при ръчна корекция.')).toBeInTheDocument()
        expect(screen.getByText('Прекъснат отговор от визуалния модел')).toBeInTheDocument()
    })

    it('loads older manual attempts without replacing the visible history', async () => {
        const user = userEvent.setup()
        vi.mocked(documentApi.listProcessingJobs)
            .mockResolvedValueOnce(page([job(103, 'SUCCEEDED'), job(102, 'SUCCEEDED')], 0, 2))
            .mockResolvedValueOnce(page([job(101, 'FAILED', 'VISION_RESPONSE_TRUNCATED')], 1, 2))
        renderHistory()

        expect(await screen.findByText('Последен опит')).toBeInTheDocument()
        expect(screen.queryByText('Прекъснат отговор от визуалния модел')).not.toBeInTheDocument()
        await user.click(screen.getByRole('button', { name: 'Покажи предишните опити (1)' }))
        await user.click(screen.getByRole('button', { name: 'Покажи по-стари опити' }))

        expect(await screen.findByText('Прекъснат отговор от визуалния модел')).toBeInTheDocument()
        expect(screen.queryByText(/Задача #/)).not.toBeInTheDocument()
    })
})

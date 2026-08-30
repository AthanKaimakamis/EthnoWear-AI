import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '../../../app/i18n'
import { renderApp } from '../../../test/render'
import type { ProcessingJobSummary } from '../../../types/document'
import ProcessingJobDetailsDialog from './ProcessingJobDetailsDialog'

function job(overrides: Partial<ProcessingJobSummary> = {}): ProcessingJobSummary {
    return {
        id: 44,
        previousJobId: 21,
        jobType: 'OCR',
        status: 'FAILED',
        purpose: { type: 'OCR', code: 'OCR' },
        target: { type: 'DOCUMENT_PAGE', documentId: 7, documentPageId: 42, inputMediaAssetId: 5, knowledgeChunkId: null },
        priority: 1,
        progress: { attemptCount: 2, maxAttempts: 3, attemptsRemaining: 1, retryable: true, cancellable: false, terminal: true },
        timestamps: { availableAt: '2026-08-27T08:00:00Z', claimedAt: '2026-08-27T08:01:00Z', startedAt: '2026-08-27T08:02:00Z', finishedAt: '2026-08-27T08:03:00Z', timeoutAt: null, createdAt: '2026-08-27T07:59:00Z', updatedAt: '2026-08-27T08:03:00Z' },
        error: { code: 'OCR_LAYOUT_INVALID', message: '/private/path stack trace secret-token' },
        cancellationReason: null,
        document: { id: 7, title: 'Документ', language: 'bg' },
        page: { id: 42, pageSequence: 8, pdfPageIndex: 7, printedPageNumber: '12', pageLabel: null },
        inputMediaAssetId: 5,
        knowledgeChunkId: null,
        attempts: [{ id: 1, executionNumber: 1, attemptNumber: 2, status: 'FAILED', worker: 'worker', claimedAt: null, startedAt: '2026-08-27T08:02:00Z', finishedAt: '2026-08-27T08:03:00Z', processorName: null, processorVersion: null, toolName: null, toolVersion: null, error: { code: 'OCR_LAYOUT_INVALID', message: 'raw model response' }, cancellationReason: null }],
        result: { producedMediaAssetIds: [], ocrResultId: null, qualityAssessment: null },
        capabilities: { retryable: true, cloneable: true, cancellable: false, deletable: true },
        retirement: { retired: false, retiredAt: null, retiredBy: null, reason: null },
        versionToken: 'v1',
        ...overrides,
    }
}

function renderDialog(value: ProcessingJobSummary) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return renderApp(<QueryClientProvider client={client}><ProcessingJobDetailsDialog job={value} language="bg" canMutate onClose={vi.fn()} onChanged={vi.fn()} /></QueryClientProvider>)
}

beforeEach(async () => {
    await i18n.changeLanguage('bg')
})

describe('ProcessingJobDetailsDialog', () => {
    it('shows safe localized details and never renders raw backend errors', () => {
        renderDialog(job())

        expect(screen.getByText('Разпознаване на текст')).toBeInTheDocument()
        expect(screen.getByText('Опит 2 от 3')).toBeInTheDocument()
        expect(screen.getAllByText('OCR_LAYOUT_INVALID').length).toBeGreaterThan(0)
        expect(screen.getAllByText('Оформлението на страницата не може да бъде обработено безопасно.').length).toBeGreaterThan(0)
        expect(screen.queryByText(/private\/path|stack trace|secret-token|raw model response/i)).not.toBeInTheDocument()
    })

    it('offers replacement instead of retry for a non-retryable failed job', () => {
        renderDialog(job({ progress: { attemptCount: 3, maxAttempts: 3, attemptsRemaining: 0, retryable: false, cancellable: false, terminal: true }, capabilities: { retryable: false, cloneable: true, cancellable: false, deletable: true } }))

        expect(screen.getByRole('button', { name: 'СЪЗДАЙ ЗАМЕСТВАЩА ЗАДАЧА' })).toBeInTheDocument()
        expect(screen.queryByRole('button', { name: 'ПОВТОРИ ЗАДАЧАТА' })).not.toBeInTheDocument()
    })

    it('shows no retry or replacement during automatic retry wait', () => {
        renderDialog(job({ status: 'RETRY_WAIT', capabilities: { retryable: true, cloneable: true, cancellable: true, deletable: false } }))

        expect(screen.getAllByText('ИЗЧАКВА АВТОМАТИЧЕН ПОВТОРЕН ОПИТ').length).toBeGreaterThan(0)
        expect(screen.queryByRole('button', { name: 'ПОВТОРИ ЗАДАЧАТА' })).not.toBeInTheDocument()
        expect(screen.queryByRole('button', { name: 'СЪЗДАЙ ЗАМЕСТВАЩА ЗАДАЧА' })).not.toBeInTheDocument()
    })
})

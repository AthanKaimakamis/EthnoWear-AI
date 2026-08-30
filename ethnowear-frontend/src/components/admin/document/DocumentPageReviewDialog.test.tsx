import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as documentApi from '../../../api/DocumentAdminApi'
import { sourceReferencesApi, sourcesApi } from '../../../api/ArchiveAdminApi'
import { AdminAuthContext } from '../../../app/adminAuth'
import { renderApp } from '../../../test/render'
import type { DocumentPageDetails, DocumentPageFigure } from '../../../types/document'
import DocumentPageReviewDialog from './DocumentPageReviewDialog'

vi.mock('../../../api/DocumentAdminApi', async importOriginal => ({
    ...await importOriginal<typeof import('../../../api/DocumentAdminApi')>(),
    getDocument: vi.fn(), getDocumentPage: vi.fn(), getCurrentPageQuality: vi.fn(), getDocumentPageWorkflow: vi.fn(),
    savePageTranscription: vi.fn(), approvePageTranscription: vi.fn(), rejectPageTranscription: vi.fn(),
    resetPageTranscriptionFromCurrentOcr: vi.fn(), queuePageReprocessing: vi.fn(),
    retireDocumentPage: vi.fn(), startPageImageExtraction: vi.fn(),
    updateDocumentPageMetadata: vi.fn(), changePageSourceProvenance: vi.fn(), changePageProvenanceTrust: vi.fn(),
    listPageFigures: vi.fn(), getPageFigureContent: vi.fn(), updatePageFigure: vi.fn(),
    approvePageFigure: vi.fn(), rejectPageFigure: vi.fn(), reextractPageFigures: vi.fn(),
}))
vi.mock('../../../api/ArchiveAdminApi', async importOriginal => ({
    ...await importOriginal<typeof import('../../../api/ArchiveAdminApi')>(),
    sourceReferencesApi: { findAll: vi.fn() }, sourcesApi: { findAll: vi.fn() },
}))

const detail: DocumentPageDetails = {
    summary: {
        id: 42, documentId: 7, sourceReferenceId: 3, pageKind: 'DOCUMENT_PAGE', pageRole: 'NORMAL', pageSequence: 1, pdfPageIndex: 0, printedPageNumber: '1', printedPageSort: 1, pageLabel: null,
        provenanceStatus: 'KNOWN_SOURCE', processingState: 'COMPLETED', reviewState: 'APPROVED', transcriptionApprovalState: 'APPROVED', provenanceTrustState: 'VERIFIED', indexingState: 'INDEXED', evidenceState: 'ACTIVE', ocrConfidence: .91,
        hasRawOcrText: true, hasCorrectedText: true, previewMediaAssetId: 5, quality: null, createdAt: '2026-08-24T10:00:00Z', updatedAt: '2026-08-24T10:00:00Z', retirement: { retired: false, retiredAt: null, retiredBy: null, reason: null }, versionToken: 'v1',
    },
    rawOcrText: 'Raw OCR', correctedText: 'Approved corrected text', ocrEngine: 'Tesseract', ocrEngineVersion: '5', ocrLanguage: 'bg', reviewer: 'reviewer', reviewedAt: '2026-08-24T10:00:00Z', reviewNotes: null, canonicalDocumentPageId: null, provenanceNote: null, provenanceReviewedBy: 'reviewer', provenanceReviewedAt: '2026-08-24T10:00:00Z', media: [],
}

const pendingDetail: DocumentPageDetails = {
    ...detail,
    summary: {
        ...detail.summary,
        reviewState: 'REVIEW_REQUIRED',
        transcriptionApprovalState: 'PENDING',
        indexingState: 'NOT_ELIGIBLE',
        hasCorrectedText: false,
    },
    correctedText: null,
    reviewer: null,
    reviewedAt: null,
}

const approvedReview = {
    id: 9, documentPageId: 42, reviewAction: 'APPROVED', reviewer: 'admin', correctedTextSnapshot: 'Raw OCR', previousReviewState: 'REVIEW_REQUIRED', newReviewState: 'APPROVED', previousApprovalState: 'PENDING', newApprovalState: 'APPROVED', reason: null, createdAt: '2026-08-24T11:00:00Z',
} as const

const figure: DocumentPageFigure = {
    id: 21, documentPageId: 42, documentPageMediaId: 31, mediaAssetId: 41, sourceReferenceId: 3, figureCandidateId: 51, figureOrdinal: 1, printedFigureNumber: 'Fig. 1', normalizedX: .1, normalizedY: .2, normalizedWidth: .4, normalizedHeight: .3,
    rawCaptionText: 'Detected figure caption', correctedCaptionText: null, reviewState: 'PENDING', reviewedBy: null, reviewedAt: null, reviewReason: null, detectionConfidence: .88, processingJobId: 61, producingAttempt: 1, createdAt: '2026-08-29T10:00:00Z', updatedAt: '2026-08-29T10:00:00Z', version: 'figure-v1',
}

function renderDialog(onClose = vi.fn()) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return renderApp(<AdminAuthContext.Provider value={{ authenticated: true, initializing: false, sessionExpired: false, admin: { id: 1, username: 'admin', firstName: 'Admin', lastName: 'User', email: null, roles: ['ADMINISTRATOR'], passwordChangeRequired: false }, login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() }}><QueryClientProvider client={client}><DocumentPageReviewDialog open documentId={7} pageId={42} onClose={onClose} /></QueryClientProvider></AdminAuthContext.Provider>)
}

beforeEach(() => {
    vi.mocked(documentApi.getDocument).mockResolvedValue({ summary: { defaultSourceReferenceId: 3 } } as never)
    vi.mocked(documentApi.getDocumentPage).mockResolvedValue(detail)
    vi.mocked(documentApi.getCurrentPageQuality).mockResolvedValue([])
    vi.mocked(documentApi.getDocumentPageWorkflow).mockResolvedValue({ documentId: 7, pageId: 42, completedSteps: 4, totalSteps: 4, steps: [] })
    vi.mocked(documentApi.savePageTranscription).mockResolvedValue({} as never)
    vi.mocked(documentApi.approvePageTranscription).mockResolvedValue(approvedReview)
    vi.mocked(documentApi.rejectPageTranscription).mockResolvedValue({} as never)
    vi.mocked(documentApi.updateDocumentPageMetadata).mockResolvedValue(undefined)
    vi.mocked(documentApi.changePageSourceProvenance).mockResolvedValue({} as never)
    vi.mocked(documentApi.changePageProvenanceTrust).mockResolvedValue({} as never)
    vi.mocked(documentApi.listPageFigures).mockResolvedValue([figure])
    vi.mocked(documentApi.getPageFigureContent).mockResolvedValue(new Blob(['image'], { type: 'image/jpeg' }))
    vi.mocked(sourceReferencesApi.findAll).mockResolvedValue({ content: [{ id: 3, sourceId: 2, chapter: null, pageFrom: 1, pageTo: 1, figureNumber: null, sectionTitle: null, catalogNumber: null, referenceUrl: null, accessedDate: null, locator: null, note: null, createdAt: '', updatedAt: '' }], totalElements: 1, totalPages: 1, size: 1000, number: 0, first: true, last: true, empty: false, numberOfElements: 1 })
    vi.mocked(sourcesApi.findAll).mockResolvedValue({ content: [{ id: 2, title: 'Existing source' } as never], totalElements: 1, totalPages: 1, size: 1000, number: 0, first: true, last: true, empty: false, numberOfElements: 1 })
    vi.stubGlobal('URL', { ...URL, createObjectURL: vi.fn(() => 'blob:figure'), revokeObjectURL: vi.fn() })
})

describe('DocumentPageReviewDialog', () => {
    it('keeps image and transcription side by side and warns when approved text changes', async () => {
        const user = userEvent.setup()
        renderDialog()
        const editor = await screen.findByRole('textbox', { name: 'Recognized and corrected text' })
        expect(screen.getByRole('img', { name: 'Page preview' })).toBeInTheDocument()
        await user.type(editor, ' changed')
        expect(screen.getByText(/Changing approved text revokes approval/)).toBeInTheDocument()
    })

    it('opens the large image preview only from the dedicated preview button', async () => {
        const user = userEvent.setup()
        renderDialog()
        const image = await screen.findByRole('img', { name: 'Page preview' })

        await user.click(image)
        expect(screen.getAllByRole('dialog')).toHaveLength(1)

        await user.click(screen.getByRole('button', { name: 'Open Page preview' }))
        expect(screen.getByRole('dialog')).toHaveTextContent('Page preview')
        expect(screen.getByRole('link', { name: 'Open original' })).toBeVisible()
    })

    it('protects unsaved corrections when closing', async () => {
        const user = userEvent.setup()
        const onClose = vi.fn()
        renderDialog(onClose)
        await user.type(await screen.findByRole('textbox', { name: 'Recognized and corrected text' }), ' changed')
        await user.click(screen.getByRole('button', { name: 'Close' }))
        expect(screen.getByText('Unsaved changes')).toBeInTheDocument()
        expect(onClose).not.toHaveBeenCalled()
        await user.click(screen.getByRole('button', { name: 'Continue without saving' }))
        expect(onClose).toHaveBeenCalledOnce()
    })

    it('requires a rejection reason', async () => {
        const user = userEvent.setup()
        renderDialog()
        await user.click(await screen.findByRole('button', { name: 'More actions' }))
        await user.click(await screen.findByRole('menuitem', { name: 'Reject' }))
        const confirm = screen.getAllByRole('button', { name: 'Reject' }).at(-1)!
        expect(confirm).toBeDisabled()
        await user.type(screen.getByRole('textbox', { name: 'Reason for rejection' }), 'Text does not match the page')
        await user.click(confirm)
        await waitFor(() => expect(documentApi.rejectPageTranscription).toHaveBeenCalledWith(42, 'Text does not match the page'))
    })

    it('keeps the corrected text visible while support panels change', async () => {
        const user = userEvent.setup()
        renderDialog()
        const editor = await screen.findByRole('textbox', { name: 'Recognized and corrected text' })

        await user.click(screen.getByRole('tab', { name: 'OCR history' }))

        expect(editor).toBeVisible()
    })

    it('keeps visual suggestions with the editor and separates the original OCR comparison', async () => {
        const user = userEvent.setup()
        renderDialog()

        expect(await screen.findByRole('textbox', { name: 'Recognized and corrected text' })).toBeVisible()
        expect(screen.getByRole('heading', { name: 'On-demand vision comparison' })).toBeVisible()
        await user.click(screen.getByRole('tab', { name: 'Changes from original' }))

        expect(screen.queryByRole('textbox', { name: 'Recognized and corrected text' })).not.toBeInTheDocument()
        const comparison = screen.getByRole('region', { name: 'Current corrected text' })
        expect(comparison).toHaveTextContent('Raw')
        expect(comparison).toHaveTextContent('Approved')
        expect(comparison).toHaveTextContent('corrected text')
        expect(screen.queryByRole('tab', { name: 'Vision comparison' })).not.toBeInTheDocument()
    })

    it('reviews extracted figures beside the original page image', async () => {
        const user = userEvent.setup()
        renderDialog()

        expect(await screen.findByRole('img', { name: 'Page preview' })).toBeInTheDocument()
        await user.click(screen.getByRole('tab', { name: 'Extracted figures' }))

        expect(screen.getByRole('img', { name: 'Page preview' })).toBeInTheDocument()
        expect(await screen.findByRole('img', { name: 'Detected figure caption' })).toBeInTheDocument()
        expect(screen.getByRole('textbox', { name: 'Detected caption' })).toHaveValue('Detected figure caption')
        expect(screen.getByRole('textbox', { name: 'Corrected caption' })).toBeEnabled()
    })

    it('preserves unsaved text while moving between edit and comparison modes', async () => {
        const user = userEvent.setup()
        renderDialog()
        const editor = await screen.findByRole('textbox', { name: 'Recognized and corrected text' })
        await user.clear(editor)
        await user.type(editor, 'Draft correction')

        await user.click(screen.getByRole('tab', { name: 'Changes from original' }))
        const comparison = screen.getByRole('region', { name: 'Current corrected text' })
        expect(comparison).toHaveTextContent('Draft')
        expect(comparison).toHaveTextContent('correction')
        await user.click(screen.getByRole('tab', { name: 'Edit and suggestions' }))

        expect(screen.getByRole('textbox', { name: 'Recognized and corrected text' })).toHaveValue('Draft correction')
    })

    it('preserves the draft when saving fails', async () => {
        vi.mocked(documentApi.savePageTranscription).mockRejectedValue(new Error('network'))
        const user = userEvent.setup()
        renderDialog()
        const editor = await screen.findByRole('textbox', { name: 'Recognized and corrected text' })
        await user.clear(editor)
        await user.type(editor, 'My unsaved correction')
        await user.click(screen.getByRole('button', { name: 'Save text' }))
        await waitFor(() => expect(documentApi.savePageTranscription).toHaveBeenCalledWith(42, 'My unsaved correction'))
        expect(editor).toHaveValue('My unsaved correction')
    })

    it('saves an edited transcription with the platform save shortcut', async () => {
        renderDialog()
        const editor = await screen.findByRole('textbox', { name: 'Recognized and corrected text' })
        fireEvent.change(editor, { target: { value: 'Shortcut correction' } })
        fireEvent.keyDown(editor, { key: 's', metaKey: true })

        await waitFor(() => expect(documentApi.savePageTranscription).toHaveBeenCalledWith(42, 'Shortcut correction'))
    })

    it('keeps secondary review details collapsed and moves page metadata to its own workspace tab', async () => {
        const user = userEvent.setup()
        renderDialog()
        const editor = await screen.findByRole('textbox', { name: 'Recognized and corrected text' })

        expect(screen.queryByText('Page identity')).not.toBeInTheDocument()
        await user.click(screen.getByRole('button', { name: 'Show review details' }))
        expect(screen.queryByText('Page identity')).not.toBeInTheDocument()
        await user.click(screen.getByRole('button', { name: 'Hide review details' }))

        expect(editor).toBeVisible()
        expect(screen.getByRole('button', { name: 'Show review details' })).toBeInTheDocument()

        await user.click(screen.getByRole('tab', { name: 'Page details' }))
        expect(screen.getByText('Page identity')).toBeVisible()
        expect(screen.getByRole('textbox', { name: /Printed page number/ })).toHaveValue('1')
        expect(screen.getByRole('combobox', { name: 'Provenance trust' })).toBeVisible()
    })

    it('shows an approved state instead of an active approval action', async () => {
        renderDialog()
        expect(await screen.findByRole('button', { name: 'Approved' })).toBeDisabled()
        expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
    })

    it('confirms and approves unchanged OCR text without saving an edit', async () => {
        vi.mocked(documentApi.getDocumentPage).mockResolvedValue(pendingDetail)
        const user = userEvent.setup()
        renderDialog()

        await user.click(await screen.findByRole('button', { name: 'Approve' }))
        expect(screen.getByText('Approve without corrections')).toBeInTheDocument()
        await user.click(screen.getByRole('button', { name: 'Approve as-is' }))

        await waitFor(() => expect(documentApi.approvePageTranscription).toHaveBeenCalledWith(42, null))
        expect(documentApi.savePageTranscription).not.toHaveBeenCalled()
        expect(await screen.findByRole('button', { name: 'Approved' })).toBeDisabled()
        expect(screen.getByText(/remains outside the index/)).toBeInTheDocument()
    })

    it('updates the printed page number without changing other page metadata', async () => {
        const user = userEvent.setup()
        renderDialog()
        await user.click(await screen.findByRole('tab', { name: 'Page details' }))
        const input = await screen.findByRole('textbox', { name: 'Printed page number' })
        await user.clear(input)
        await user.type(input, '15')
        await user.click(screen.getByRole('button', { name: 'Save page identity' }))
        await waitFor(() => expect(documentApi.updateDocumentPageMetadata).toHaveBeenCalledWith(7, 42, 'v1', { printedPageNumber: '15', printedPageSort: 1, pageLabel: null }))
    })

    it('prefills the reason when changing provenance trust', async () => {
        const user = userEvent.setup()
        renderDialog()
        await user.click(await screen.findByRole('tab', { name: 'Page details' }))
        await user.click(await screen.findByRole('combobox', { name: 'Provenance trust' }))
        await user.click(screen.getByRole('option', { name: 'Trusted provenance' }))
        expect(screen.getByRole('textbox', { name: 'Reason for trust decision' })).toHaveValue('Reviewer approval')
        await user.click(screen.getByRole('button', { name: 'Save trust decision' }))
        await waitFor(() => expect(documentApi.changePageProvenanceTrust).toHaveBeenCalledWith(42, { provenanceTrustState: 'TRUSTED', reason: 'Reviewer approval' }))
    })

    it('persists the document citation on an unassigned page', async () => {
        vi.mocked(documentApi.getDocument).mockResolvedValue({ summary: { defaultSourceReferenceId: 9 } } as never)
        vi.mocked(documentApi.getDocumentPage).mockResolvedValue({
            ...detail,
            summary: { ...detail.summary, sourceReferenceId: null, provenanceStatus: 'UNKNOWN_SOURCE', indexingState: 'NOT_ELIGIBLE' },
        })
        vi.mocked(sourceReferencesApi.findAll).mockResolvedValue({
            content: [{ id: 9, sourceId: 4, chapter: 'Chapter 1', pageFrom: 12, pageTo: 12, figureNumber: null, sectionTitle: null, catalogNumber: null, referenceUrl: null, accessedDate: null, locator: null, note: null, createdAt: '', updatedAt: '' }],
            totalElements: 1, totalPages: 1, size: 1000, number: 0, first: true, last: true, empty: false, numberOfElements: 1,
        })
        vi.mocked(sourcesApi.findAll).mockResolvedValue({
            content: [{ id: 4, title: 'Source book' } as never],
            totalElements: 1, totalPages: 1, size: 1000, number: 0, first: true, last: true, empty: false, numberOfElements: 1,
        })
        const user = userEvent.setup()
        renderDialog()

        await user.click(await screen.findByRole('tab', { name: 'Page details' }))
        expect(await screen.findByRole('combobox', { name: 'Exact citation for this page' })).toHaveTextContent('Source book')
        const reason = screen.getByRole('textbox', { name: 'Reason for source selection' })
        await user.clear(reason)
        await user.type(reason, 'Inherited from document source')
        await user.click(screen.getByRole('button', { name: 'Save source' }))

        await waitFor(() => expect(documentApi.changePageSourceProvenance).toHaveBeenCalledWith(42, {
            sourceReferenceId: 9,
            provenanceStatus: 'KNOWN_SOURCE',
            provenanceTrustState: 'VERIFIED',
            note: null,
            reason: 'Inherited from document source',
        }))
    })

    it('persists the inherited document citation before approving', async () => {
        vi.mocked(documentApi.getDocument).mockResolvedValue({ summary: { defaultSourceReferenceId: 9 } } as never)
        vi.mocked(documentApi.getDocumentPage).mockResolvedValue({
            ...pendingDetail,
            summary: { ...pendingDetail.summary, sourceReferenceId: null, provenanceStatus: 'UNKNOWN_SOURCE' },
        })
        const user = userEvent.setup()
        renderDialog()

        await user.click(await screen.findByRole('button', { name: 'Approve' }))
        await user.click(screen.getByRole('button', { name: 'Approve as-is' }))

        await waitFor(() => expect(documentApi.changePageSourceProvenance).toHaveBeenCalledWith(42, {
            sourceReferenceId: 9,
            provenanceStatus: 'KNOWN_SOURCE',
            provenanceTrustState: 'VERIFIED',
            note: null,
            reason: 'Inherited from the document source',
        }))
        expect(vi.mocked(documentApi.changePageSourceProvenance).mock.invocationCallOrder[0]).toBeLessThan(vi.mocked(documentApi.approvePageTranscription).mock.invocationCallOrder[0])
    })
})

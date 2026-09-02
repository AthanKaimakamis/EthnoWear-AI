import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as documentApi from '../../../api/DocumentAdminApi'
import { sourceReferencesApi, sourcesApi } from '../../../api/ArchiveAdminApi'
import { AdminAuthContext } from '../../../app/adminAuth'
import { renderApp } from '../../../test/render'
import type { DocumentPageFigure, DocumentPageSummary } from '../../../types/document'
import DocumentFiguresPanel from './DocumentFiguresPanel'

vi.mock('../../../api/DocumentAdminApi', async importOriginal => ({
    ...await importOriginal<typeof import('../../../api/DocumentAdminApi')>(),
    listDocumentFigures: vi.fn(), getPageFigureContent: vi.fn(), updatePageFigure: vi.fn(),
    approvePageFigure: vi.fn(), rejectPageFigure: vi.fn(), reextractPageFigures: vi.fn(),
}))
vi.mock('../../../api/ArchiveAdminApi', async importOriginal => ({
    ...await importOriginal<typeof import('../../../api/ArchiveAdminApi')>(),
    sourceReferencesApi: { findAll: vi.fn() }, sourcesApi: { findAll: vi.fn() },
}))

const page: DocumentPageSummary = {
    id: 11, documentId: 7, sourceReferenceId: 9, pageKind: 'DOCUMENT_PAGE', pageRole: 'NORMAL', pageSequence: 4, pdfPageIndex: 3, printedPageNumber: '12', printedPageSort: 12, pageLabel: null,
    provenanceStatus: 'KNOWN_SOURCE', processingState: 'COMPLETED', reviewState: 'APPROVED', transcriptionApprovalState: 'APPROVED', provenanceTrustState: 'VERIFIED', indexingState: 'INDEXED', evidenceState: 'ACTIVE', ocrConfidence: .9, hasRawOcrText: true, hasCorrectedText: true, previewMediaAssetId: 5, quality: null,
    createdAt: '2026-08-29T10:00:00Z', updatedAt: '2026-08-29T10:00:00Z', retirement: { retired: false, retiredAt: null, retiredBy: null, reason: null }, versionToken: 'page-v1',
}
const figure: DocumentPageFigure = {
    id: 21, documentPageId: 11, documentPageMediaId: 31, mediaAssetId: 41, sourceReferenceId: null, figureCandidateId: 51, figureOrdinal: 1, printedFigureNumber: 'Фиг. 1', normalizedX: .1, normalizedY: .2, normalizedWidth: .4, normalizedHeight: .3,
    rawCaptionText: 'Detected caption', correctedCaptionText: null, reviewState: 'PENDING', reviewedBy: null, reviewedAt: null, reviewReason: null, detectionConfidence: .88, processingJobId: 61, producingAttempt: 1, createdAt: '2026-08-29T10:00:00Z', updatedAt: '2026-08-29T10:00:00Z', version: 'figure-v1',
}

function renderPanel() {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return renderApp(<AdminAuthContext.Provider value={{ authenticated: true, initializing: false, sessionExpired: false, admin: { id: 1, username: 'admin', firstName: 'Admin', lastName: 'User', email: null, roles: ['ADMINISTRATOR'], passwordChangeRequired: false }, login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() }}><QueryClientProvider client={client}><DocumentFiguresPanel documentId={7} /></QueryClientProvider></AdminAuthContext.Provider>)
}

beforeEach(() => {
    vi.mocked(documentApi.listDocumentFigures).mockResolvedValue([{ page, figures: [figure] }])
    vi.mocked(documentApi.getPageFigureContent).mockResolvedValue(new Blob(['image'], { type: 'image/jpeg' }))
    vi.mocked(documentApi.updatePageFigure).mockResolvedValue({ ...figure, sourceReferenceId: 9, version: 'figure-v2' })
    vi.mocked(sourceReferencesApi.findAll).mockResolvedValue({ content: [{ id: 9, sourceId: 3, chapter: null, pageFrom: 12, pageTo: null, figureNumber: '1', sectionTitle: null, catalogNumber: null, referenceUrl: null, accessedDate: null, locator: null, note: null, createdAt: '', updatedAt: '' }], totalElements: 1, totalPages: 1, size: 1000, number: 0, first: true, last: true, empty: false, numberOfElements: 1 })
    vi.mocked(sourcesApi.findAll).mockResolvedValue({ content: [{ id: 3, title: 'Source book', author: null, publisher: null, year: null, sourceType: 'BOOK', language: 'bg', filePath: null, url: null, isbn: null, notes: null, trusted: true, createdAt: '', updatedAt: '' }], totalElements: 1, totalPages: 1, size: 1000, number: 0, first: true, last: true, empty: false, numberOfElements: 1 })
    vi.stubGlobal('URL', { ...URL, createObjectURL: vi.fn(() => 'blob:figure'), revokeObjectURL: vi.fn() })
})

describe('DocumentFiguresPanel', () => {
    it('groups figures by page and explains approved-only publication', async () => {
        renderPanel()
        expect(await screen.findByText('Page 12')).toBeInTheDocument()
        expect(screen.getByText(/Only approved figures appear in the Media library/)).toBeInTheDocument()
        expect(screen.getByText('Pending review')).toBeInTheDocument()
    })

    it('requires saved caption and source metadata before approval', async () => {
        const user = userEvent.setup()
        renderPanel()
        await user.click(await screen.findByRole('button', { name: /Open figure 1/ }))
        expect(screen.getByRole('button', { name: 'Approve' })).toBeDisabled()
        await user.click(screen.getByRole('combobox', { name: 'Source reference' }))
        await user.click(await screen.findByRole('option', { name: /Source book/ }))
        await user.click(screen.getByRole('button', { name: 'Save' }))
        expect(documentApi.updatePageFigure).toHaveBeenCalledWith(11, 21, 'figure-v1', expect.objectContaining({ sourceReferenceId: 9 }))
    })

    it('submits the reviewer identity as a hidden approval reason', async () => {
        const ready = { ...figure, correctedCaptionText: 'Reviewed caption', sourceReferenceId: 9 }
        vi.mocked(documentApi.listDocumentFigures).mockResolvedValue([{ page, figures: [ready] }])
        vi.mocked(documentApi.approvePageFigure).mockResolvedValue({ ...ready, reviewState: 'APPROVED', version: 'figure-v2' })
        const user = userEvent.setup()
        renderPanel()

        await user.click(await screen.findByRole('button', { name: /Open figure 1/ }))
        await user.click(screen.getByRole('button', { name: 'Approve' }))
        expect(screen.queryByRole('textbox', { name: 'Decision reason' })).not.toBeInTheDocument()
        await user.click(screen.getByRole('button', { name: 'Approve' }))

        await vi.waitFor(() => expect(documentApi.approvePageFigure).toHaveBeenCalledWith(11, 21, 'figure-v1', 'Reviewed by admin - Administrator'))
    })

    it('keeps approve disabled after the figure is approved', async () => {
        const approved = { ...figure, correctedCaptionText: 'Reviewed caption', sourceReferenceId: 9, reviewState: 'APPROVED' as const }
        vi.mocked(documentApi.listDocumentFigures).mockResolvedValue([{ page, figures: [approved] }])
        const user = userEvent.setup()
        renderPanel()

        await user.click(await screen.findByRole('button', { name: /Open figure 1/ }))
        expect(screen.getByRole('button', { name: 'Approve' })).toBeDisabled()
    })

    it('prefills the page citation and requires saving it on the figure', async () => {
        const user = userEvent.setup()
        renderPanel()
        await user.click(await screen.findByRole('button', { name: /Open figure 1/ }))

        expect(screen.getByRole('combobox', { name: 'Source reference' })).toHaveTextContent('Source book')
        expect(screen.getByText(/page or document reference is suggested/i)).toBeInTheDocument()
        await user.click(screen.getByRole('button', { name: 'Save' }))
        expect(documentApi.updatePageFigure).toHaveBeenCalledWith(11, 21, 'figure-v1', expect.objectContaining({ sourceReferenceId: 9 }))
    })

    it('keeps outdated results visible but blocks review', async () => {
        const outdated = { ...figure, reviewState: 'OUTDATED' as const, sourceReferenceId: 9 }
        vi.mocked(documentApi.listDocumentFigures).mockResolvedValue([{ page, figures: [outdated] }])
        const user = userEvent.setup()
        renderPanel()
        expect(await screen.findByText('Outdated')).toBeInTheDocument()
        await user.click(screen.getByRole('button', { name: /Open figure 1/ }))
        expect(screen.getByText(/retained for history only/)).toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Approve' })).toBeDisabled()
    })
})

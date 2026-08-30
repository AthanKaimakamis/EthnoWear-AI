import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminAuthContext } from '../../../app/adminAuth'
import { renderApp } from '../../../test/render'
import * as documentApi from '../../../api/DocumentAdminApi'
import DocumentChunkManagement from './DocumentChunkManagement'

vi.mock('../../../api/DocumentAdminApi', async importOriginal => ({
    ...await importOriginal<typeof import('../../../api/DocumentAdminApi')>(),
    getChunkGenerationEligibility: vi.fn(),
    listChunkGenerationJobs: vi.fn(),
    listGeneratedChunks: vi.fn(),
    queueDocumentChunkGeneration: vi.fn(),
    retryProcessingJob: vi.fn(),
}))

const emptyPage = { content: [], number: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true, numberOfElements: 0, empty: true }

function renderChunkManagement(onOpenPage = vi.fn()) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return renderApp(
        <AdminAuthContext.Provider value={{ authenticated: true, initializing: false, sessionExpired: false, admin: { id: 1, username: 'admin', firstName: 'Admin', lastName: 'User', email: null, roles: ['ADMINISTRATOR'], passwordChangeRequired: false }, login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() }}>
            <QueryClientProvider client={client}><DocumentChunkManagement documentId={7} onOpenPage={onOpenPage} /></QueryClientProvider>
        </AdminAuthContext.Provider>,
    )
}

beforeEach(() => {
    vi.mocked(documentApi.listChunkGenerationJobs).mockResolvedValue(emptyPage)
    vi.mocked(documentApi.listGeneratedChunks).mockResolvedValue(emptyPage)
    vi.mocked(documentApi.queueDocumentChunkGeneration).mockResolvedValue({ id: 99 } as never)
})

describe('DocumentChunkManagement', () => {
    it('renders localized blockers and disables generation when ineligible', async () => {
        vi.mocked(documentApi.getChunkGenerationEligibility).mockResolvedValue({ documentId: 7, eligible: false, eligiblePageCount: 0, blockers: [{ documentPageId: 42, code: 'TRANSCRIPTION_NOT_APPROVED', message: 'backend text' }] })
        const onOpenPage = vi.fn()
        renderChunkManagement(onOpenPage)

        expect(await screen.findByText('The corrected text is not approved.')).toBeInTheDocument()
        expect(screen.queryByText('backend text')).not.toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Generate chunks' })).toBeDisabled()
        await userEvent.click(screen.getByRole('button', { name: 'Open page' }))
        expect(onOpenPage).toHaveBeenCalledWith(42)
    })

    it('confirms and schedules one generation job', async () => {
        vi.mocked(documentApi.getChunkGenerationEligibility).mockResolvedValue({ documentId: 7, eligible: true, eligiblePageCount: 3, blockers: [] })
        renderChunkManagement()
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: 'Generate chunks' }))
        await user.click(screen.getByRole('button', { name: 'Generate' }))

        await waitFor(() => expect(documentApi.queueDocumentChunkGeneration).toHaveBeenCalledOnce())
        expect(documentApi.queueDocumentChunkGeneration).toHaveBeenCalledWith(7)
    })

    it('shows full chunk text, superseded state, and ordered citations', async () => {
        vi.mocked(documentApi.getChunkGenerationEligibility).mockResolvedValue({ documentId: 7, eligible: false, eligiblePageCount: 0, blockers: [] })
        vi.mocked(documentApi.listGeneratedChunks).mockResolvedValue({ ...emptyPage, totalElements: 1, numberOfElements: 1, empty: false, content: [{
            id: 5, documentId: 7, sourceReferenceId: 2, chunkType: 'SOURCE_EXCERPT', language: 'bg', content: 'Complete corrected text', sourceTextType: 'CORRECTED_PAGE_TEXT', chunkOrdinal: 2, chunkingStrategy: 'PAGE_AWARE', chunkingVersion: '1', reviewState: 'APPROVED', transcriptionApprovalState: 'APPROVED', provenanceTrustState: 'VERIFIED', indexingState: 'OUTDATED', supersededByKnowledgeChunkId: 6, current: false, createdAt: '2026-08-24T10:00:00Z', citations: [
                { documentPageId: 12, pageOrder: 2, startCharOffset: 80, endCharOffset: 120, startsOnPage: false, endsOnPage: true, printedPageNumber: '12', pdfPageIndex: 11, label: 'p. 12' },
                { documentPageId: 11, pageOrder: 1, startCharOffset: 0, endCharOffset: 79, startsOnPage: true, endsOnPage: false, printedPageNumber: '11', pdfPageIndex: 10, label: 'p. 11' },
            ],
        }] })
        renderChunkManagement()

        expect(await screen.findByText('Complete corrected text')).toBeInTheDocument()
        expect(screen.getByText('Superseded')).toBeInTheDocument()
        const rows = screen.getAllByRole('row')
        expect(rows.at(-2)).toHaveTextContent('p. 11')
        expect(rows.at(-1)).toHaveTextContent('p. 12')
    })
})

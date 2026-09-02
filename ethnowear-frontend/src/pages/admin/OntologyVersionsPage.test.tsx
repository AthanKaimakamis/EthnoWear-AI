import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderApp } from '../../test/render'
import type { OntologyVersion } from '../../types/ontologyAdmin'
import OntologyVersionsPage from './OntologyVersionsPage'

const api = vi.hoisted(() => ({ list: vi.fn(), get: vi.fn(), latest: vi.fn(), content: vi.fn(), restore: vi.fn() }))
vi.mock('../../api/OntologyAdminApi', async importOriginal => {
    const original = await importOriginal<typeof import('../../api/OntologyAdminApi')>()
    return { ...original, listOntologyVersions: api.list, getOntologyVersion: api.get, getLatestOntologyVersion: api.latest,
        getOntologyVersionContent: api.content, restoreOntologyVersion: api.restore }
})

const active: OntologyVersion = {
    id: 3, versionNumber: 3, createdAt: '2026-09-02T10:00:00', createdByUserId: 1, createdByUsername: 'admin',
    changeReason: 'Current version', contentHash: 'abc123', fileName: 'EthnoWear.owx', ontologyNamespace: 'https://example.test#',
    valid: true, validationMessage: null, previousVersionId: 2, previousVersionNumber: 2,
    restoredFromVersionId: null, restoredFromVersionNumber: null, status: 'ACTIVE',
}
const old = { ...active, id: 2, versionNumber: 2, status: 'SUPERSEDED' as const, changeReason: 'Older version', previousVersionId: 1, previousVersionNumber: 1 }

function renderPage() {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    return renderApp(<QueryClientProvider client={client}><OntologyVersionsPage /></QueryClientProvider>)
}

describe('OntologyVersionsPage', () => {
    beforeEach(() => {
        api.list.mockResolvedValue({ content: [active, old], number: 0, size: 20, totalElements: 2, totalPages: 1, first: true, last: true, numberOfElements: 2, empty: false })
        api.get.mockResolvedValue(old)
        api.latest.mockResolvedValue(active)
        api.content.mockResolvedValue(new Blob(['<owl:Ontology rdf:about="https://example.test" />'], { type: 'application/rdf+xml' }))
        api.restore.mockResolvedValue({ ...active, id: 4, versionNumber: 4, restoredFromVersionId: 2, restoredFromVersionNumber: 2 })
    })

    it('shows the active version and disables restore for it', async () => {
        renderPage()
        expect(await screen.findAllByText('Current version')).toHaveLength(2)
        expect(screen.getByRole('button', { name: 'Download active OWL version' })).toBeVisible()
        const restoreButtons = screen.getAllByRole('button', { name: 'Restore' })
        expect(restoreButtons).toHaveLength(2)
        expect(restoreButtons[0]).toBeDisabled()
        expect(restoreButtons[1]).toBeEnabled()
    })

    it('loads OWL content only when preview is requested', async () => {
        const user = userEvent.setup()
        renderPage()
        await screen.findByText('Older version')
        expect(api.content).not.toHaveBeenCalled()
        await user.click(screen.getAllByRole('button', { name: 'Preview OWL' })[2])
        expect(await screen.findByText(/owl:Ontology/)).toBeVisible()
        expect(document.querySelector('[data-workspace-modal="true"]')).toBeInTheDocument()
        expect(api.content).toHaveBeenCalledWith(2, expect.any(AbortSignal))
    })

    it('requires an explicit reason before restoring', async () => {
        const user = userEvent.setup()
        renderPage()
        await screen.findByText('Older version')
        const restoreButtons = screen.getAllByRole('button', { name: 'Restore' })
        await user.click(restoreButtons[1])
        const confirm = screen.getByRole('button', { name: 'Restore version' })
        expect(confirm).toBeDisabled()
        await user.type(screen.getByRole('textbox', { name: /Restoration reason/ }), 'Reviewed rollback')
        expect(confirm).toBeEnabled()
        await user.click(confirm)
        await waitFor(() => expect(api.restore).toHaveBeenCalledWith(2, 'Reviewed rollback'))
    })
})

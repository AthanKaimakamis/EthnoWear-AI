import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mediaEntityLinksApi } from '../../../api/ArchiveAdminApi'
import { getFullReference } from '../../../api/ReferenceApi'
import { renderApp } from '../../../test/render'
import type { ReferenceData } from '../../../types/reference'
import MediaOntologyLinksEditor from './MediaOntologyLinksEditor'

vi.mock('../../../api/ArchiveAdminApi', async importOriginal => ({
    ...await importOriginal<typeof import('../../../api/ArchiveAdminApi')>(),
    mediaEntityLinksApi: {
        findAll: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(), findById: vi.fn(),
    },
}))
vi.mock('../../../api/ReferenceApi', async importOriginal => ({
    ...await importOriginal<typeof import('../../../api/ReferenceApi')>(),
    getFullReference: vi.fn(),
}))

const reference: ReferenceData = {
    language: 'en',
    regions: [], regionGroups: [], colors: [], techniques: [], techniqueTypes: [], motifs: [], regionalEmbroideryTypes: [], ornamentTypes: [],
    ornaments: [
        { iri: 'https://example.test/Flower', localName: 'Flower', label: 'Flower' },
        { iri: 'https://example.test/Cross', localName: 'Cross', label: 'Cross' },
    ],
    regionsByRegionGroup: {}, regionByRegionalEmbroidery: {}, ornamentsByRegion: {}, techniquesByRegion: {}, ornamentsByType: {}, techniquesByType: {},
}

function renderEditor() {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return renderApp(<QueryClientProvider client={client}><MediaOntologyLinksEditor mediaAssetId={41} canEdit /></QueryClientProvider>)
}

beforeEach(() => {
    vi.mocked(getFullReference).mockResolvedValue(reference)
    vi.mocked(mediaEntityLinksApi.findAll).mockResolvedValue({
        content: [{ id: 7, mediaAssetId: 41, entityType: 'ORNAMENT', ontologyIri: 'https://example.test/Flower', ontologyLocalName: 'Flower', description: null, createdAt: '', updatedAt: '' }],
        totalElements: 1, totalPages: 1, size: 1000, number: 0, first: true, last: true, empty: false, numberOfElements: 1,
    })
    vi.mocked(mediaEntityLinksApi.create).mockResolvedValue({ id: 8, mediaAssetId: 41, entityType: 'ORNAMENT', ontologyIri: 'https://example.test/Cross', ontologyLocalName: 'Cross', description: null, createdAt: '', updatedAt: '' })
    vi.mocked(mediaEntityLinksApi.remove).mockResolvedValue(undefined)
})

describe('MediaOntologyLinksEditor', () => {
    it('shows current links and saves additions and removals for the media asset', async () => {
        const user = userEvent.setup()
        renderEditor()

        expect(await screen.findByText('Flower')).toBeInTheDocument()
        await user.click(screen.getByRole('button', { name: 'Manage links' }))
        await user.click(screen.getByRole('checkbox', { name: 'Flower' }))
        await user.click(screen.getByRole('checkbox', { name: 'Cross' }))
        await user.click(screen.getByRole('button', { name: 'Save' }))

        expect(mediaEntityLinksApi.remove).toHaveBeenCalledWith(7)
        expect(mediaEntityLinksApi.create).toHaveBeenCalledWith({
            mediaAssetId: 41,
            entityType: 'ORNAMENT',
            ontologyIri: 'https://example.test/Cross',
            ontologyLocalName: 'Cross',
            description: null,
        })
    })
})

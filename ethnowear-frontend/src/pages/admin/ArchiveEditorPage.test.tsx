import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/http'
import { renderApp as renderBase } from '../../test/render'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactElement } from 'react'
import ArchiveEditorPage from './ArchiveEditorPage'

const renderApp = (ui: ReactElement) => renderBase(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

const apiMocks = vi.hoisted(() => ({
    createFullArchiveEntry: vi.fn(),
    updateFullArchiveEntry: vi.fn(),
    getAdminArchiveItemDetail: vi.fn(),
    getPublicationReadiness: vi.fn(),
    runPublicationCommand: vi.fn(),
    sourcesFindAll: vi.fn(),
    referencesFindAll: vi.fn(),
    referenceCreate: vi.fn(),
    assetsFindAll: vi.fn(),
    getFullReference: vi.fn(),
}))

vi.mock('../../api/ArchiveAdminApi', async importOriginal => ({
    ...await importOriginal<typeof import('../../api/ArchiveAdminApi')>(),
    createFullArchiveEntry: apiMocks.createFullArchiveEntry,
    updateFullArchiveEntry: apiMocks.updateFullArchiveEntry,
    getAdminArchiveItemDetail: apiMocks.getAdminArchiveItemDetail,
    getPublicationReadiness: apiMocks.getPublicationReadiness,
    runPublicationCommand: apiMocks.runPublicationCommand,
    sourcesApi: { findAll: apiMocks.sourcesFindAll },
    sourceReferencesApi: { findAll: apiMocks.referencesFindAll, create: apiMocks.referenceCreate },
    mediaAssetsApi: { findAll: apiMocks.assetsFindAll },
}))

vi.mock('../../api/ReferenceApi', () => ({
    getFullReference: apiMocks.getFullReference,
}))

beforeEach(() => {
    apiMocks.sourcesFindAll.mockResolvedValue({ content: [] })
    apiMocks.referencesFindAll.mockResolvedValue({ content: [] })
    apiMocks.assetsFindAll.mockResolvedValue({ content: [] })
    apiMocks.getFullReference.mockResolvedValue({
        regions: [], regionGroups: [], regionalMotifTypes: [], regionalEmbroideryTypes: [],
        techniques: [], techniqueTypes: [], ornaments: [], ornamentTypes: [], motifs: [], colors: [],
        regionsByRegionGroup: {}, regionByRegionalEmbroidery: {}, regionByRegionalMotif: {},
        ornamentsByRegion: {}, techniquesByRegion: {}, ornamentsByType: {}, techniquesByType: {},
    })
})

describe('ArchiveEditorPage', () => {
    it('saves a motif without observations and retains its optional embroidery', async () => {
        const region = { iri: 'urn:ElhovoRegion', localName: 'ElhovoRegion', label: 'Elhovo' }
        const embroidery = { iri: 'urn:ElhovoEmbroidery', localName: 'ElhovoEmbroidery', label: 'Elhovo embroidery' }
        const motif = { iri: 'urn:ElhovoMotif', localName: 'ElhovoMotif', label: 'Elhovo motif' }
        const item = { id: 10, sourceReferenceId: 7, titleBg: 'Запис', titleEn: 'Sample', archiveType: 'MOTIF_EXAMPLE',
            publicationStatus: 'DRAFT', trustedLevel: 'LIKELY', ontologyRegionIri: region.iri, ontologyRegionLocalName: region.localName,
            ontologyRegionalEmbroideryIri: embroidery.iri, ontologyRegionalEmbroideryLocalName: embroidery.localName,
            ontologyRegionalMotifIri: motif.iri, ontologyRegionalMotifLocalName: motif.localName }
        apiMocks.getAdminArchiveItemDetail.mockResolvedValue({ archiveItem: item, features: [], media: [] })
        apiMocks.getPublicationReadiness.mockResolvedValue({ archiveItemId: 10, publicationStatus: 'DRAFT', ready: true, checks: [] })
        apiMocks.getFullReference.mockResolvedValue({
            regions: [region], regionGroups: [], regionalMotifTypes: [motif], regionalEmbroideryTypes: [embroidery],
            regionByRegionalEmbroidery: { ElhovoEmbroidery: 'ElhovoRegion' }, regionByRegionalMotif: { ElhovoMotif: 'ElhovoRegion' },
            techniques: [], techniqueTypes: [], ornaments: [], ornamentTypes: [], motifs: [], colors: [],
            regionsByRegionGroup: {}, ornamentsByRegion: {}, techniquesByRegion: {}, ornamentsByType: {}, techniquesByType: {},
        })
        apiMocks.updateFullArchiveEntry.mockImplementation(async (_id, payload) => ({ archiveItem: { ...payload.archiveItem, id: 10 }, features: [], media: [] }))
        const user = userEvent.setup()
        renderApp(<ArchiveEditorPage itemId={10} />)
        expect(await screen.findByText('Regional embroidery (optional)')).toBeVisible()
        expect(screen.queryByText('Motifs represented')).not.toBeInTheDocument()
        await user.click(screen.getByRole('button', { name: 'Save draft' }))
        await waitFor(() => expect(apiMocks.updateFullArchiveEntry).toHaveBeenCalledWith(10, expect.objectContaining({
            archiveItem: expect.objectContaining({ archiveType: 'MOTIF_EXAMPLE', ontologyRegionalMotifIri: motif.iri,
                ontologyRegionalEmbroideryIri: embroidery.iri, sourceReferenceId: 7 }),
            features: [], media: [],
        })))
    })
    it('preserves unsaved draft fields when aggregate save fails', async () => {
        apiMocks.sourcesFindAll.mockResolvedValue({ content: [{ id: 3, title: 'Test source' }] })
        apiMocks.referencesFindAll.mockResolvedValue({ content: [{ id: 7, sourceId: 3, pageFrom: 12, pageTo: null, locator: null }] })
        apiMocks.createFullArchiveEntry.mockRejectedValue(new ApiError(
            'Request failed with status 400',
            400,
            { message: 'Validation failed', fields: { sourceReferenceId: 'must be selected' } },
        ))
        const user = userEvent.setup()
        renderApp(<ArchiveEditorPage itemId={null} />)

        const title = await screen.findByRole('textbox', { name: /Bulgarian title/i })
        await user.clear(title)
        await user.type(title, 'Unsaved curator title')
        await user.click(screen.getByRole('combobox', { name: /Record type/i }))
        await user.click(await screen.findByRole('option', { name: /Text reference/i }))
        expect(screen.getAllByRole('tab')).toHaveLength(3)
        await user.click(screen.getByRole('combobox', { name: /Source and exact citation/i }))
        await user.click(await screen.findByRole('option', { name: /Test source/i }))
        await user.click(screen.getByRole('button', { name: 'Save draft' }))

        await waitFor(() => expect(apiMocks.createFullArchiveEntry).toHaveBeenCalledOnce())
        expect(title).toHaveValue('Unsaved curator title')
        expect(screen.getByText('Review the highlighted fields.')).toBeVisible()
        expect(screen.getByText('Exact citation is required.')).toBeVisible()
    })

    it('allows an incomplete draft with a null citation', async () => {
        apiMocks.createFullArchiveEntry.mockRejectedValue(new Error('Test save failure'))
        const user = userEvent.setup()
        renderApp(<ArchiveEditorPage itemId={null} />)

        await user.click(await screen.findByRole('button', { name: 'Save draft' }))

        await waitFor(() => expect(apiMocks.createFullArchiveEntry).toHaveBeenCalledOnce())
        expect(apiMocks.createFullArchiveEntry.mock.calls[0][0].archiveItem.sourceReferenceId).toBeNull()
    })
})

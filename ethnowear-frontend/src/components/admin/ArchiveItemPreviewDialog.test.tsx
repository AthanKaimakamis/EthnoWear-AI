import { screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ArchiveItemPreviewDialog from './ArchiveItemPreviewDialog'
import { renderApp } from '../../test/render'
import type { ArchiveItemDetailDetails, PublicationStatus } from '../../types/archive'

const apiMocks = vi.hoisted(() => ({
    getAdminArchiveItemDetail: vi.fn(),
    getFullReference: vi.fn(),
}))

vi.mock('../../api/ArchiveAdminApi', async importOriginal => ({
    ...await importOriginal<typeof import('../../api/ArchiveAdminApi')>(),
    getAdminArchiveItemDetail: apiMocks.getAdminArchiveItemDetail,
}))

vi.mock('../../api/ReferenceApi', () => ({
    getFullReference: apiMocks.getFullReference,
}))

beforeEach(() => {
    apiMocks.getFullReference.mockResolvedValue({
        regions: [], regionGroups: [], regionalEmbroideryTypes: [],
        techniques: [], ornaments: [], motifs: [], colors: [],
    })
})

describe('ArchiveItemPreviewDialog', () => {
    it('uses protected detail and hides the public link for a draft', async () => {
        apiMocks.getAdminArchiveItemDetail.mockResolvedValue(details('DRAFT'))
        renderApp(<ArchiveItemPreviewDialog itemId={7} onClose={vi.fn()} onEdit={vi.fn()} />)

        await waitFor(() => expect(apiMocks.getAdminArchiveItemDetail).toHaveBeenCalledWith(7, expect.any(AbortSignal)))
        expect(await screen.findByText('Draft')).toBeVisible()
        expect(screen.queryByRole('link', { name: 'Open public page' })).not.toBeInTheDocument()
    })

    it('offers a public link only for a published record', async () => {
        apiMocks.getAdminArchiveItemDetail.mockResolvedValue(details('PUBLISHED'))
        renderApp(<ArchiveItemPreviewDialog itemId={9} onClose={vi.fn()} onEdit={vi.fn()} />)

        const link = await screen.findByRole('link', { name: 'Open public page' })
        expect(link).toHaveAttribute('href', '/archive/items/9')
    })
})

function details(publicationStatus: PublicationStatus): ArchiveItemDetailDetails {
    return {
        archiveItem: {
            id: publicationStatus === 'PUBLISHED' ? 9 : 7,
            sourceReferenceId: 1,
            collectionId: null,
            inventoryNumber: null,
            titleBg: 'Тестова шевица',
            titleEn: 'Test embroidery',
            descriptionBg: null,
            descriptionEn: null,
            archiveType: 'EMBROIDERY_SAMPLE',
            periodText: null,
            originText: null,
            currentLocation: null,
            trustedLevel: 'VERIFIED',
            publicationStatus,
            ontologyRegionIri: null,
            ontologyRegionLocalName: null,
            ontologyRegionalEmbroideryIri: null,
            ontologyRegionalEmbroideryLocalName: null,
            submittedAt: null,
            publishedAt: publicationStatus === 'PUBLISHED' ? '2026-08-15T10:00:00' : null,
            archivedAt: null,
            createdAt: '2026-08-15T09:00:00',
            updatedAt: '2026-08-15T10:00:00',
        },
        source: {
            sourceReferenceId: 1,
            sourceId: 1,
            title: 'Source',
            author: null,
            publisher: null,
            year: null,
            sourceType: 'BOOK',
            sourceLanguage: 'bg',
            filePath: null,
            url: null,
            isbn: null,
            trusted: true,
            chapter: null,
            pageFrom: null,
            pageTo: null,
            figureNumber: null,
            sectionTitle: null,
            catalogNumber: null,
            referenceUrl: null,
            accessedDate: null,
            locator: null,
            note: null,
        },
        features: [],
        media: [],
    }
}

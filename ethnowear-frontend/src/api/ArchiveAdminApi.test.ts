import { afterEach, describe, expect, it, vi } from 'vitest'
import {
    createFullArchiveEntry,
    getAdminArchiveItemDetail,
    getPublicationReadiness,
    runPublicationCommand,
    updateFullArchiveEntry,
} from './ArchiveAdminApi'
import type { ArchiveEntryWriteDto } from '../types/archive'

afterEach(() => {
    vi.unstubAllGlobals()
})

describe('archive publication API', () => {
    it('composes unpublished previews from protected admin CRUD endpoints', async () => {
        const fetchMock = vi.fn((input: RequestInfo | URL) => {
            const url = String(input)
            if (url === '/api/admin/archive-items/42') return Promise.resolve(jsonResponse({ id: 42, sourceReferenceId: 5 }))
            if (url.startsWith('/api/admin/archive-item-features?')) return Promise.resolve(jsonResponse(page([])))
            if (url.startsWith('/api/admin/archive-item-media?')) return Promise.resolve(jsonResponse(page([])))
            if (url === '/api/admin/source-references/5') return Promise.resolve(jsonResponse({ id: 5, sourceId: 9 }))
            if (url === '/api/admin/sources/9') return Promise.resolve(jsonResponse({ id: 9, title: 'Source' }))
            return Promise.reject(new Error(`Unexpected request: ${url}`))
        })
        vi.stubGlobal('fetch', fetchMock)

        await expect(getAdminArchiveItemDetail(42)).resolves.toMatchObject({
            archiveItem: { id: 42 },
            source: { sourceReferenceId: 5, sourceId: 9, title: 'Source' },
            features: [],
            media: [],
        })

        expect(fetchMock.mock.calls.every(([url]) => String(url).startsWith('/api/admin/'))).toBe(true)
        expect(fetchMock.mock.calls.every(([url]) => !String(url).endsWith('/detail'))).toBe(true)
    })

    it('creates the archive entry through the existing resource CRUD endpoints', async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(jsonResponse({ id: 42, publicationStatus: 'DRAFT' }))
            .mockResolvedValueOnce(jsonResponse({ id: 51, archiveItemId: 42, ...entry.features[0] }))
            .mockResolvedValueOnce(jsonResponse({ id: 61, archiveItemId: 42, ...entry.media[0] }))
        vi.stubGlobal('fetch', fetchMock)

        await expect(createFullArchiveEntry(entry)).resolves.toMatchObject({ archiveItem: { id: 42 } })

        expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/admin/archive-items', expect.objectContaining({ method: 'POST' }))
        expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/admin/archive-item-features', expect.objectContaining({ method: 'POST' }))
        expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/admin/archive-item-media', expect.objectContaining({ method: 'POST' }))
        expect(fetchMock.mock.calls.every(([url]) => !String(url).includes('/full'))).toBe(true)
    })

    it('updates and synchronizes an archive entry through resource CRUD endpoints', async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(jsonResponse({ id: 42, publicationStatus: 'DRAFT' }))
            .mockResolvedValueOnce(jsonResponse(page([{ id: 51, archiveItemId: 42, ...entry.features[0] }])))
            .mockResolvedValueOnce(jsonResponse(page([{ id: 61, archiveItemId: 42, ...entry.media[0] }])))
            .mockResolvedValueOnce(jsonResponse({ id: 51, archiveItemId: 42, ...entry.features[0] }))
            .mockResolvedValueOnce(jsonResponse({ id: 61, archiveItemId: 42, ...entry.media[0] }))
        vi.stubGlobal('fetch', fetchMock)

        await updateFullArchiveEntry(42, entry)

        expect(fetchMock).toHaveBeenCalledWith('/api/admin/archive-items/42', expect.objectContaining({ method: 'PUT' }))
        expect(fetchMock).toHaveBeenCalledWith('/api/admin/archive-item-features/51', expect.objectContaining({ method: 'PUT' }))
        expect(fetchMock).toHaveBeenCalledWith('/api/admin/archive-item-media/61', expect.objectContaining({ method: 'PUT' }))
        expect(fetchMock.mock.calls.every(([url]) => !String(url).includes('/full'))).toBe(true)
    })

    it('loads backend publication readiness', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ archiveItemId: 42, publicationStatus: 'DRAFT', ready: true, checks: [] }))
        vi.stubGlobal('fetch', fetchMock)

        await expect(getPublicationReadiness(42)).resolves.toMatchObject({ ready: true })
        expect(fetchMock).toHaveBeenCalledWith('/api/admin/archive-items/42/publication-readiness', expect.any(Object))
    })

    it('returns the server state after a successful transition', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 42, publicationStatus: 'IN_REVIEW' }))
        vi.stubGlobal('fetch', fetchMock)

        await expect(runPublicationCommand(42, 'submit')).resolves.toMatchObject({ publicationStatus: 'IN_REVIEW' })
        expect(fetchMock).toHaveBeenCalledWith('/api/admin/archive-items/42/submit', expect.objectContaining({ method: 'POST' }))
    })

    it('rejects a failed transition without manufacturing a new state', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ message: 'Invalid transition' }, 409)))

        await expect(runPublicationCommand(42, 'publish')).rejects.toEqual(expect.objectContaining({
            status: 409,
            details: { message: 'Invalid transition' },
        }))
    })
})

const entry: ArchiveEntryWriteDto = {
    archiveItem: {
        sourceReferenceId: 5,
        collectionId: null,
        inventoryNumber: null,
        titleBg: 'Заглавие',
        titleEn: null,
        descriptionBg: null,
        descriptionEn: null,
        archiveType: 'TEXT_REFERENCE',
        periodText: null,
        originText: null,
        currentLocation: null,
        trustedLevel: 'UNVERIFIED',
        ontologyRegionIri: null,
        ontologyRegionLocalName: null,
        ontologyRegionalEmbroideryIri: null,
        ontologyRegionalEmbroideryLocalName: null,
    },
    features: [{
        featureType: 'ORNAMENT',
        ontologyIri: 'https://example.test/ornament',
        ontologyLocalName: 'Ornament',
        confidence: null,
        validated: true,
        notes: null,
        sourceReferenceId: 5,
    }],
    media: [{ mediaAssetId: 7, role: 'PRIMARY', captionBg: null, captionEn: null }],
}

function page<T>(content: T[]) {
    return {
        content,
        number: 0,
        size: 200,
        totalElements: content.length,
        totalPages: 1,
        first: true,
        last: true,
        numberOfElements: content.length,
        empty: content.length === 0,
    }
}

function jsonResponse(body: unknown, status = 200) {
    return new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
    })
}

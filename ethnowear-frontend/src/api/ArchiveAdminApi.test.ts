import { afterEach, describe, expect, it, vi } from 'vitest'
import {
    createFullArchiveEntry,
    getAdminArchiveItemDetail,
    getPublicationReadiness,
    runPublicationCommand,
    updateFullArchiveEntry,
    mediaAssetsApi,
    getAdminMediaContent,
} from './ArchiveAdminApi'
import type { ArchiveEntryWriteDto } from '../types/archive'
import { clearAdminSession, setAdminSession } from '../app/adminAuthStore'

afterEach(() => {
    clearAdminSession()
    vi.unstubAllGlobals()
})

describe('archive publication API', () => {
    it('composes unpublished previews from the protected aggregate endpoint', async () => {
        const fetchMock = vi.fn((input: RequestInfo | URL) => {
            const url = String(input)
            if (url === '/api/admin/archive-entries/42') return Promise.resolve(jsonResponse({
                archiveItem: { id: 42, sourceReferenceId: 5 },
                features: [],
                media: [],
            }))
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

    it('creates the complete archive entry transactionally', async () => {
        const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse({
            archiveItem: { id: 42, publicationStatus: 'DRAFT' },
            features: [],
            media: [],
        }))
        vi.stubGlobal('fetch', fetchMock)

        await expect(createFullArchiveEntry(entry)).resolves.toMatchObject({ archiveItem: { id: 42 } })

        expect(fetchMock).toHaveBeenCalledOnce()
        expect(fetchMock).toHaveBeenCalledWith('/api/admin/archive-entries', expect.objectContaining({
            method: 'POST',
            body: JSON.stringify(entry),
        }))
    })

    it('updates the complete archive entry transactionally', async () => {
        const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse({
            archiveItem: { id: 42, publicationStatus: 'DRAFT' },
            features: [],
            media: [],
        }))
        vi.stubGlobal('fetch', fetchMock)

        await updateFullArchiveEntry(42, entry)

        expect(fetchMock).toHaveBeenCalledOnce()
        expect(fetchMock).toHaveBeenCalledWith('/api/admin/archive-entries/42', expect.objectContaining({
            method: 'PUT',
            body: JSON.stringify(entry),
        }))
    })

    it('patches media rights metadata after upload', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 7, rightsStatus: 'LICENSED' }))
        vi.stubGlobal('fetch', fetchMock)

        await mediaAssetsApi.update(7, { sourceReferenceId: 1, description: 'Description', rightsStatus: 'LICENSED', license: 'CC BY-SA 4.0', publicDisplayAllowed: true } as never)

        expect(fetchMock).toHaveBeenCalledWith('/api/admin/media-assets/7', expect.objectContaining({
            method: 'PATCH',
            body: JSON.stringify({ sourceReferenceId: 1, description: 'Description', rightsStatus: 'LICENSED', license: 'CC BY-SA 4.0', publicDisplayAllowed: true }),
        }))
    })

    it('loads private media through the protected admin content endpoint', async () => {
        setAdminSession({
            accessToken: 'signed-token',
            tokenType: 'Bearer',
            expiresAt: '2099-01-01T00:00:00Z',
            username: 'editor',
        })
        const fetchMock = vi.fn().mockResolvedValue(new Response('private-content', {
            headers: { 'Content-Type': 'image/jpeg' },
        }))
        vi.stubGlobal('fetch', fetchMock)

        await expect(getAdminMediaContent(17)).resolves.toBeInstanceOf(Blob)

        const [, request] = fetchMock.mock.calls[0]
        expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/media-assets/17/content')
        expect(new Headers(request.headers).get('Authorization')).toBe('Bearer signed-token')
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
        ontologyRegionalMotifIri: null,
        ontologyRegionalMotifLocalName: null,
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

function jsonResponse(body: unknown, status = 200) {
    return new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
    })
}

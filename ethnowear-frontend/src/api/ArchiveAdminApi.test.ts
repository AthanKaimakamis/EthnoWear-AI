import { afterEach, describe, expect, it, vi } from 'vitest'
import {
    getAdminArchiveItemDetail,
    getPublicationReadiness,
    runPublicationCommand,
} from './ArchiveAdminApi'

afterEach(() => {
    vi.unstubAllGlobals()
})

describe('archive publication API', () => {
    it('uses the protected admin detail endpoint for unpublished previews', async () => {
        const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ archiveItem: {}, source: {}, features: [], media: [] }))
        vi.stubGlobal('fetch', fetchMock)

        await getAdminArchiveItemDetail(42)

        expect(fetchMock).toHaveBeenCalledWith('/api/admin/archive-items/42/detail', expect.any(Object))
        expect(fetchMock.mock.calls[0][0]).not.toContain('/api/archive/items/')
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

function jsonResponse(body: unknown, status = 200) {
    return new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
    })
}

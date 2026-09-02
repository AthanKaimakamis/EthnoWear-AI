import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearAdminSession, setAdminSession } from '../app/adminAuthStore'
import { createOntologyEntity, listOntologyVersions, restoreOntologyVersion, updateOntologyEntity } from './OntologyAdminApi'

function jsonResponse(body: unknown) {
    return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

const input = {
    localName: 'CrossStitch', labelBg: 'Кръстат бод', labelEn: 'Cross stitch', altLabelsBg: [], altLabelsEn: [],
    commentBg: null, commentEn: null,
}

describe('OntologyAdminApi version management', () => {
    beforeEach(() => {
        clearAdminSession()
        sessionStorage.clear()
        setAdminSession({ accessToken: 'jwt-token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', username: 'admin' })
    })

    it('loads pageable safe version metadata with authorization', async () => {
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({ content: [], totalElements: 0 }))
        await listOntologyVersions({ page: 2, size: 20, sort: 'versionNumber,desc' })
        const [url, request] = fetchMock.mock.calls[0]
        expect(String(url)).toContain('/api/admin/ontology/versions?')
        expect(String(url)).toContain('page=2')
        expect((request?.headers as Headers).get('Authorization')).toBe('Bearer jwt-token')
    })

    it('restores a version with only the required reason', async () => {
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({ id: 8, versionNumber: 8 }))
        await restoreOntologyVersion(4, '  Recovery after review  ')
        const [url, request] = fetchMock.mock.calls[0]
        expect(String(url).endsWith('/api/admin/ontology/versions/4/restore')).toBe(true)
        expect(request?.method).toBe('POST')
        expect(JSON.parse(String(request?.body))).toEqual({ reason: 'Recovery after review' })
        expect(String(request?.body)).not.toContain('ontologyContent')
    })

    it('sends an optional change reason for ordinary edits', async () => {
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => jsonResponse(input))
        await createOntologyEntity('techniques', input, 'Added technique')
        await updateOntologyEntity('techniques', input.localName, input, 'Updated labels')
        expect((fetchMock.mock.calls[0][1]?.headers as Headers).get('X-Ontology-Change-Reason')).toBe('Added technique')
        expect((fetchMock.mock.calls[1][1]?.headers as Headers).get('X-Ontology-Change-Reason')).toBe('Updated labels')
    })
})

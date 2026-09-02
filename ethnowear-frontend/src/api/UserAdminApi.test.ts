import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createUser, deleteUser, getUsers, resetUserPassword } from './UserAdminApi'
import { clearAdminSession, setAdminSession } from '../app/adminAuthStore'

function jsonResponse(body: unknown, status = 200) {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('UserAdminApi', () => {
    beforeEach(() => {
        clearAdminSession()
        sessionStorage.clear()
        setAdminSession({ accessToken: 'jwt-token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', username: 'admin' })
        vi.restoreAllMocks()
    })

    it('loads a searched server page with Bearer authorization', async () => {
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({ content: [], totalElements: 0, totalPages: 0, number: 1, size: 20 }))

        await getUsers({ search: 'mila', page: 1, size: 20, sort: 'username,asc' })

        const [url, request] = fetchMock.mock.calls[0]
        expect(String(url)).toContain('/api/admin/users?')
        expect(String(url)).toContain('search=mila')
        expect(String(url)).toContain('page=1')
        expect((request?.headers as Headers).get('Authorization')).toBe('Bearer jwt-token')
    })

    it('never sends a chosen password when creating a user', async () => {
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({
            user: {}, credentials: { temporaryPassword: 'Generated!234', expiresAt: '2099-01-01T00:00:00' },
        }, 201))

        await createUser({
            username: 'reviewer', roles: ['REVIEWER'],
            profile: { firstName: 'Rada', lastName: 'Ivanova', email: null, phone: null, addressLine1: null, addressLine2: null, city: null, postalCode: null, countryCode: null },
        })

        const body = String(fetchMock.mock.calls[0][1]?.body)
        expect(body).not.toContain('password')
        expect(body).toContain('REVIEWER')
    })

    it('uses the reset endpoint and returns one-time credentials', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({ temporaryPassword: 'Generated!234', expiresAt: '2099-01-01T00:00:00' }))
        await expect(resetUserPassword(12)).resolves.toMatchObject({ temporaryPassword: 'Generated!234' })
    })

    it('deletes a user through the protected user endpoint', async () => {
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(null, { status: 204 }))

        await deleteUser(12)

        const [url, request] = fetchMock.mock.calls[0]
        expect(String(url)).toContain('/api/admin/users/12')
        expect(request?.method).toBe('DELETE')
        expect((request?.headers as Headers).get('Authorization')).toBe('Bearer jwt-token')
    })
})

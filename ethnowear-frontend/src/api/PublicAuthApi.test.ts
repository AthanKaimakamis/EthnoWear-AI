import { afterEach, describe, expect, it, vi } from 'vitest'
import { PublicAuthClient, PublicAuthError } from './PublicAuthApi'

const json = (value: unknown, status = 200, headers = {}) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json', ...headers } })
const csrf = () => json({ headerName: 'X-PUBLIC-CSRF', token: 'csrf-token' })
afterEach(() => vi.unstubAllGlobals())
describe('isolated public authentication client', () => {
    it('uses cookies and CSRF, never Authorization, and sends only the Google credential', async () => {
        const fetch = vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(json({ nonce: 'nonce', expiresAt: '' }))
            .mockResolvedValueOnce(json({ userId: '1', displayName: 'User', email: 'user@example.org' }))
        vi.stubGlobal('fetch', fetch)
        const client = new PublicAuthClient()
        await client.challenge()
        await client.login('google-credential')
        for (const [, options] of fetch.mock.calls) {
            expect(options.credentials).toBe('include')
            expect(options.headers.has('Authorization')).toBe(false)
        }
        expect(fetch.mock.calls[1][1].headers.get('X-PUBLIC-CSRF')).toBe('csrf-token')
        expect(JSON.parse(fetch.mock.calls[2][1].body)).toEqual({ credential: 'google-credential' })
    })
    it('refreshes CSRF once and stops repeated access-denied responses', async () => {
        const fetch = vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(json({ code: 'PUBLIC_ACCESS_DENIED' }, 403))
            .mockResolvedValueOnce(csrf()).mockResolvedValueOnce(json({ code: 'PUBLIC_ACCESS_DENIED' }, 403))
        vi.stubGlobal('fetch', fetch)
        await expect(new PublicAuthClient().challenge()).rejects.toMatchObject({ status: 403 })
        expect(fetch).toHaveBeenCalledTimes(4)
    })
    it('notifies only the public identity on 401', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({}, 401)))
        const signedOut = vi.fn()
        await expect(new PublicAuthClient(signedOut).me()).rejects.toBeInstanceOf(PublicAuthError)
        expect(signedOut).toHaveBeenCalledOnce()
    })
    it.each(['30', new Date(Date.now() + 60000).toUTCString()])('honors Retry-After %s without more requests', async value => {
        const fetch = vi.fn().mockResolvedValue(json({}, 429, { 'Retry-After': value }))
        vi.stubGlobal('fetch', fetch)
        const client = new PublicAuthClient()
        await expect(client.config()).rejects.toMatchObject({ status: 429 })
        await expect(client.config()).rejects.toMatchObject({ status: 429 })
        expect(fetch).toHaveBeenCalledOnce()
    })
    it('does not replay consumed or invalid Google challenges', async () => {
        const fetch = vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(json({ code: 'PUBLIC_LOGIN_CHALLENGE_INVALID' }, 401))
        vi.stubGlobal('fetch', fetch)
        await expect(new PublicAuthClient().login('credential')).rejects.toMatchObject({ code: 'PUBLIC_LOGIN_CHALLENGE_INVALID' })
        expect(fetch).toHaveBeenCalledTimes(2)
    })
    it('logs out using CSRF and accepts 204', async () => {
        const fetch = vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(new Response(null, { status: 204 }))
        vi.stubGlobal('fetch', fetch)
        await new PublicAuthClient().logout()
        expect(fetch.mock.calls[1][0]).toBe('/api/public/auth/logout')
        expect(fetch.mock.calls[1][1]).toMatchObject({ method: 'POST', credentials: 'include' })
    })
})

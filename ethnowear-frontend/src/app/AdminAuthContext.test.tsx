import { useState } from 'react'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../api/http'
import { useAdminAuth } from './adminAuth'
import { AdminAuthProvider } from './AdminAuthContext'
import { clearAdminSession, getAdminSession, setAdminSession } from './adminAuthStore'

function AuthProbe() {
    const auth = useAdminAuth()
    const [error, setError] = useState(false)
    return <>
        <span>{auth.initializing ? 'checking' : auth.authenticated ? `${auth.admin?.username}:${auth.admin?.passwordChangeRequired ? 'change' : 'ready'}` : 'anonymous'}</span>
        <button onClick={() => auth.login('curator', 'secret').catch(() => setError(true))}>login</button>
        <button onClick={() => auth.changePassword('old-password', 'New-password-123!').catch(() => setError(true))}>change</button>
        {error && <span>failed</span>}
    </>
}

function response(body: unknown, status = 200) {
    return new Response(body === null ? null : JSON.stringify(body), {
        status,
        headers: body === null ? undefined : { 'Content-Type': 'application/json' },
    })
}

describe('AdminAuthProvider', () => {
    beforeEach(() => {
        clearAdminSession()
        sessionStorage.clear()
        vi.restoreAllMocks()
    })

    it('restores a stored session after the backend verifies it', async () => {
        setAdminSession({
            accessToken: 'persisted-token', tokenType: 'Bearer',
            expiresAt: '2099-01-01T00:00:00Z', username: 'curator',
        })
        const fetchMock = vi.spyOn(globalThis, 'fetch')
        fetchMock.mockResolvedValueOnce(response({
            id: 7, username: 'curator', firstName: 'Mila', lastName: 'Petrova', email: 'mila@example.test',
            roles: ['EDITOR'], passwordChangeRequired: false,
        }))

        render(<AdminAuthProvider><AuthProbe /></AdminAuthProvider>)

        await waitFor(() => expect(screen.getByText('curator:ready')).toBeInTheDocument())
        expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/auth/me', expect.objectContaining({
            headers: expect.any(Headers),
        }))
        const firstHeaders = fetchMock.mock.calls[0][1]?.headers as Headers
        expect(firstHeaders.get('Authorization')).toBe('Bearer persisted-token')
    })

    it('exchanges credentials once and keeps the returned access token', async () => {
        const fetchMock = vi.spyOn(globalThis, 'fetch')
        fetchMock.mockResolvedValueOnce(response({
            accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 3600,
            expiresAt: '2099-01-01T00:00:00Z', passwordChangeRequired: false,
        }))
        fetchMock.mockResolvedValueOnce(response({
            id: 7, username: 'curator', firstName: 'Mila', lastName: 'Petrova', email: null,
            roles: ['EDITOR'], passwordChangeRequired: false,
        }))

        render(<AdminAuthProvider><AuthProbe /></AdminAuthProvider>)
        await userEvent.click(screen.getByRole('button', { name: 'login' }))

        await waitFor(() => expect(screen.getByText('curator:ready')).toBeInTheDocument())
        expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/login')
        const loginRequest = fetchMock.mock.calls[0][1]
        expect(loginRequest?.body).toBe(JSON.stringify({ username: 'curator', password: 'secret' }))
        expect((loginRequest?.headers as Headers).has('Authorization')).toBe(false)
        expect(getAdminSession()?.accessToken).toBe('new-token')
    })

    it('clears the session when login fails', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response({ message: 'Invalid credentials' }, 401))
        render(<AdminAuthProvider><AuthProbe /></AdminAuthProvider>)

        await userEvent.click(screen.getByRole('button', { name: 'login' }))

        await waitFor(() => expect(screen.getByText('failed')).toBeInTheDocument())
        await waitFor(() => expect(screen.getByText('anonymous')).toBeInTheDocument())
        expect(getAdminSession()).toBeNull()
    })

    it('signs out after a protected request returns 401', async () => {
        setAdminSession({
            accessToken: 'persisted-token', tokenType: 'Bearer',
            expiresAt: '2099-01-01T00:00:00Z', username: 'curator',
        })
        const fetchMock = vi.spyOn(globalThis, 'fetch')
        fetchMock.mockResolvedValueOnce(response({
            id: 7, username: 'curator', firstName: 'Mila', lastName: 'Petrova', email: null,
            roles: ['EDITOR'], passwordChangeRequired: false,
        }))
        render(<AdminAuthProvider><AuthProbe /></AdminAuthProvider>)
        await waitFor(() => expect(screen.getByText('curator:ready')).toBeInTheDocument())
        fetchMock.mockResolvedValueOnce(response({ message: 'Expired' }, 401))

        await act(async () => {
            await expect(apiRequest('/api/admin/archive-items')).rejects.toMatchObject({ status: 401 })
        })

        await waitFor(() => expect(screen.getByText('anonymous')).toBeInTheDocument())
        expect(getAdminSession()).toBeNull()
    })

    it('keeps the session after a protected request returns 403', async () => {
        setAdminSession({ accessToken: 'persisted-token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', username: 'curator' })
        const fetchMock = vi.spyOn(globalThis, 'fetch')
        fetchMock.mockResolvedValueOnce(response({ id: 7, username: 'curator', firstName: 'Mila', lastName: 'Petrova', email: null, roles: ['EDITOR'], passwordChangeRequired: false }))
        render(<AdminAuthProvider><AuthProbe /></AdminAuthProvider>)
        await waitFor(() => expect(screen.getByText('curator:ready')).toBeInTheDocument())
        fetchMock.mockResolvedValueOnce(response({ message: 'Forbidden' }, 403))

        await act(async () => {
            await expect(apiRequest('/api/admin/users')).rejects.toMatchObject({ status: 403 })
        })

        expect(screen.getByText('curator:ready')).toBeInTheDocument()
        expect(getAdminSession()).not.toBeNull()
    })

    it('forces password change and replaces the invalidated token without signing the user out', async () => {
        const fetchMock = vi.spyOn(globalThis, 'fetch')
        fetchMock.mockResolvedValueOnce(response({ accessToken: 'temporary-token', tokenType: 'Bearer', expiresIn: 900, expiresAt: '2099-01-01T00:00:00Z', passwordChangeRequired: true }))
        fetchMock.mockResolvedValueOnce(response({ id: 8, username: 'curator', firstName: 'Mila', lastName: 'Petrova', email: null, roles: ['EDITOR'], passwordChangeRequired: true }))
        render(<AdminAuthProvider><AuthProbe /></AdminAuthProvider>)

        await userEvent.click(screen.getByRole('button', { name: 'login' }))
        await waitFor(() => expect(screen.getByText('curator:change')).toBeInTheDocument())
        fetchMock.mockResolvedValueOnce(response(null, 204))
        fetchMock.mockResolvedValueOnce(response({ accessToken: 'replacement-token', tokenType: 'Bearer', expiresIn: 900, expiresAt: '2099-01-01T00:00:00Z', passwordChangeRequired: false }))
        fetchMock.mockResolvedValueOnce(response({ id: 8, username: 'curator', firstName: 'Mila', lastName: 'Petrova', email: null, roles: ['EDITOR'], passwordChangeRequired: false }))
        await userEvent.click(screen.getByRole('button', { name: 'change' }))

        await waitFor(() => expect(screen.getByText('curator:ready')).toBeInTheDocument())
        expect(fetchMock.mock.calls[2][0]).toBe('/api/auth/password/change')
        expect(fetchMock.mock.calls[3][0]).toBe('/api/auth/login')
        expect(fetchMock.mock.calls[3][1]?.body).toBe(JSON.stringify({ username: 'curator', password: 'New-password-123!' }))
        expect(getAdminSession()?.accessToken).toBe('replacement-token')
    })
})

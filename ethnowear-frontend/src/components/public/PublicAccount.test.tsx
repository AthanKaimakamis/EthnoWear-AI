import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PublicAccount, { publicAuthErrorKey } from './PublicAccount'
import { PublicAuthError } from '../../api/PublicAuthApi'

const mocks = vi.hoisted(() => ({
    state: { config: { enabled: true, googleClientId: 'google-client' }, profile: null as { userId: string; displayName: string; email: string } | null, ready: true },
    challenge: vi.fn(), login: vi.fn(), logout: vi.fn(), initialize: vi.fn(), renderButton: vi.fn(),
}))
vi.mock('../../app/publicAuthStore', () => ({
    usePublicAuth: () => mocks.state, initializePublicAuth: vi.fn(),
    loginPublicUser: mocks.login, logoutPublicUser: mocks.logout,
    publicAuthClient: { challenge: mocks.challenge },
}))
vi.mock('../../api/googleIdentity', () => ({ loadGoogleIdentity: async () => ({ initialize: mocks.initialize, renderButton: mocks.renderButton }) }))
beforeEach(() => {
    vi.clearAllMocks()
    mocks.state.profile = null
    mocks.state.config.enabled = true
    mocks.challenge.mockResolvedValue({ nonce: 'server-nonce', expiresAt: new Date(Date.now() + 300000).toISOString() })
    mocks.login.mockResolvedValue(undefined)
})
describe('public Google login control', () => {
    it('shows signed-in identity and opens manager login without changing public identity', () => {
        mocks.state.profile = { userId: '1', displayName: 'Maria', email: 'maria@example.org' }
        const managerLogin = vi.fn()
        render(<PublicAccount onManagerLogin={managerLogin} />)
        fireEvent.click(screen.getByRole('button', { name: 'Signed in as Maria.' }))
        expect(screen.getByText('maria@example.org')).toBeVisible()
        fireEvent.click(screen.getByRole('menuitem', { name: 'Log in as manager' }))
        expect(managerLogin).toHaveBeenCalledOnce()
        expect(mocks.logout).not.toHaveBeenCalled()
        expect(mocks.login).not.toHaveBeenCalled()
    })
    it('keeps public logout in the account menu', async () => {
        mocks.state.profile = { userId: '1', displayName: 'Maria', email: 'maria@example.org' }
        render(<PublicAccount />)
        fireEvent.click(screen.getByRole('button', { name: 'Signed in as Maria.' }))
        expect(screen.queryByRole('menuitem', { name: 'Log in as manager' })).not.toBeInTheDocument()
        fireEvent.click(screen.getByRole('menuitem', { name: 'Sign out of personal account' }))
        await waitFor(() => expect(mocks.logout).toHaveBeenCalledOnce())
    })
    it('finishes login when the profile update cleans up the Google effect first', async () => {
        let finish!: () => void
        mocks.login.mockImplementationOnce(() => new Promise<void>(resolve => { finish = resolve }))
        const onSuccess = vi.fn()
        const onPendingChange = vi.fn()
        const view = render(<PublicAccount loginPanel onSuccess={onSuccess} onPendingChange={onPendingChange} />)
        await waitFor(() => expect(mocks.initialize).toHaveBeenCalledOnce())
        act(() => mocks.initialize.mock.calls[0][0].callback({ credential: 'credential' }))
        expect(screen.getByRole('progressbar')).toBeVisible()
        mocks.state.profile = { userId: '1', displayName: 'Maria', email: 'maria@example.org' }
        view.rerender(<PublicAccount loginPanel onSuccess={onSuccess} onPendingChange={onPendingChange} />)
        await act(async () => { finish() })
        expect(onSuccess).toHaveBeenCalledOnce()
        expect(onPendingChange).toHaveBeenLastCalledWith(false)
        expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    })
    it('does not close a different screen if login finishes after unmount', async () => {
        let finish!: () => void
        mocks.login.mockImplementationOnce(() => new Promise<void>(resolve => { finish = resolve }))
        const onSuccess = vi.fn()
        const view = render(<PublicAccount loginPanel onSuccess={onSuccess} />)
        await waitFor(() => expect(mocks.initialize).toHaveBeenCalledOnce())
        act(() => mocks.initialize.mock.calls[0][0].callback({ credential: 'credential' }))
        view.unmount()
        await act(async () => { finish() })
        expect(onSuccess).not.toHaveBeenCalled()
    })
    it.each([240, 348, 500])('gives the Google iframe an explicit responsive width at %s pixels', async (width) => {
        const measurement = vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(width)
        try {
            render(<PublicAccount loginPanel />)
            await waitFor(() => expect(mocks.renderButton).toHaveBeenCalledOnce())
            expect(mocks.renderButton.mock.calls[0][1]).toMatchObject({ width: Math.min(400, width) })
        } finally { measurement.mockRestore() }
    })
    it('keeps the Google iframe mounted during the credential handoff', async () => {
        let finish!: () => void
        mocks.login.mockImplementationOnce(() => new Promise<void>(resolve => { finish = resolve }))
        mocks.renderButton.mockImplementationOnce((host: HTMLElement) => {
            const frame = document.createElement('iframe')
            frame.title = 'Google sign-in'
            host.appendChild(frame)
        })
        render(<PublicAccount loginPanel />)
        await waitFor(() => expect(mocks.initialize).toHaveBeenCalledOnce())
        const frame = screen.getByTitle('Google sign-in')
        act(() => mocks.initialize.mock.calls[0][0].callback({ credential: 'credential' }))
        expect(frame).toBeInTheDocument()
        await act(async () => { finish() })
        expect(frame).toBeInTheDocument()
    })
    it('hides disabled Google login', () => {
        mocks.state.config.enabled = false
        render(<PublicAccount />)
        expect(screen.queryByRole('button')).not.toBeInTheDocument()
    })
    it('uses the server client ID and nonce without automatic login', async () => {
        render(<PublicAccount loginPanel />)
        await waitFor(() => expect(mocks.initialize).toHaveBeenCalled())
        expect(mocks.initialize.mock.calls[0][0]).toMatchObject({ client_id: 'google-client', nonce: 'server-nonce', auto_select: false })
        expect(mocks.login).not.toHaveBeenCalled()
    })
    it('suppresses duplicate credential callbacks and permits a fresh challenge after invalid login', async () => {
        mocks.login.mockRejectedValueOnce(new PublicAuthError(401, 'PUBLIC_LOGIN_CHALLENGE_INVALID'))
        render(<PublicAccount loginPanel />)
        await waitFor(() => expect(mocks.initialize).toHaveBeenCalledOnce())
        const callback = mocks.initialize.mock.calls[0][0].callback
        callback({ credential: 'credential' })
        callback({ credential: 'credential' })
        await waitFor(() => expect(mocks.challenge).toHaveBeenCalledTimes(2))
        expect(mocks.login).toHaveBeenCalledOnce()
    })
    it.each([
        [403, 'PUBLIC_ACCOUNT_DISABLED', 'disabled'], [403, 'PUBLIC_ACCESS_DENIED', 'denied'],
        [401, 'PUBLIC_AUTH_REQUIRED', 'restart'], [429, 'RATE_LIMIT', 'rateLimited'], [503, 'CONFIG_MISSING', 'unavailable'],
    ])('maps %s %s without backend text', (status, code, key) => {
        expect(publicAuthErrorKey(new PublicAuthError(status as number, code as string))).toBe(key)
    })
})

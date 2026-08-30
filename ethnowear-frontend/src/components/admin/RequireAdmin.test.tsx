import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { lightTheme } from '../../app/theme'
import { useAdminAuth } from '../../app/adminAuth'
import RequireAdmin from './RequireAdmin'

vi.mock('../../app/adminAuth', () => ({ useAdminAuth: vi.fn() }))

const mockedUseAdminAuth = vi.mocked(useAdminAuth)

function renderGuard(sessionExpired: boolean) {
    const logout = vi.fn()
    mockedUseAdminAuth.mockReturnValue({
        admin: null,
        authenticated: false,
        initializing: false,
        sessionExpired,
        login: vi.fn(),
        logout,
        changePassword: vi.fn(),
    })

    render(
        <ThemeProvider theme={lightTheme}>
            <MemoryRouter initialEntries={['/management']}>
                <Routes>
                    <Route path="/management" element={<RequireAdmin><div>Protected content</div></RequireAdmin>} />
                    <Route path="/archive" element={<div>Public archive</div>} />
                </Routes>
            </MemoryRouter>
        </ThemeProvider>,
    )

    return logout
}

describe('RequireAdmin', () => {
    it('preserves protected-page draft state while the expired session is restored', async () => {
        const authenticated = {
            admin: { id: 1, username: 'admin', firstName: 'Admin', lastName: 'User', email: null, roles: ['ADMINISTRATOR' as const], passwordChangeRequired: false },
            authenticated: true,
            initializing: false,
            sessionExpired: false,
            login: vi.fn(),
            logout: vi.fn(),
            changePassword: vi.fn(),
        }
        mockedUseAdminAuth.mockReturnValue(authenticated as ReturnType<typeof useAdminAuth>)

        const view = render(
            <ThemeProvider theme={lightTheme}>
                <MemoryRouter><RequireAdmin><input aria-label="Draft title" defaultValue="" /></RequireAdmin></MemoryRouter>
            </ThemeProvider>,
        )
        await userEvent.type(screen.getByRole('textbox', { name: 'Draft title' }), 'Unsaved edit')

        mockedUseAdminAuth.mockReturnValue({ ...authenticated, admin: null, authenticated: false, sessionExpired: true } as ReturnType<typeof useAdminAuth>)
        view.rerender(
            <ThemeProvider theme={lightTheme}>
                <MemoryRouter><RequireAdmin><input aria-label="Draft title" defaultValue="" /></RequireAdmin></MemoryRouter>
            </ThemeProvider>,
        )
        mockedUseAdminAuth.mockReturnValue(authenticated as ReturnType<typeof useAdminAuth>)
        view.rerender(
            <ThemeProvider theme={lightTheme}>
                <MemoryRouter><RequireAdmin><input aria-label="Draft title" defaultValue="" /></RequireAdmin></MemoryRouter>
            </ThemeProvider>,
        )

        expect(screen.getByRole('textbox', { name: 'Draft title' })).toHaveValue('Unsaved edit')
    })

    it('returns to the public archive when the expired-session dialog is closed', async () => {
        const logout = renderGuard(true)

        await userEvent.click(screen.getByRole('button', { name: 'Close' }))

        expect(logout).toHaveBeenCalledOnce()
        expect(screen.getByText('Public archive')).toBeInTheDocument()
    })

    it('keeps the login dialog non-dismissible for direct unauthenticated access', () => {
        renderGuard(false)

        expect(screen.getByRole('button', { name: 'Close' })).toBeDisabled()
    })
})

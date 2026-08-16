import { useMemo, useState, type ReactNode } from 'react'
import { apiRequest } from '../api/http'
import { clearAdminCredentials, setAdminCredentials } from './adminAuthStore'
import { AdminAuthContext, type AdminAuth } from './adminAuth'

export function AdminAuthProvider({ children }: { children: ReactNode }) {
    const [authenticated, setAuthenticated] = useState(false)

    const value = useMemo<AdminAuth>(() => ({
        authenticated,
        async login(username: string, password: string) {
            setAdminCredentials(username, password)

            try {
                await apiRequest<void>('/api/admin/auth')
                setAuthenticated(true)
            } catch (error) {
                clearAdminCredentials()
                setAuthenticated(false)
                throw error
            }
        },
        logout() {
            clearAdminCredentials()
            setAuthenticated(false)
        },
    }), [authenticated])

    return <AdminAuthContext value={value}>{children}</AdminAuthContext>
}

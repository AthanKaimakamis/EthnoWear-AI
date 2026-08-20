import { createContext, useContext } from 'react'
import type { CurrentUser } from '../api/AdminAuthApi'

export type AdminAuth = {
    authenticated: boolean
    initializing: boolean
    admin: CurrentUser | null
    login: (username: string, password: string) => Promise<CurrentUser>
    logout: () => void
    changePassword: (currentPassword: string, newPassword: string) => Promise<void>
}

export const AdminAuthContext = createContext<AdminAuth | null>(null)

export function useAdminAuth() {
    const value = useContext(AdminAuthContext)
    if (!value) throw new Error('useAdminAuth must be used inside AdminAuthProvider')
    return value
}

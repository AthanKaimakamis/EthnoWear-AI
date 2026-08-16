import { createContext, useContext } from 'react'

export type AdminAuth = {
    authenticated: boolean
    login: (username: string, password: string) => Promise<void>
    logout: () => void
}

export const AdminAuthContext = createContext<AdminAuth | null>(null)

export function useAdminAuth() {
    const value = useContext(AdminAuthContext)
    if (!value) throw new Error('useAdminAuth must be used inside AdminAuthProvider')
    return value
}

import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { changeCurrentPassword, getCurrentAdmin, loginAdmin, type CurrentUser } from '../api/AdminAuthApi'
import { clearAdminSession, getAdminSession, subscribeToAdminSessionChanges } from './adminAuthStore'
import { AdminAuthContext, type AdminAuth } from './adminAuth'

export function AdminAuthProvider({ children }: { children: ReactNode }) {
    const [admin, setAdmin] = useState<CurrentUser | null>(null)
    const [initializing, setInitializing] = useState(() => getAdminSession() !== null)

    useEffect(() => {
        const unsubscribe = subscribeToAdminSessionChanges(() => {
            if (!getAdminSession()) {
                setAdmin(null)
                setInitializing(false)
            }
        })
        const controller = new AbortController()

        if (getAdminSession()) {
            getCurrentAdmin(controller.signal)
                .then(setAdmin)
                .catch(error => {
                    if (error instanceof DOMException && error.name === 'AbortError') return
                    clearAdminSession()
                    setAdmin(null)
                })
                .finally(() => {
                    if (!controller.signal.aborted) setInitializing(false)
                })
        }

        return () => {
            controller.abort()
            unsubscribe()
        }
    }, [])

    const login = useCallback(async (username: string, password: string) => {
        clearAdminSession()
        setAdmin(null)
        const token = await loginAdmin(username, password)

        try {
            const identity = await getCurrentAdmin()
            const currentUser = token.passwordChangeRequired && !identity.passwordChangeRequired
                ? { ...identity, passwordChangeRequired: true }
                : identity
            setAdmin(currentUser)
            return currentUser
        } catch (error) {
            clearAdminSession()
            throw error
        }
    }, [])

    const logout = useCallback(() => {
        clearAdminSession()
        setAdmin(null)
    }, [])

    const changePassword = useCallback(async (currentPassword: string, newPassword: string) => {
        const username = admin?.username
        if (!username) throw new Error('The authenticated user is not available.')

        await changeCurrentPassword(currentPassword, newPassword)

        try {
            clearAdminSession()
            await loginAdmin(username, newPassword)
            const currentUser = await getCurrentAdmin()
            setAdmin(currentUser)
        } catch (error) {
            clearAdminSession()
            setAdmin(null)
            throw error
        }
    }, [admin?.username])

    const value = useMemo<AdminAuth>(() => ({
        authenticated: admin !== null,
        initializing,
        admin,
        login,
        logout,
        changePassword,
    }), [admin, changePassword, initializing, login, logout])

    return <AdminAuthContext value={value}>{children}</AdminAuthContext>
}

import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { changeCurrentPassword, getCurrentAdmin, loginAdmin, refreshAdminToken, type CurrentUser } from '../api/AdminAuthApi'
import { clearAdminSession, getAdminSession, subscribeToAdminSessionChanges } from './adminAuthStore'
import { AdminAuthContext, type AdminAuth } from './adminAuth'

export function AdminAuthProvider({ children }: { children: ReactNode }) {
    const [admin, setAdmin] = useState<CurrentUser | null>(null)
    const [initializing, setInitializing] = useState(() => getAdminSession() !== null)
    const [sessionExpired, setSessionExpired] = useState(false)
    const adminRef = useRef<CurrentUser | null>(null)
    const refreshingRef = useRef(false)

    useEffect(() => { adminRef.current = admin }, [admin])

    useEffect(() => {
        const unsubscribe = subscribeToAdminSessionChanges(reason => {
            if (!getAdminSession()) {
                if (adminRef.current && (reason === 'expired' || reason === 'unauthorized')) setSessionExpired(true)
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

    useEffect(() => {
        let lastActivity = Date.now()
        const active = () => { lastActivity = Date.now() }
        const events: (keyof WindowEventMap)[] = ['pointerdown', 'keydown', 'scroll', 'focus']
        events.forEach(event => window.addEventListener(event, active, { passive: true }))
        const timer = window.setInterval(async () => {
            const session = getAdminSession()
            if (!session || refreshingRef.current || Date.now() - lastActivity > 5 * 60_000) return
            if (Date.parse(session.expiresAt) - Date.now() > 5 * 60_000) return
            refreshingRef.current = true
            try { await refreshAdminToken() } catch { /* 401 handling clears the session. */ }
            finally { refreshingRef.current = false }
        }, 60_000)
        return () => {
            window.clearInterval(timer)
            events.forEach(event => window.removeEventListener(event, active))
        }
    }, [])

    const login = useCallback(async (username: string, password: string) => {
        clearAdminSession()
        setAdmin(null)
        setSessionExpired(false)
        const token = await loginAdmin(username, password)

        try {
            const identity = await getCurrentAdmin()
            const currentUser = token.passwordChangeRequired && !identity.passwordChangeRequired
                ? { ...identity, passwordChangeRequired: true }
                : identity
            setAdmin(currentUser)
            setSessionExpired(false)
            return currentUser
        } catch (error) {
            clearAdminSession()
            throw error
        }
    }, [])

    const logout = useCallback(() => {
        clearAdminSession()
        setAdmin(null)
        setSessionExpired(false)
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
        sessionExpired,
        admin,
        login,
        logout,
        changePassword,
    }), [admin, changePassword, initializing, login, logout, sessionExpired])

    return <AdminAuthContext value={value}>{children}</AdminAuthContext>
}

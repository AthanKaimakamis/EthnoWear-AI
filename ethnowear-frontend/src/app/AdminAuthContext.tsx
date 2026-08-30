import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { changeCurrentPassword, getCurrentAdmin, loginAdmin, refreshAdminToken, type CurrentUser } from '../api/AdminAuthApi'
import { clearAdminSession, getAdminSession, restoreAdminSessionFromAnotherTab, subscribeToAdminSessionChanges } from './adminAuthStore'
import { AdminAuthContext, type AdminAuth } from './adminAuth'

const SESSION_IDLE_LIMIT_MS = 30 * 60_000
const SESSION_REFRESH_LEAD_MS = 3 * 60_000
const SESSION_REFRESH_CHECK_MS = 30_000

export function AdminAuthProvider({ children }: { children: ReactNode }) {
    const [admin, setAdmin] = useState<CurrentUser | null>(null)
    const [initializing, setInitializing] = useState(true)
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

        async function restoreSession() {
            const session = getAdminSession() ?? await restoreAdminSessionFromAnotherTab()
            if (controller.signal.aborted) return
            if (!session) {
                setInitializing(false)
                return
            }
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
        void restoreSession()

        return () => {
            controller.abort()
            unsubscribe()
        }
    }, [])

    useEffect(() => {
        let lastActivity = Date.now()
        const refreshIfNeeded = async () => {
            const session = getAdminSession()
            if (!session || refreshingRef.current || Date.now() - lastActivity > SESSION_IDLE_LIMIT_MS) return
            if (Date.parse(session.expiresAt) - Date.now() > SESSION_REFRESH_LEAD_MS) return
            refreshingRef.current = true
            try { await refreshAdminToken() } catch { /* 401 handling clears the session. */ }
            finally { refreshingRef.current = false }
        }
        const active = () => {
            lastActivity = Date.now()
            void refreshIfNeeded()
        }
        const windowEvents: (keyof WindowEventMap)[] = ['pointerdown', 'keydown', 'focus']
        windowEvents.forEach(event => window.addEventListener(event, active, { passive: true }))
        document.addEventListener('scroll', active, { capture: true, passive: true })
        document.addEventListener('visibilitychange', active)
        const timer = window.setInterval(() => void refreshIfNeeded(), SESSION_REFRESH_CHECK_MS)
        return () => {
            window.clearInterval(timer)
            windowEvents.forEach(event => window.removeEventListener(event, active))
            document.removeEventListener('scroll', active, { capture: true })
            document.removeEventListener('visibilitychange', active)
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

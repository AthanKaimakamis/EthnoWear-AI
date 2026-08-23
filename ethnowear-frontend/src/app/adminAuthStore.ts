export type AdminSession = {
    accessToken: string
    tokenType: 'Bearer'
    expiresAt: string
    username: string
}

const STORAGE_KEY = 'ethnowear.admin.session'
const SESSION_CHANGED_EVENT = 'ethnowear:admin-session-changed'

export type SessionChangeReason = 'updated' | 'expired' | 'unauthorized' | 'logout'

let adminSession = readStoredSession()
let expiryTimer: ReturnType<typeof setTimeout> | null = null

function storage() {
    if (typeof window === 'undefined') return null
    try {
        return window.sessionStorage
    } catch {
        return null
    }
}

function isValidSession(value: unknown): value is AdminSession {
    if (!value || typeof value !== 'object') return false
    const candidate = value as Partial<AdminSession>
    return candidate.tokenType === 'Bearer'
        && typeof candidate.accessToken === 'string'
        && candidate.accessToken.length > 0
        && typeof candidate.expiresAt === 'string'
        && Number.isFinite(Date.parse(candidate.expiresAt))
        && Date.parse(candidate.expiresAt) > Date.now()
        && typeof candidate.username === 'string'
        && candidate.username.length > 0
}

function readStoredSession() {
    const stored = storage()?.getItem(STORAGE_KEY)
    if (!stored) return null

    try {
        const parsed: unknown = JSON.parse(stored)
        if (isValidSession(parsed)) return parsed
    } catch {
        // Corrupt session data is handled as a signed-out session.
    }

    storage()?.removeItem(STORAGE_KEY)
    return null
}

function notifySessionChanged(reason: SessionChangeReason) {
    if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent(SESSION_CHANGED_EVENT, { detail: reason }))
}

function scheduleExpiry(session: AdminSession | null) {
    if (expiryTimer) clearTimeout(expiryTimer)
    expiryTimer = null
    if (!session) return

    const delay = Date.parse(session.expiresAt) - Date.now()
    if (delay <= 0) {
        clearAdminSession('expired')
        return
    }

    expiryTimer = setTimeout(() => clearAdminSession('expired'), Math.min(delay, 2_147_483_647))
}

export function getAdminSession() {
    if (adminSession && Date.parse(adminSession.expiresAt) <= Date.now()) {
        clearAdminSession('expired')
    }
    return adminSession
}

export function getAdminAuthorization() {
    const session = getAdminSession()
    return session ? `${session.tokenType} ${session.accessToken}` : null
}

export function getAdminUsername() {
    return getAdminSession()?.username ?? null
}

export function setAdminSession(session: AdminSession) {
    adminSession = session
    storage()?.setItem(STORAGE_KEY, JSON.stringify(session))
    scheduleExpiry(session)
    notifySessionChanged('updated')
}

export function clearAdminSession(reason: SessionChangeReason = 'logout') {
    const changed = adminSession !== null || storage()?.getItem(STORAGE_KEY) !== null
    adminSession = null
    storage()?.removeItem(STORAGE_KEY)
    scheduleExpiry(null)
    if (changed) notifySessionChanged(reason)
}

scheduleExpiry(adminSession)

export function subscribeToAdminSessionChanges(listener: (reason: SessionChangeReason) => void) {
    if (typeof window === 'undefined') return () => undefined
    const handler = (event: Event) => listener((event as CustomEvent<SessionChangeReason>).detail)
    window.addEventListener(SESSION_CHANGED_EVENT, handler)
    return () => window.removeEventListener(SESSION_CHANGED_EVENT, handler)
}

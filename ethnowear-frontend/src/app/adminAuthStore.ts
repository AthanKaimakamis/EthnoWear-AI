export type AdminSession = {
    accessToken: string
    tokenType: 'Bearer'
    expiresAt: string
    username: string
}

const STORAGE_KEY = 'ethnowear.admin.session'
const SESSION_CHANGED_EVENT = 'ethnowear:admin-session-changed'
const SESSION_CHANNEL = 'ethnowear:admin-session'
const TAB_ID = typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`

export type SessionChangeReason = 'updated' | 'expired' | 'unauthorized' | 'logout'

let adminSession = readStoredSession()
let expiryTimer: ReturnType<typeof setTimeout> | null = null
let sessionChannel: BroadcastChannel | null | undefined
const pendingRestores = new Map<string, (session: AdminSession | null) => void>()

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

function getSessionChannel() {
    if (sessionChannel !== undefined) return sessionChannel
    sessionChannel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(SESSION_CHANNEL)
    if (sessionChannel) sessionChannel.addEventListener('message', handleChannelMessage)
    return sessionChannel
}

function handleChannelMessage(event: MessageEvent) {
    const message = event.data as { type?: string; source?: string; target?: string; requestId?: string; session?: unknown; reason?: SessionChangeReason }
    if (!message || message.source === TAB_ID) return

    if (message.type === 'session-request') {
        const session = getAdminSession()
        if (session && message.requestId) getSessionChannel()?.postMessage({ type: 'session-response', source: TAB_ID, target: message.source, requestId: message.requestId, session })
        return
    }
    if (message.type === 'session-response' && message.target === TAB_ID && message.requestId) {
        const resolve = pendingRestores.get(message.requestId)
        if (!resolve) return
        pendingRestores.delete(message.requestId)
        resolve(isValidSession(message.session) ? message.session : null)
        return
    }
    if (message.type === 'session-updated' && isValidSession(message.session)) setAdminSessionInternal(message.session)
    if (message.type === 'session-cleared') clearAdminSessionInternal(message.reason ?? 'logout')
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
    setAdminSessionInternal(session)
    getSessionChannel()?.postMessage({ type: 'session-updated', source: TAB_ID, session })
}

function setAdminSessionInternal(session: AdminSession) {
    adminSession = session
    storage()?.setItem(STORAGE_KEY, JSON.stringify(session))
    scheduleExpiry(session)
    notifySessionChanged('updated')
}

export function clearAdminSession(reason: SessionChangeReason = 'logout') {
    const changed = clearAdminSessionInternal(reason)
    if (changed) getSessionChannel()?.postMessage({ type: 'session-cleared', source: TAB_ID, reason })
}

function clearAdminSessionInternal(reason: SessionChangeReason) {
    const changed = adminSession !== null || storage()?.getItem(STORAGE_KEY) !== null
    adminSession = null
    storage()?.removeItem(STORAGE_KEY)
    scheduleExpiry(null)
    if (changed) notifySessionChanged(reason)
    return changed
}

export function restoreAdminSessionFromAnotherTab(timeoutMs = 300) {
    const current = getAdminSession()
    if (current) return Promise.resolve(current)
    const channel = getSessionChannel()
    if (!channel) return Promise.resolve(null)

    return new Promise<AdminSession | null>(resolve => {
        const requestId = `${TAB_ID}:${Date.now()}:${Math.random()}`
        const timeout = window.setTimeout(() => {
            pendingRestores.delete(requestId)
            resolve(null)
        }, timeoutMs)
        pendingRestores.set(requestId, session => {
            window.clearTimeout(timeout)
            if (session) setAdminSessionInternal(session)
            resolve(session)
        })
        channel.postMessage({ type: 'session-request', source: TAB_ID, requestId })
    })
}

scheduleExpiry(adminSession)

export function subscribeToAdminSessionChanges(listener: (reason: SessionChangeReason) => void) {
    if (typeof window === 'undefined') return () => undefined
    getSessionChannel()
    const handler = (event: Event) => listener((event as CustomEvent<SessionChangeReason>).detail)
    window.addEventListener(SESSION_CHANGED_EVENT, handler)
    return () => window.removeEventListener(SESSION_CHANGED_EVENT, handler)
}

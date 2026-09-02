export type PublicProfile = { userId: string; displayName: string; email: string }
export type PublicAuthConfig = { enabled: boolean; googleClientId: string | null }
export type PublicLoginChallenge = { nonce: string; expiresAt: string }

export class PublicAuthError extends Error {
    status: number
    code: string
    retryAt: number
    constructor(status: number, code: string, retryAt = 0) {
        super(code)
        this.status = status
        this.code = code
        this.retryAt = retryAt
    }
}

// Deliberately independent of the management HTTP client and its authorization hooks.
export class PublicAuthClient {
    private csrf: string | null = null
    private csrfRequest: Promise<void> | null = null
    private retryAt = 0

    private signedOut: () => void
    constructor(signedOut: () => void = () => {}) { this.signedOut = signedOut }

    async request<T>(path: string, body?: object, mutation = false, refreshAllowed = true): Promise<T> {
        if (Date.now() < this.retryAt) throw new PublicAuthError(429, 'PUBLIC_RATE_LIMITED', this.retryAt)
        if (mutation && !this.csrf) await this.refreshCsrf()
        const headers = new Headers({ Accept: 'application/json' })
        if (mutation) headers.set('X-PUBLIC-CSRF', this.csrf!)
        if (body) headers.set('Content-Type', 'application/json')
        const response = await fetch(`/api/public/auth/${path}`, {
            method: mutation ? 'POST' : 'GET', credentials: 'include', cache: 'no-store',
            headers, ...(body ? { body: JSON.stringify(body) } : {}),
        })
        if (!response.ok) {
            const details = await response.json().catch(() => ({}))
            const code = typeof details.code === 'string' ? details.code : 'PUBLIC_AUTH_UNAVAILABLE'
            if (response.status === 401) this.signedOut()
            if (response.status === 429) {
                const value = response.headers.get('Retry-After')
                const seconds = value ? Number(value) : NaN
                this.retryAt = Number.isFinite(seconds) ? Date.now() + Math.max(0, seconds) * 1000
                    : Math.max(Date.now() + 1000, value ? Date.parse(value) || Date.now() + 30000 : Date.now() + 30000)
            }
            if (mutation && code === 'PUBLIC_ACCESS_DENIED' && refreshAllowed) {
                await this.refreshCsrf()
                return this.request<T>(path, body, true, false)
            }
            throw new PublicAuthError(response.status, code, this.retryAt)
        }
        return response.status === 204 ? undefined as T : response.json()
    }

    refreshCsrf() {
        if (!this.csrfRequest) {
            this.csrf = null
            this.csrfRequest = this.request<{ headerName: string; token: string }>('csrf').then(value => {
                if (value.headerName !== 'X-PUBLIC-CSRF' || !value.token) throw new PublicAuthError(503, 'PUBLIC_AUTH_UNAVAILABLE')
                this.csrf = value.token
            }).finally(() => { this.csrfRequest = null })
        }
        return this.csrfRequest
    }

    config = () => this.request<PublicAuthConfig>('config')
    me = () => this.request<PublicProfile>('me')
    challenge = () => this.request<PublicLoginChallenge>('google/challenge', undefined, true)
    login = (credential: string) => this.request<PublicProfile>('google', { credential }, true)
    logout = () => this.request<void>('logout', undefined, true)
}

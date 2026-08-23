import { getAdminSession, setAdminSession, type AdminSession } from '../app/adminAuthStore'
import type { RoleName } from '../app/permissions'
import { apiRequest } from './http'

export type AuthTokenDetails = {
    accessToken: string
    tokenType: string
    expiresIn: number
    expiresAt: string
    passwordChangeRequired: boolean
}

export type CurrentUser = {
    id: number
    username: string
    firstName: string
    lastName: string
    email: string | null
    roles: RoleName[]
    passwordChangeRequired: boolean
}

export async function loginAdmin(username: string, password: string) {
    const token = await apiRequest<AuthTokenDetails>('/api/auth/login', {
        method: 'POST',
        body: { username, password },
        authorization: 'none',
    })

    if (token.tokenType.toLowerCase() !== 'bearer' || !token.accessToken || !token.expiresAt) {
        throw new Error('The authentication server returned an invalid access token.')
    }

    const session: AdminSession = {
        accessToken: token.accessToken,
        tokenType: 'Bearer',
        expiresAt: token.expiresAt,
        username,
    }
    setAdminSession(session)
    return token
}

export async function refreshAdminToken() {
    const username = getAdminSession()?.username
    if (!username) throw new Error('No session is available to refresh.')
    const token = await apiRequest<AuthTokenDetails>('/api/auth/refresh', {
        method: 'POST',
        authorization: 'protected',
    })
    setAdminSession({ accessToken: token.accessToken, tokenType: 'Bearer', expiresAt: token.expiresAt, username })
    return token
}

export function getCurrentAdmin(signal?: AbortSignal) {
    return apiRequest<CurrentUser>('/api/auth/me', {
        signal,
        authorization: 'protected',
    })
}

export function changeCurrentPassword(currentPassword: string, newPassword: string) {
    return apiRequest<void>('/api/auth/password/change', {
        method: 'POST',
        body: { currentPassword, newPassword },
        authorization: 'protected',
    })
}

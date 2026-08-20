import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
    clearAdminSession,
    getAdminAuthorization,
    getAdminSession,
    getAdminUsername,
    setAdminSession,
} from './adminAuthStore'

describe('adminAuthStore', () => {
    beforeEach(() => {
        clearAdminSession()
        sessionStorage.clear()
        vi.useRealTimers()
    })

    it('stores only the Bearer session and restores its authorization value', () => {
        setAdminSession({
            accessToken: 'signed.jwt.token',
            tokenType: 'Bearer',
            expiresAt: '2099-01-01T00:00:00Z',
            username: 'curator',
        })

        expect(getAdminAuthorization()).toBe('Bearer signed.jwt.token')
        expect(getAdminUsername()).toBe('curator')
        expect(sessionStorage.getItem('ethnowear.admin.session')).not.toContain('password')
    })

    it('removes an expired session before returning it', () => {
        vi.useFakeTimers()
        vi.setSystemTime(new Date('2030-01-01T00:00:00Z'))
        setAdminSession({
            accessToken: 'expired-token',
            tokenType: 'Bearer',
            expiresAt: '2029-12-31T23:59:59Z',
            username: 'curator',
        })

        expect(getAdminSession()).toBeNull()
        expect(sessionStorage.getItem('ethnowear.admin.session')).toBeNull()
    })
})

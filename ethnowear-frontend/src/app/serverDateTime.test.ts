import { describe, expect, it } from 'vitest'
import { isFutureServerDateTime, parseServerDateTime } from './serverDateTime'

describe('serverDateTime', () => {
    it('treats timezone-free backend LocalDateTime values as UTC', () => {
        expect(parseServerDateTime('2026-08-20T12:00:00').toISOString()).toBe('2026-08-20T12:00:00.000Z')
    })

    it('only reports a lock whose expiration is after the current instant', () => {
        const now = Date.parse('2026-08-20T12:00:00Z')
        expect(isFutureServerDateTime(null, now)).toBe(false)
        expect(isFutureServerDateTime('2026-08-20T11:59:59', now)).toBe(false)
        expect(isFutureServerDateTime('2026-08-20T12:00:01', now)).toBe(true)
    })
})

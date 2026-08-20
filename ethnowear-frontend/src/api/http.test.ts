import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './http'

describe('apiRequest error messages', () => {
    afterEach(() => vi.restoreAllMocks())

    it('uses an API-provided message for a 405 response', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(JSON.stringify({
            status: 405,
            message: 'This operation is not available for the selected record.',
        }), { status: 405, headers: { 'Content-Type': 'application/json' } }))

        await expect(apiRequest('/api/example', { method: 'POST' })).rejects.toMatchObject({
            status: 405,
            message: 'This operation is not available for the selected record.',
        })
    })

    it('keeps the existing status message when the API provides no message', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(JSON.stringify({
            status: 405,
            error: 'Method Not Allowed',
        }), { status: 405, headers: { 'Content-Type': 'application/json' } }))

        await expect(apiRequest('/api/example', { method: 'POST' })).rejects.toMatchObject({
            status: 405,
            message: 'Request failed with status 405',
        })
    })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'

const fetchEventSourceMock = vi.hoisted(() => vi.fn())
vi.mock('@microsoft/fetch-event-source', () => ({ fetchEventSource: fetchEventSourceMock }))

import { connectManagementEvents, ManagementEventsHttpError } from './ManagementEventsApi'

describe('ManagementEventsApi', () => {
    beforeEach(() => fetchEventSourceMock.mockReset().mockResolvedValue(undefined))

    it('sends Bearer authorization and the reconnect event ID', () => {
        connectManagementEvents({
            authorization: 'Bearer signed-token', lastEventId: 41, signal: new AbortController().signal,
            onManagementEvent: vi.fn(), onResyncRequired: vi.fn(),
        })

        expect(fetchEventSourceMock).toHaveBeenCalledWith('/api/admin/events', expect.objectContaining({
            headers: expect.objectContaining({ Authorization: 'Bearer signed-token', Accept: 'text/event-stream', 'Last-Event-ID': '41' }),
        }))
    })

    it('parses supported events and ignores comments', () => {
        const onManagementEvent = vi.fn()
        const onResyncRequired = vi.fn()
        connectManagementEvents({ authorization: 'Bearer token', lastEventId: null, signal: new AbortController().signal, onManagementEvent, onResyncRequired })
        const options = fetchEventSourceMock.mock.calls[0][1]

        options.onmessage({ event: '', data: '', id: '', retry: undefined })
        options.onmessage({ event: 'management-event', data: JSON.stringify({ eventId: 42, resourceType: 'DOCUMENT_PAGE', action: 'UPDATED', resourceId: 9, documentId: 7, pageId: 9, state: 'COMPLETED', occurredAt: '2026-08-23T10:00:00Z' }), id: '42' })
        options.onmessage({ event: 'resync-required', data: JSON.stringify({ latestEventId: 44, message: 'Refresh authoritative API data' }), id: '44' })

        expect(onManagementEvent).toHaveBeenCalledWith(expect.objectContaining({ eventId: 42, pageId: 9 }))
        expect(onResyncRequired).toHaveBeenCalledWith({ latestEventId: 44, message: 'Refresh authoritative API data' })
    })

    it('exposes 401 responses to the session lifecycle', async () => {
        connectManagementEvents({ authorization: 'Bearer token', lastEventId: null, signal: new AbortController().signal, onManagementEvent: vi.fn(), onResyncRequired: vi.fn() })
        const options = fetchEventSourceMock.mock.calls[0][1]

        await expect(options.onopen(new Response(null, { status: 401 }))).rejects.toEqual(new ManagementEventsHttpError(401))
    })
})

import { act, render, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const connectMock = vi.hoisted(() => vi.fn())
vi.mock('../api/ManagementEventsApi', async importOriginal => ({
    ...await importOriginal<typeof import('../api/ManagementEventsApi')>(),
    connectManagementEvents: connectMock,
}))

import { ManagementEventsHttpError, type ManagementEvent } from '../api/ManagementEventsApi'
import { clearAdminSession, getAdminSession, setAdminSession } from './adminAuthStore'
import { ManagementEventsProvider } from './ManagementEventsProvider'
import { createManagementEventConsumer } from './managementEventInvalidation'

const pageEvent: ManagementEvent = {
    eventId: 42, resourceType: 'DOCUMENT_PAGE', action: 'UPDATED', resourceId: 9,
    documentId: 7, pageId: 9, state: 'COMPLETED', occurredAt: '2026-08-23T10:00:00Z',
}

describe('management event consumption', () => {
    it('invalidates targeted page query families and suppresses duplicates', async () => {
        const queryClient = new QueryClient()
        const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined)
        const lastEventId = { current: null as number | null }
        const consumer = createManagementEventConsumer(queryClient, lastEventId)

        await consumer.handle(pageEvent)
        await consumer.handle(pageEvent)

        expect(lastEventId.current).toBe(42)
        expect(invalidate).toHaveBeenCalledTimes(7)
        expect(invalidate).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ['admin', 'documents', 'detail', 7, 'page', 9] }))
        expect(invalidate).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ['admin', 'documents', 'detail', 7, 'indexing'] }))
        expect(invalidate).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ['admin', 'documents', 'detail', 7, 'chunk-eligibility'] }))
    })

    it.each([
        ['DOCUMENT', 3],
        ['PROCESSING_JOB', 6],
        ['MEDIA_ASSET', 3],
        ['INDEXING', 3],
    ] as const)('invalidates REST queries for %s events', async (resourceType, expectedCalls) => {
        const queryClient = new QueryClient()
        const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined)
        const consumer = createManagementEventConsumer(queryClient, { current: null })
        await consumer.handle({ ...pageEvent, resourceType, resourceId: 7 })
        expect(invalidate).toHaveBeenCalledTimes(expectedCalls)
    })

    it('invalidates page details, workflow, suggestions, and job history after a processing event', async () => {
        const queryClient = new QueryClient()
        const pageRoot = ['admin', 'documents', 'detail', 7, 'page', 9] as const
        const keys = [
            pageRoot,
            [...pageRoot, 'workflow'],
            [...pageRoot, 'text-suggestion'],
            ['admin', 'processing', 'jobs', { documentId: 7, documentPageId: 9 }],
        ] as const
        keys.forEach(key => queryClient.setQueryData(key, { value: true }))

        await createManagementEventConsumer(queryClient, { current: null }).handle({
            ...pageEvent,
            resourceType: 'PROCESSING_JOB',
        })

        keys.forEach(key => expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true))
    })

    it('resyncs all document management queries and active views', async () => {
        const queryClient = new QueryClient()
        const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined)
        const refetch = vi.spyOn(queryClient, 'refetchQueries').mockResolvedValue(undefined)
        const lastEventId = { current: 42 as number | null }

        await createManagementEventConsumer(queryClient, lastEventId).resync({ latestEventId: 50, message: 'Refresh' })

        expect(lastEventId.current).toBe(50)
        expect(invalidate).toHaveBeenCalledWith({ queryKey: ['admin', 'documents'], refetchType: 'none' })
        expect(invalidate).toHaveBeenCalledWith({ queryKey: ['admin', 'processing'], refetchType: 'none' })
        expect(refetch).toHaveBeenCalledWith({ queryKey: ['admin', 'documents'], type: 'active' })
        expect(refetch).toHaveBeenCalledWith({ queryKey: ['admin', 'processing'], type: 'active' })
    })
})

describe('ManagementEventsProvider lifecycle', () => {
    beforeEach(() => {
        clearAdminSession()
        sessionStorage.clear()
        connectMock.mockReset()
    })

    function renderProvider() {
        const client = new QueryClient()
        return render(<QueryClientProvider client={client}><ManagementEventsProvider><div>admin</div></ManagementEventsProvider></QueryClientProvider>)
    }

    it('closes the connection on logout', async () => {
        let streamSignal: AbortSignal | undefined
        connectMock.mockImplementation(({ signal }: { signal: AbortSignal }) => {
            streamSignal = signal
            return new Promise<void>(resolve => signal.addEventListener('abort', () => resolve(), { once: true }))
        })
        setAdminSession({ accessToken: 'token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', username: 'curator' })
        renderProvider()
        await waitFor(() => expect(connectMock).toHaveBeenCalled())

        act(() => clearAdminSession())

        await waitFor(() => expect(streamSignal?.aborted).toBe(true))
    })

    it('reconnects with the latest processed event ID', async () => {
        vi.useFakeTimers()
        let connection = 0
        connectMock.mockImplementation((options: { signal: AbortSignal; lastEventId: number | null; onManagementEvent: (event: ManagementEvent) => void }) => {
            connection += 1
            if (connection === 1) {
                options.onManagementEvent(pageEvent)
                return Promise.reject(new Error('Disconnected'))
            }
            return new Promise<void>(resolve => options.signal.addEventListener('abort', () => resolve(), { once: true }))
        })
        setAdminSession({ accessToken: 'token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', username: 'curator' })
        const view = renderProvider()

        await act(async () => { await vi.advanceTimersByTimeAsync(3_000) })

        expect(connectMock).toHaveBeenCalledTimes(2)
        expect(connectMock.mock.calls[1][0].lastEventId).toBe(42)
        view.unmount()
        vi.useRealTimers()
    })

    it('clears the session after a stream 401', async () => {
        connectMock.mockRejectedValue(new ManagementEventsHttpError(401))
        setAdminSession({ accessToken: 'token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', username: 'curator' })
        renderProvider()

        await waitFor(() => expect(getAdminSession()).toBeNull())
    })
})

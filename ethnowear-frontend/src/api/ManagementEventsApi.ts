import { fetchEventSource, type EventSourceMessage } from '@microsoft/fetch-event-source'
import { apiUrl } from './http'

export type ManagementResourceType = 'DOCUMENT' | 'DOCUMENT_PAGE' | 'PROCESSING_JOB' | 'MEDIA_ASSET' | 'INDEXING'
export type ManagementAction = 'CREATED' | 'UPDATED' | 'STATUS_CHANGED' | 'DELETED'

export type ManagementEvent = {
    eventId: number
    resourceType: ManagementResourceType
    action: ManagementAction
    resourceId: number
    documentId: number | null
    pageId: number | null
    state: string | null
    occurredAt: string
}

export type ManagementResyncRequired = {
    latestEventId: number
    message: string
}

export class ManagementEventsHttpError extends Error {
    readonly status: number

    constructor(status: number) {
        super(`Management event stream failed with status ${status}`)
        this.status = status
    }
}

export class ManagementEventsDisconnectedError extends Error {}

type Options = {
    authorization: string
    lastEventId: number | null
    signal: AbortSignal
    onConnected?: () => void
    onManagementEvent: (event: ManagementEvent) => void
    onResyncRequired: (event: ManagementResyncRequired) => void
}

export function connectManagementEvents(options: Options) {
    const headers: Record<string, string> = {
        Accept: 'text/event-stream',
        Authorization: options.authorization,
    }
    if (options.lastEventId !== null) headers['Last-Event-ID'] = String(options.lastEventId)

    return fetchEventSource(apiUrl('/api/admin/events'), {
        method: 'GET',
        headers,
        signal: options.signal,
        openWhenHidden: true,
        async onopen(response) {
            if (!response.ok) throw new ManagementEventsHttpError(response.status)
            options.onConnected?.()
        },
        onmessage(message) {
            parseMessage(message, options)
        },
        onclose() {
            throw new ManagementEventsDisconnectedError()
        },
        onerror(error) {
            throw error
        },
    })
}

function parseMessage(message: EventSourceMessage, options: Pick<Options, 'onManagementEvent' | 'onResyncRequired'>) {
    if (!message.data) return
    try {
        if (message.event === 'management-event') options.onManagementEvent(JSON.parse(message.data) as ManagementEvent)
        if (message.event === 'resync-required') options.onResyncRequired(JSON.parse(message.data) as ManagementResyncRequired)
    } catch {
        // A malformed notification is ignored; REST remains authoritative.
    }
}

import { apiUrl } from './http'

export type ConversationStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export type ConversationStage = 'RECEIVED' | 'RESOLVING_ENTITIES' | 'READING_ONTOLOGY' | 'SEARCHING_ARCHIVE' | 'RETRIEVING_SOURCES' | 'REASONING' | 'INTERPRETING' | 'GENERATING_ANSWER' | 'VALIDATING_ANSWER'

export type ConversationSummary = {
    conversationId: string
    title: string
    language: 'bg' | 'en'
    createdAt: string
    updatedAt: string
}

export type ConversationSource = { citationId: string; sourceId: number | null; title: string; author: string | null }
export type ConversationEntityCard = { entityType: string; localName: string; label: string; representativeMediaAssetId: number | null }
export type ConversationArchiveCard = { archiveItemId: number; title: string; representativeMediaAssetId: number | null }
export type ConversationArchiveTarget = 'REGIONAL_EMBROIDERY' | 'REGIONAL_MOTIF' | 'MOTIF' | 'TECHNIQUE' | 'ORNAMENT'
export type ConversationAction = {
    type: 'OPEN_ARCHIVE_FILTER'
    label: string
    target: ConversationArchiveTarget
    filters: { categoryLocalNames: string[]; entityLocalNames: string[]; regionLocalNames: string[] }
}
export type ConversationMedia = {
    mediaAssetId: number
    mediaType: string
    caption: string | null
    contentUrl: string
    archiveItemId: number | null
    entityType: string | null
    entityLocalName: string | null
}
export type ConversationAnswer = {
    conversationId: string
    turnId: string
    answer: string
    insufficientEvidence: boolean
    sources: ConversationSource[]
    entityCards: ConversationEntityCard[]
    archiveCards: ConversationArchiveCard[]
    media: ConversationMedia[]
    actions?: ConversationAction[]
    warningCodes: string[]
}
export type ConversationTurn = {
    conversationId: string
    turnId: string
    turnSequence: number
    userMessage: string
    status: ConversationStatus
    stage: ConversationStage
    answer: ConversationAnswer | null
    errorCode: string | null
    lastEventId: number
    createdAt: string
    startedAt: string | null
    finishedAt: string | null
}
export type ConversationProgress = {
    eventId: number
    conversationId: string
    turnId: string
    status: ConversationStatus
    stage: ConversationStage
    errorCode: string | null
    occurredAt: string
}
export type ConversationAvailability = { available: boolean; ollamaAvailable: boolean; ragAvailable: boolean; unavailableCodes: string[] }
export type Page<T> = { content: T[]; totalElements: number; totalPages: number; number: number; last: boolean }

export class ConversationApiError extends Error {
    status: number
    code: string
    fields: Record<string, string>
    constructor(status: number, code: string, fields: Record<string, string> = {}) {
        super(code)
        this.name = 'ConversationApiError'
        this.status = status
        this.code = code
        this.fields = fields
    }
}

async function readError(response: Response) {
    const details = await response.json().catch(() => ({})) as { code?: string; fields?: Record<string, string> }
    return new ConversationApiError(response.status, details.code ?? 'CONVERSATION_PROCESSING_FAILED', details.fields ?? {})
}

async function csrf() {
    const response = await fetch(apiUrl('/api/public/auth/csrf'), { credentials: 'include', cache: 'no-store' })
    if (!response.ok) throw await readError(response)
    const value = await response.json() as { headerName: string; token: string }
    if (!value.headerName || !value.token) throw new ConversationApiError(503, 'CONVERSATION_PROCESSING_FAILED')
    return value
}

async function get<T>(path: string): Promise<T> {
    const response = await fetch(apiUrl(path), { credentials: 'include', cache: 'no-store', headers: { Accept: 'application/json' } })
    if (!response.ok) throw await readError(response)
    return response.json() as Promise<T>
}

async function post<T>(path: string, body?: object, retryNetworkFailure = true): Promise<T> {
    return mutate<T>('POST', path, body, retryNetworkFailure)
}

async function mutate<T>(method: 'POST' | 'PATCH' | 'DELETE', path: string, body?: object, retryNetworkFailure = true): Promise<T> {
    const token = await csrf()
    const headers = new Headers({ Accept: 'application/json', [token.headerName]: token.token })
    if (body) headers.set('Content-Type', 'application/json')
    let response: Response
    try {
        response = await fetch(apiUrl(path), {
            method, credentials: 'include', cache: 'no-store', headers,
            ...(body ? { body: JSON.stringify(body) } : {}),
        })
    } catch (error) {
        if (retryNetworkFailure && error instanceof TypeError) return mutate<T>(method, path, body, false)
        throw error
    }
    if (!response.ok) throw await readError(response)
    return response.status === 204 ? undefined as T : response.json() as Promise<T>
}

export const conversationApi = {
    availability: () => get<ConversationAvailability>('/api/conversations/availability'),
    initializeGuest: () => post<void>('/api/conversations/guest-session'),
    list: (page = 0) => get<Page<ConversationSummary>>(`/api/conversations?page=${page}&size=20`),
    get: (id: string) => get<ConversationSummary>(`/api/conversations/${id}`),
    rename: (id: string, title: string) => mutate<ConversationSummary>('PATCH', `/api/conversations/${id}`, { title }),
    remove: (id: string) => mutate<void>('DELETE', `/api/conversations/${id}`),
    create: (clientRequestId: string, language: 'bg' | 'en') => post<ConversationSummary>('/api/conversations', { clientRequestId, language }),
    listTurns: (id: string, page = 0) => get<Page<ConversationTurn>>(`/api/conversations/${id}/turns?page=${page}&size=20`),
    getTurn: (conversationId: string, turnId: string) => get<ConversationTurn>(`/api/conversations/${conversationId}/turns/${turnId}`),
    submit: (conversationId: string, clientRequestId: string, text: string) => post<{ conversationId: string; turnId: string; status: ConversationStatus }>(`/api/conversations/${conversationId}/turns`, { clientRequestId, text }),
    cancel: (conversationId: string, turnId: string) => post<ConversationTurn>(`/api/conversations/${conversationId}/turns/${turnId}/cancel`),
    events: (conversationId: string, turnId: string, afterEventId: number) => get<ConversationProgress[]>(`/api/conversations/${conversationId}/turns/${turnId}/events?afterEventId=${afterEventId}`),
    streamUrl: (conversationId: string, turnId: string) => apiUrl(`/api/conversations/${conversationId}/turns/${turnId}/stream`),
}

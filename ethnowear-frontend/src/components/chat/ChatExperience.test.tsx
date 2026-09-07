import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router'
import i18n from '../../app/i18n'
import { ChatPage, QuickChat, shouldShowEvidenceWarning } from './ChatExperience'
import { resetChatForTests } from './chatState'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, last: true }
class FakeEventSource {
 static instances: FakeEventSource[] = []
 listeners = new Map<string, (event: MessageEvent) => void>(); onerror: (() => void) | null = null; withCredentials: boolean
 url: string
 constructor(url: string, options?: EventSourceInit) { this.url = url; this.withCredentials = Boolean(options?.withCredentials); FakeEventSource.instances.push(this) }
 addEventListener(type: string, listener: EventListenerOrEventListenerObject) { this.listeners.set(type, listener as (event: MessageEvent) => void) }
 emit(value: unknown) { this.listeners.get('conversation-progress')?.({ data: JSON.stringify(value) } as MessageEvent) }
 close() {}
}
function defaultFetch() {
 return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, options) => {
  const url = String(input); const method = options?.method ?? 'GET'
  if (url === '/api/conversations/availability') return json({ available: true, ollamaAvailable: true, ragAvailable: true, unavailableCodes: [] })
  if (url === '/api/public/auth/csrf') return json({ headerName: 'X-PUBLIC-CSRF', token: 'csrf' })
  if (url === '/api/conversations/guest-session' && method === 'POST') return new Response(null, { status: 204 })
  if (url === '/api/conversations?page=0&size=20') return json(emptyPage)
  if (url.includes('/events?')) return json([])
  throw new Error(`Unhandled ${method} ${url}`)
 })
}
beforeEach(async () => { resetChatForTests(); FakeEventSource.instances = []; vi.stubGlobal('EventSource', FakeEventSource); await i18n.changeLanguage('en') })

describe('conversation chat', () => {
 it('does not duplicate the insufficient-evidence message when no evidence was found', () => {
  expect(shouldShowEvidenceWarning({
   conversationId: 'c1', turnId: 't1', answer: 'There is not enough verified evidence to answer this question.',
   insufficientEvidence: true, sources: [], entityCards: [], archiveCards: [], media: [], warningCodes: [], actions: []
  })).toBe(false)
 })
 it('disables chat and identifies unavailable Ollama and RAG dependencies', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(json({ available: false, ollamaAvailable: false, ragAvailable: false, unavailableCodes: ['CONVERSATION_OLLAMA_UNAVAILABLE', 'CONVERSATION_RAG_UNAVAILABLE'] }))
  render(<MemoryRouter><ChatPage /></MemoryRouter>)
  expect(await screen.findByText('Chat unavailable')).toBeInTheDocument()
  expect(screen.getByText(/Ollama.*unavailable/)).toBeInTheDocument()
  expect(screen.getByText(/RAG.*unavailable/)).toBeInTheDocument()
  expect(screen.getByRole('textbox')).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Add message' })).toBeDisabled()
 })
 it('initializes a guest session and preserves the existing welcome design', async () => {
  const fetchMock = defaultFetch(); render(<MemoryRouter><ChatPage /></MemoryRouter>)
  expect(await screen.findByRole('button', { name: /What makes Shopluk/ })).toBeInTheDocument()
  expect(fetchMock).toHaveBeenCalledWith('/api/conversations/guest-session', expect.objectContaining({ method: 'POST', credentials: 'include' }))
 })
 it('creates a conversation, submits once on rapid clicks, and shows one stable assistant placeholder', async () => {
  const fetchMock = defaultFetch(); fetchMock.mockImplementation(async (input, options) => {
   const url = String(input); const method = options?.method ?? 'GET'
   if (url === '/api/public/auth/csrf') return json({ headerName: 'X-PUBLIC-CSRF', token: 'csrf' })
   if (url === '/api/conversations/guest-session') return new Response(null, { status: 204 })
   if (url === '/api/conversations?page=0&size=20') return json(emptyPage)
   if (url === '/api/conversations' && method === 'POST') return json({ conversationId: 'c1', title: 'Question', language: 'en', createdAt: '', updatedAt: '' }, 201)
   if (url === '/api/conversations/c1/turns' && method === 'POST') return json({ conversationId: 'c1', turnId: 't1', status: 'QUEUED' }, 202)
   if (url.includes('/events?')) return json([])
   throw new Error(`Unhandled ${method} ${url}`)
  })
  render(<MemoryRouter><ChatPage /></MemoryRouter>); const box = await screen.findByRole('textbox')
  fireEvent.change(box, { target: { value: 'What is chain stitch?' } }); const send = screen.getByRole('button', { name: 'Add message' }); fireEvent.click(send); fireEvent.click(send)
  expect(await screen.findByText('What is chain stitch?')).toBeInTheDocument(); expect(screen.getAllByText('Preparing the answer')).toHaveLength(1)
  await waitFor(() => expect(fetchMock.mock.calls.filter(([url, options]) => String(url) === '/api/conversations/c1/turns' && options?.method === 'POST')).toHaveLength(1))
  expect(FakeEventSource.instances[0].withCredentials).toBe(true)
 })
 it('replaces progress with the final answer, citations, canonical links and approved media', async () => {
  const fetchMock = defaultFetch(); fetchMock.mockImplementation(async (input, options) => {
   const url = String(input); const method = options?.method ?? 'GET'
   if (url === '/api/public/auth/csrf') return json({ headerName: 'X-PUBLIC-CSRF', token: 'csrf' })
   if (url === '/api/conversations/guest-session') return new Response(null, { status: 204 })
   if (url === '/api/conversations?page=0&size=20') return json(emptyPage)
   if (url === '/api/conversations' && method === 'POST') return json({ conversationId: 'c1', title: 'Chain stitch', language: 'en', createdAt: '', updatedAt: '' }, 201)
   if (url === '/api/conversations/c1/turns' && method === 'POST') return json({ conversationId: 'c1', turnId: 't1', status: 'QUEUED' }, 202)
   if (url.endsWith('/turns/t1')) return json({ conversationId: 'c1', turnId: 't1', turnSequence: 1, userMessage: 'Question', status: 'COMPLETED', stage: 'VALIDATING_ANSWER', errorCode: null, lastEventId: 2, createdAt: '2026-09-02T10:00:00Z', startedAt: '2026-09-02T10:00:01Z', finishedAt: '2026-09-02T10:00:03.500Z', answer: { conversationId: 'c1', turnId: 't1', answer: 'Grounded answer', insufficientEvidence: true, sources: [{ citationId: 'chunk:12', sourceId: 5, title: 'Book', author: 'Author' }], entityCards: [{ entityType: 'TECHNIQUE', localName: 'ChainTechnique', label: 'Chain stitch', representativeMediaAssetId: 51 }], archiveCards: [{ archiveItemId: 7, title: 'Archive example', representativeMediaAssetId: null }], media: [{ mediaAssetId: 52, mediaType: 'IMAGE', caption: 'Approved image', contentUrl: '/api/media/52/content', archiveItemId: 7, entityType: null, entityLocalName: null }], warningCodes: [], actions: [{ type: 'OPEN_ARCHIVE_FILTER', label: 'Show bird ornaments', target: 'ORNAMENT', filters: { categoryLocalNames: ['BirdOrnament'], entityLocalNames: [], regionLocalNames: [] } }] } })
   if (url.includes('/events?')) return json([])
   throw new Error(`Unhandled ${method} ${url}`)
  })
  render(<MemoryRouter><ChatPage /></MemoryRouter>); fireEvent.change(await screen.findByRole('textbox'), { target: { value: 'Question' } }); fireEvent.click(screen.getByRole('button', { name: 'Add message' }))
  await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1)); FakeEventSource.instances[0].emit({ eventId: 1, conversationId: 'c1', turnId: 't1', status: 'RUNNING', stage: 'READING_ONTOLOGY', errorCode: null, occurredAt: '' })
  expect(await screen.findByText('Reading the ontology')).toBeInTheDocument()
  FakeEventSource.instances[0].emit({ eventId: 2, conversationId: 'c1', turnId: 't1', status: 'COMPLETED', stage: 'VALIDATING_ANSWER', errorCode: null, occurredAt: '' })
  expect(await screen.findByText('Grounded answer')).toBeInTheDocument(); expect(screen.getByText('· answered in 2.5 s')).toBeInTheDocument(); expect(screen.getByText('There is not enough verified evidence for a complete answer.')).toBeInTheDocument()
  expect(screen.getByText('Book')).toBeInTheDocument(); expect(screen.getByRole('link', { name: /Chain stitch/ })).toHaveAttribute('href', '/archive/techniques/ChainTechnique')
  expect(screen.getByText('Related records to explore. Their inclusion does not mean they support every claim in the answer.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /Archive example/ })).toHaveAttribute('href', '/archive/items/7'); expect(screen.getByAltText('Approved image')).toHaveAttribute('src', '/api/media/52/content')
  expect(screen.getByRole('button', { name: 'Show bird ornaments' })).toBeInTheDocument()
 })
 it('queues and edits the next question before submitting it after completion', async () => {
  const fetchMock = defaultFetch(); let submissions = 0
  fetchMock.mockImplementation(async (input, options) => {
   const url = String(input); const method = options?.method ?? 'GET'
   if (url === '/api/public/auth/csrf') return json({ headerName: 'X-PUBLIC-CSRF', token: 'csrf' })
   if (url === '/api/conversations/guest-session') return new Response(null, { status: 204 })
   if (url === '/api/conversations?page=0&size=20') return json(emptyPage)
   if (url === '/api/conversations' && method === 'POST') return json({ conversationId: 'c1', title: 'First', language: 'en', createdAt: '', updatedAt: '' }, 201)
   if (url === '/api/conversations/c1/turns' && method === 'POST') {
    submissions += 1
    return json({ conversationId: 'c1', turnId: `t${submissions}`, status: 'QUEUED' }, 202)
   }
   if (url.endsWith('/turns/t1')) return json({ conversationId: 'c1', turnId: 't1', turnSequence: 1, userMessage: 'First question', status: 'COMPLETED', stage: 'VALIDATING_ANSWER', errorCode: null, lastEventId: 1, createdAt: '', startedAt: null, finishedAt: '', answer: { conversationId: 'c1', turnId: 't1', answer: 'First answer', insufficientEvidence: false, sources: [], entityCards: [], archiveCards: [], media: [], warningCodes: [], actions: [] } })
   if (url.includes('/events?')) return json([])
   throw new Error(`Unhandled ${method} ${url}`)
  })
  render(<MemoryRouter><ChatPage /></MemoryRouter>)
  const composer = await screen.findByRole('textbox')
  fireEvent.change(composer, { target: { value: 'First question' } }); fireEvent.click(screen.getByRole('button', { name: 'Add message' }))
  await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1))
  fireEvent.change(composer, { target: { value: 'Second question' } }); fireEvent.click(screen.getByRole('button', { name: 'Add to queue' }))
  expect(screen.getByText('Second question')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Edit queued question' }))
  fireEvent.change(screen.getByDisplayValue('Second question'), { target: { value: 'Edited second question' } })
  fireEvent.click(screen.getByRole('button', { name: 'Save' }))
  expect(screen.getByText('Edited second question')).toBeInTheDocument()
  FakeEventSource.instances[0].emit({ eventId: 1, conversationId: 'c1', turnId: 't1', status: 'COMPLETED', stage: 'VALIDATING_ANSWER', errorCode: null, occurredAt: '2026-09-02T10:00:03Z' })
  await waitFor(() => expect(fetchMock.mock.calls.filter(([url, options]) => String(url) === '/api/conversations/c1/turns' && options?.method === 'POST')).toHaveLength(2))
  const secondBody = JSON.parse(String(fetchMock.mock.calls.filter(([url, options]) => String(url) === '/api/conversations/c1/turns' && options?.method === 'POST')[1][1]?.body))
  expect(secondBody.text).toBe('Edited second question')
 })
 it('offers cancellation only while active and localizes a failed turn', async () => {
  const fetchMock = defaultFetch(); fetchMock.mockImplementation(async (input, options) => {
   const url = String(input); const method = options?.method ?? 'GET'
   if (url === '/api/public/auth/csrf') return json({ headerName: 'X-PUBLIC-CSRF', token: 'csrf' })
   if (url === '/api/conversations/guest-session') return new Response(null, { status: 204 })
   if (url === '/api/conversations?page=0&size=20') return json(emptyPage)
   if (url === '/api/conversations' && method === 'POST') return json({ conversationId: 'c1', title: 'Question', language: 'en', createdAt: '', updatedAt: '' }, 201)
   if (url === '/api/conversations/c1/turns' && method === 'POST') return json({ conversationId: 'c1', turnId: 't1', status: 'QUEUED' }, 202)
   if (url.endsWith('/cancel') && method === 'POST') return json({ conversationId: 'c1', turnId: 't1', turnSequence: 1, userMessage: 'Question', status: 'CANCELLED', stage: 'RECEIVED', answer: null, errorCode: null, lastEventId: 1, createdAt: '', startedAt: null, finishedAt: '' })
   throw new Error(`Unhandled ${method} ${url}`)
  })
  render(<MemoryRouter><ChatPage /></MemoryRouter>); fireEvent.change(await screen.findByRole('textbox'), { target: { value: 'Question' } }); fireEvent.click(screen.getByRole('button', { name: 'Add message' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Cancel' })); expect(await screen.findByText('The request was cancelled.')).toBeInTheDocument(); expect(screen.queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument()
 })
 it('uses fallback event recovery after SSE disconnect', async () => {
  const fetchMock = defaultFetch(); fetchMock.mockImplementation(async (input, options) => {
   const url = String(input); const method = options?.method ?? 'GET'
   if (url === '/api/public/auth/csrf') return json({ headerName: 'X-PUBLIC-CSRF', token: 'csrf' })
   if (url === '/api/conversations/guest-session') return new Response(null, { status: 204 })
   if (url === '/api/conversations?page=0&size=20') return json(emptyPage)
   if (url === '/api/conversations' && method === 'POST') return json({ conversationId: 'c1', title: 'Q', language: 'en', createdAt: '', updatedAt: '' }, 201)
   if (url === '/api/conversations/c1/turns' && method === 'POST') return json({ conversationId: 'c1', turnId: 't1', status: 'QUEUED' }, 202)
   if (url.endsWith('events?afterEventId=0')) return json([{ eventId: 4, conversationId: 'c1', turnId: 't1', status: 'RUNNING', stage: 'SEARCHING_ARCHIVE', errorCode: null, occurredAt: '' }])
   throw new Error(`Unhandled ${method} ${url}`)
  })
  render(<MemoryRouter><ChatPage /></MemoryRouter>); fireEvent.change(await screen.findByRole('textbox'), { target: { value: 'Question' } }); fireEvent.click(screen.getByRole('button', { name: 'Add message' }))
  await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1)); FakeEventSource.instances[0].onerror?.(); expect(await screen.findByText('Searching the archive')).toBeInTheDocument()
 })
 it('preserves quick-chat draft when continuing to the full page', async () => {
  defaultFetch(); render(<MemoryRouter initialEntries={['/archive']}><Routes><Route path="/archive" element={<QuickChat />} /><Route path="/chat" element={<ChatPage />} /></Routes></MemoryRouter>)
  fireEvent.click(screen.getByRole('button', { name: 'Open quick chat' })); fireEvent.change(await screen.findByRole('textbox'), { target: { value: 'My embroidery question' } }); fireEvent.click(screen.getAllByRole('button', { name: 'Continue in full chat' })[0])
  await waitFor(() => expect(screen.getByRole('textbox')).toHaveValue('My embroidery question')); expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
 })
 it('renames and deletes a conversation from its options menu', async () => {
  const fetchMock = defaultFetch(); fetchMock.mockImplementation(async (input, options) => {
   const url = String(input); const method = options?.method ?? 'GET'
   if (url === '/api/public/auth/csrf') return json({ headerName: 'X-PUBLIC-CSRF', token: 'csrf' })
   if (url === '/api/conversations/availability') return json({ available: true, ollamaAvailable: true, ragAvailable: true, unavailableCodes: [] })
   if (url === '/api/conversations/guest-session') return new Response(null, { status: 204 })
   if (url === '/api/conversations?page=0&size=20') return json({ ...emptyPage, content: [{ conversationId: 'c1', title: 'Old title', language: 'en', createdAt: '', updatedAt: '' }], totalElements: 1 })
   if (url === '/api/conversations/c1/turns?page=0&size=20') return json(emptyPage)
   if (url === '/api/conversations/c1' && method === 'PATCH') return json({ conversationId: 'c1', title: 'New title', language: 'en', createdAt: '', updatedAt: '' })
   if (url === '/api/conversations/c1' && method === 'DELETE') return new Response(null, { status: 204 })
   throw new Error(`Unhandled ${method} ${url}`)
  })
  render(<MemoryRouter><ChatPage /></MemoryRouter>)
  expect(screen.getByRole('button', { name: 'Show conversations' })).toHaveAttribute('aria-expanded', 'true')
  fireEvent.click(await screen.findByRole('button', { name: 'Options for Old title' }))
  fireEvent.click(screen.getByRole('menuitem', { name: 'Rename' }))
  fireEvent.change(screen.getByLabelText('Title'), { target: { value: 'New title' } })
  fireEvent.click(screen.getByRole('button', { name: 'Save' }))
  expect(await screen.findByText('New title')).toBeInTheDocument()
  expect(fetchMock).toHaveBeenCalledWith('/api/conversations/c1', expect.objectContaining({ method: 'PATCH', credentials: 'include' }))
  await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Rename conversation' })).not.toBeInTheDocument())

  fireEvent.click(screen.getByRole('button', { name: 'Options for New title' }))
  fireEvent.click(screen.getByRole('menuitem', { name: 'Delete' }))
  expect(screen.getByText(/complete history will be permanently deleted/)).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Delete conversation' }))
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/conversations/c1', expect.objectContaining({ method: 'DELETE', credentials: 'include' })))
  await waitFor(() => expect(screen.queryByText('New title')).not.toBeInTheDocument())
 })
 it.each(['/chat', '/management/documents'])('hides floating launcher on %s', path => { defaultFetch(); render(<MemoryRouter initialEntries={[path]}><QuickChat /></MemoryRouter>); expect(screen.queryByRole('button', { name: 'Open quick chat' })).not.toBeInTheDocument() })
})

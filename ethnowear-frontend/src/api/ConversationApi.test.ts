import { beforeEach, describe, expect, it, vi } from 'vitest'
import { conversationApi } from './ConversationApi'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
describe('conversationApi', () => {
 beforeEach(() => vi.restoreAllMocks())
 it('initializes guest ownership with credentials and a fresh CSRF token', async () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(json({ headerName: 'X-PUBLIC-CSRF', token: 'csrf-1' })).mockResolvedValueOnce(new Response(null, { status: 204 }))
  await conversationApi.initializeGuest()
  expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/public/auth/csrf', expect.objectContaining({ credentials: 'include' }))
  expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/conversations/guest-session', expect.objectContaining({ method: 'POST', credentials: 'include', headers: expect.any(Headers) }))
  expect((fetchMock.mock.calls[1][1]!.headers as Headers).get('X-PUBLIC-CSRF')).toBe('csrf-1')
 })
 it('creates a conversation and submits a message with caller-owned idempotency keys', async () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch')
   .mockResolvedValueOnce(json({ headerName: 'X-PUBLIC-CSRF', token: 'a' })).mockResolvedValueOnce(json({ conversationId: 'c1', title: 'Title', language: 'bg' }, 201))
   .mockResolvedValueOnce(json({ headerName: 'X-PUBLIC-CSRF', token: 'b' })).mockResolvedValueOnce(json({ conversationId: 'c1', turnId: 't1', status: 'QUEUED' }, 202))
  await conversationApi.create('request-create', 'bg'); await conversationApi.submit('c1', 'request-turn', 'Question')
  expect(JSON.parse(String(fetchMock.mock.calls[1][1]!.body))).toEqual({ clientRequestId: 'request-create', language: 'bg' })
  expect(JSON.parse(String(fetchMock.mock.calls[3][1]!.body))).toEqual({ clientRequestId: 'request-turn', text: 'Question' })
  expect((fetchMock.mock.calls[3][1]!.headers as Headers).get('X-PUBLIC-CSRF')).toBe('b')
 })
 it('reuses the same request body when retrying a network failure', async () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch')
   .mockResolvedValueOnce(json({ headerName: 'X-PUBLIC-CSRF', token: 'first' })).mockRejectedValueOnce(new TypeError('network'))
   .mockResolvedValueOnce(json({ headerName: 'X-PUBLIC-CSRF', token: 'second' })).mockResolvedValueOnce(json({ conversationId: 'c1', turnId: 't1', status: 'QUEUED' }, 202))
  await conversationApi.submit('c1', 'same-request', 'Same question')
  const posts = fetchMock.mock.calls.filter(([, options]) => options?.method === 'POST')
  expect(posts).toHaveLength(2)
  expect(posts[0][1]?.body).toBe(posts[1][1]?.body)
 })
 it('loads fallback progress after an event id with credentials', async () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(json([]))
  await conversationApi.events('c1', 't1', 7)
  expect(fetchMock).toHaveBeenCalledWith('/api/conversations/c1/turns/t1/events?afterEventId=7', expect.objectContaining({ credentials: 'include' }))
 })
})

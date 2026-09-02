import { useSyncExternalStore } from 'react'
import i18n from '../../app/i18n'
import { ConversationApiError, conversationApi, type ConversationAnswer, type ConversationAvailability, type ConversationProgress, type ConversationStage, type ConversationStatus, type ConversationTurn } from '../../api/ConversationApi'

export type ChatTurn = { id: string; sequence: number; userMessage: string; status: ConversationStatus; stage: ConversationStage; progress: ConversationProgress[]; answer: ConversationAnswer | null; errorCode: string | null; createdAt: string; startedAt: string | null; finishedAt: string | null }
export type ChatQueuedQuestion = { id: string; text: string }
export type ChatConversation = { id: string; title: string; language: 'bg' | 'en'; draft: string; queue: ChatQueuedQuestion[]; turns: ChatTurn[]; loaded: boolean; local: boolean }
type ChatState = { activeId: string; conversations: ChatConversation[]; ready: boolean; loading: boolean; errorCode: string | null; availability: ConversationAvailability | null }
const localId = () => `local:${crypto.randomUUID()}`
const emptyConversation = (): ChatConversation => ({ id: localId(), title: '', language: i18n.language.startsWith('en') ? 'en' : 'bg', draft: '', queue: [], turns: [], loaded: true, local: true })
let initial = emptyConversation()
let state: ChatState = { activeId: initial.id, conversations: [initial], ready: false, loading: false, errorCode: null, availability: null }
let initialization: Promise<void> | null = null
let mutation: Promise<void> | null = null
const streams = new Map<string, EventSource>()
const listeners = new Set<() => void>()
const terminal = new Set<ConversationStatus>(['COMPLETED', 'FAILED', 'CANCELLED'])
function publish(patch?: Partial<ChatState>) { state = patch ? { ...state, ...patch } : { ...state }; listeners.forEach(listener => listener()) }
function replaceConversation(id: string, update: (item: ChatConversation) => ChatConversation) { state.conversations = state.conversations.map(item => item.id === id ? update(item) : item); publish() }
function fromTurn(turn: ConversationTurn): ChatTurn { return { id: turn.turnId, sequence: turn.turnSequence, userMessage: turn.userMessage, status: turn.status, stage: turn.stage, progress: [], answer: turn.answer, errorCode: turn.errorCode, createdAt: turn.createdAt, startedAt: turn.startedAt, finishedAt: turn.finishedAt } }
function activeConversation() { return state.conversations.find(item => item.id === state.activeId) }
function closeStream(turnId: string) { streams.get(turnId)?.close(); streams.delete(turnId) }
async function loadTurns(conversationId: string) {
 const firstPage = await conversationApi.listTurns(conversationId, 0)
 const turns: ConversationTurn[] = [...firstPage.content]
 for (let page = 1; page < firstPage.totalPages && !firstPage.last; page += 1) {
  const result = await conversationApi.listTurns(conversationId, page)
  turns.push(...result.content)
  if (result.last) break
 }
 const mapped = turns.sort((a, b) => a.turnSequence - b.turnSequence).map(fromTurn)
 replaceConversation(conversationId, item => ({ ...item, turns: mapped, loaded: true }))
 mapped.filter(item => !terminal.has(item.status)).forEach(item => watchTurn(conversationId, item.id, 0))
}
export async function initializeChat() {
 if (initialization) return initialization
 initialization = (async () => {
  publish({ loading: true, errorCode: null })
  try {
   const availability = await conversationApi.availability()
   if (!availability.available) { publish({ ready: true, loading: false, availability }); return }
   publish({ availability })
   await conversationApi.initializeGuest()
   const firstPage = await conversationApi.list(0)
   const conversations: ChatConversation[] = firstPage.content.map(item => ({ id: item.conversationId, title: item.title, language: item.language, draft: '', queue: [], turns: [], loaded: false, local: false }))
   for (let page = 1; page < firstPage.totalPages && !firstPage.last; page += 1) {
    const result = await conversationApi.list(page)
    conversations.push(...result.content.map(item => ({ id: item.conversationId, title: item.title, language: item.language, draft: '', queue: [], turns: [], loaded: false, local: false })))
    if (result.last) break
   }
   if (conversations.length) { state.conversations = conversations; state.activeId = conversations[0].id; publish({ ready: true, loading: false }); await loadTurns(conversations[0].id) }
   else { const fresh = emptyConversation(); state.conversations = [fresh]; state.activeId = fresh.id; publish({ ready: true, loading: false }) }
  } catch (error) { publish({ ready: true, loading: false, errorCode: error instanceof ConversationApiError ? error.code : 'CONVERSATION_PROCESSING_FAILED' }) }
 })().finally(() => { initialization = null })
 return initialization
}
export function useChatDesign() { return useSyncExternalStore(listener => { listeners.add(listener); return () => { listeners.delete(listener) } }, () => state) }
export async function selectConversation(id: string) { state.activeId = id; publish(); const item = activeConversation(); if (item && !item.local && !item.loaded) await loadTurns(item.id).catch(setError) }
export function newConversation() { const item = emptyConversation(); state.conversations = [item, ...state.conversations]; state.activeId = item.id; publish({ errorCode: null }) }
export async function renameConversation(id: string, title: string) {
 const normalized = title.trim()
 if (!normalized || normalized.length > 300 || mutation) return false
 const item = state.conversations.find(value => value.id === id)
 if (!item) return false
 if (item.local) { replaceConversation(id, value => ({ ...value, title: normalized })); return true }
 let succeeded = false
 mutation = conversationApi.rename(id, normalized).then(result => {
  replaceConversation(id, value => ({ ...value, title: result.title })); succeeded = true
 }).catch(setError).finally(() => { mutation = null; publish() })
 await mutation
 return succeeded
}
export async function deleteConversation(id: string) {
 if (mutation) return false
 const item = state.conversations.find(value => value.id === id)
 if (!item || item.turns.some(turn => !terminal.has(turn.status))) return false
 let succeeded = item.local
 if (!item.local) {
  mutation = conversationApi.remove(id).then(() => { succeeded = true }).catch(setError).finally(() => { mutation = null; publish() })
  await mutation
 }
 if (!succeeded) return false
 item.turns.forEach(turn => closeStream(turn.id))
 state.conversations = state.conversations.filter(value => value.id !== id)
 if (!state.conversations.length) state.conversations = [emptyConversation()]
 if (state.activeId === id) state.activeId = state.conversations[0].id
 publish({ errorCode: null })
 return true
}
export function updateDraft(draft: string) { replaceConversation(state.activeId, item => ({ ...item, draft })) }
export function editQueuedQuestion(id: string, text: string) {
 const normalized = text.trim()
 if (!normalized) return false
 replaceConversation(state.activeId, item => ({ ...item, queue: item.queue.map(question => question.id === id ? { ...question, text: normalized } : question) }))
 return true
}
export function removeQueuedQuestion(id: string) { replaceConversation(state.activeId, item => ({ ...item, queue: item.queue.filter(question => question.id !== id) })) }
function setError(error: unknown) { publish({ errorCode: error instanceof ConversationApiError ? (error.status === 400 && error.fields.text ? 'CONVERSATION_VALIDATION' : error.code) : 'CONVERSATION_PROCESSING_FAILED' }) }
async function ensureRemoteConversation(item: ChatConversation) {
 if (!item.local) return item
 const created = await conversationApi.create(crypto.randomUUID(), item.language)
 const remote = { ...item, id: created.conversationId, title: created.title, local: false }
 state.conversations = state.conversations.map(value => value.id === item.id ? remote : value); state.activeId = remote.id; publish(); return remote
}
export function submitDraft() {
 const item = activeConversation()
 if (!item?.draft.trim()) return
 const text = item.draft.trim()
 if (item.turns.some(turn => !terminal.has(turn.status)) || mutation) {
  replaceConversation(item.id, value => ({ ...value, draft: '', queue: [...value.queue, { id: localId(), text }] }))
  return
 }
 startQuestion(item, text)
}
function startQuestion(item: ChatConversation, text: string, queuedId?: string) {
 if (mutation) return
 const requestId = crypto.randomUUID(); let remoteId: string | null = null; let optimisticId: string | null = null
 mutation = (async () => {
  try {
   const conversation = await ensureRemoteConversation(item); remoteId = conversation.id; optimisticId = `pending:${requestId}`
   const createdAt = new Date().toISOString()
   const optimistic: ChatTurn = { id: optimisticId, sequence: (conversation.turns.at(-1)?.sequence ?? 0) + 1, userMessage: text, status: 'QUEUED', stage: 'RECEIVED', progress: [], answer: null, errorCode: null, createdAt, startedAt: null, finishedAt: null }
   replaceConversation(conversation.id, value => ({ ...value, draft: '', queue: queuedId ? value.queue.filter(question => question.id !== queuedId) : value.queue, turns: [...value.turns, optimistic] }))
   const accepted = await conversationApi.submit(conversation.id, requestId, text)
   replaceConversation(conversation.id, value => ({ ...value, turns: value.turns.map(turn => turn.id === optimisticId ? { ...turn, id: accepted.turnId, status: accepted.status } : turn) }))
   watchTurn(conversation.id, accepted.turnId, 0)
  } catch (error) {
   if (remoteId && optimisticId) replaceConversation(remoteId, value => ({ ...value, turns: value.turns.map(turn => turn.id === optimisticId ? { ...turn, status: 'FAILED', errorCode: error instanceof ConversationApiError ? error.code : 'CONVERSATION_PROCESSING_FAILED' } : turn) }))
   setError(error)
  }
 })().finally(() => { mutation = null; publish() })
}
function drainQueue(conversationId: string) {
 queueMicrotask(() => {
  const item = state.conversations.find(value => value.id === conversationId)
  const next = item?.queue[0]
  if (!item || !next || mutation || item.turns.some(turn => !terminal.has(turn.status))) return
  startQuestion(item, next.text, next.id)
 })
}
function applyProgress(conversationId: string, turnId: string, event: ConversationProgress) {
 replaceConversation(conversationId, item => ({ ...item, turns: item.turns.map(turn => turn.id !== turnId ? turn : { ...turn, status: event.status, stage: event.stage, errorCode: event.errorCode, startedAt: turn.startedAt ?? (event.status === 'RUNNING' ? event.occurredAt : null), finishedAt: terminal.has(event.status) ? event.occurredAt : turn.finishedAt, progress: turn.progress.some(value => value.eventId === event.eventId) ? turn.progress : [...turn.progress, event].sort((a, b) => a.eventId - b.eventId) }) }))
 if (terminal.has(event.status)) { closeStream(turnId); if (event.status === 'COMPLETED') void refreshTurn(conversationId, turnId); drainQueue(conversationId) }
}
async function refreshTurn(conversationId: string, turnId: string) { try { const result = await conversationApi.getTurn(conversationId, turnId); replaceConversation(conversationId, item => ({ ...item, turns: item.turns.map(turn => turn.id === turnId ? { ...fromTurn(result), progress: turn.progress } : turn) })) } catch (error) { setError(error) } }
export function watchTurn(conversationId: string, turnId: string, afterEventId: number) {
 if (streams.has(turnId) || typeof EventSource === 'undefined') return
 const source = new EventSource(conversationApi.streamUrl(conversationId, turnId), { withCredentials: true }); streams.set(turnId, source)
 source.addEventListener('conversation-progress', raw => { try { applyProgress(conversationId, turnId, JSON.parse((raw as MessageEvent).data) as ConversationProgress) } catch { setError(new ConversationApiError(500, 'CONVERSATION_PROCESSING_FAILED')) } })
 source.onerror = () => { const item = state.conversations.find(value => value.id === conversationId)?.turns.find(value => value.id === turnId); const last = item?.progress.at(-1)?.eventId ?? afterEventId; void conversationApi.events(conversationId, turnId, last).then(events => events.forEach(event => applyProgress(conversationId, turnId, event))).catch(() => {}) }
}
export async function cancelActiveTurn() {
 const item = activeConversation(); const turn = item?.turns.find(value => value.status === 'QUEUED' || value.status === 'RUNNING')
 if (!item || !turn || mutation) return
 mutation = conversationApi.cancel(item.id, turn.id).then(result => { closeStream(turn.id); replaceConversation(item.id, value => ({ ...value, turns: value.turns.map(current => current.id === turn.id ? { ...fromTurn(result), progress: current.progress } : current) })) }).catch(setError).finally(() => { mutation = null; publish(); drainQueue(item.id) }); await mutation
}
export function retryTurn(turnId: string) { const item = activeConversation(); const turn = item?.turns.find(value => value.id === turnId); if (!item || !turn || turn.status !== 'FAILED') return; replaceConversation(item.id, value => ({ ...value, draft: turn.userMessage })); queueMicrotask(submitDraft) }
export function changeChatOwner() { streams.forEach(source => source.close()); streams.clear(); initialization = null; mutation = null; initial = emptyConversation(); state = { activeId: initial.id, conversations: [initial], ready: false, loading: false, errorCode: null, availability: null }; publish(); void initializeChat() }
export function resetChatForTests() { streams.forEach(source => source.close()); streams.clear(); initialization = null; mutation = null; initial = emptyConversation(); state = { activeId: initial.id, conversations: [initial], ready: false, loading: false, errorCode: null, availability: null }; publish() }

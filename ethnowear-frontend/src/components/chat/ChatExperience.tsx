import { useEffect, useRef, useState, type CSSProperties } from 'react'
import RemoveIcon from '@mui/icons-material/Remove'
import LoginOutlinedIcon from '@mui/icons-material/LoginOutlined'
import { usePublicAuth } from '../../app/publicAuthStore'
import PublicAccount from '../public/PublicAccount'
import { Link, useLocation, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useMediaQuery, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Drawer, IconButton, Menu, MenuItem, TextField, Tooltip } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import ArrowOutwardIcon from '@mui/icons-material/ArrowOutward'
import CloseIcon from '@mui/icons-material/Close'
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutlineOutlined'
import MenuIcon from '@mui/icons-material/VerticalSplitOutlined'
import MoreVertIcon from '@mui/icons-material/MoreVert'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import StopCircleOutlinedIcon from '@mui/icons-material/StopCircleOutlined'
import ReplayIcon from '@mui/icons-material/Replay'
import PlaceOutlinedIcon from '@mui/icons-material/PlaceOutlined'
import LocalFloristOutlinedIcon from '@mui/icons-material/LocalFloristOutlined'
import ArchitectureIcon from '@mui/icons-material/Architecture'
import PaletteOutlinedIcon from '@mui/icons-material/PaletteOutlined'
import { apiUrl } from '../../api/http'
import { archiveItemPath, conceptPath } from '../../app/archiveRoutes'
import { archiveActionPath } from '../../app/archiveFilterActions'
import type { OntologyFeatureType } from '../../types/catalogue'
import { cancelActiveTurn, changeChatOwner, deleteConversation, editQueuedQuestion, initializeChat, newConversation, removeQueuedQuestion, renameConversation, retryTurn, selectConversation, submitDraft, updateDraft, useChatDesign, type ChatConversation, type ChatQueuedQuestion, type ChatTurn } from './chatState'
import './chatTranslations'
import './chat.css'
import { groupChatSources } from './groupChatSources'

const ACTIVE = new Set(['QUEUED', 'RUNNING'])
const ENTITY_TYPES = new Set(['REGION', 'REGIONAL_EMBROIDERY', 'REGIONAL_MOTIF', 'MOTIF', 'ORNAMENT', 'TECHNIQUE', 'COLOR'])
function entityPath(type: string, localName: string) { return ENTITY_TYPES.has(type) ? conceptPath(type as OntologyFeatureType, localName) : '/archive' }
function errorText(t: (key: string, options?: { defaultValue: string }) => string, code: string | null) { return t(`errors.${code ?? 'CONVERSATION_PROCESSING_FAILED'}`, { defaultValue: t('errors.CONVERSATION_PROCESSING_FAILED') }) }
function timestamp(value: string | null | undefined) { const parsed = value ? Date.parse(value) : Number.NaN; return Number.isFinite(parsed) ? parsed : null }
function formatDuration(milliseconds: number) {
 const seconds = Math.max(0, milliseconds) / 1000
 if (seconds < 10) return `${seconds.toFixed(1)} s`
 if (seconds < 60) return `${Math.round(seconds)} s`
 return `${Math.floor(seconds / 60)} min ${Math.round(seconds % 60)} s`
}
function useTurnDuration(turn: ChatTurn) {
 const active = ACTIVE.has(turn.status)
 const [now, setNow] = useState(() => Date.now())
 useEffect(() => {
  if (!active) return
  setNow(Date.now())
  const timer = window.setInterval(() => setNow(Date.now()), 250)
  return () => window.clearInterval(timer)
 }, [active])
 const start = timestamp(turn.startedAt) ?? timestamp(turn.createdAt)
 const end = timestamp(turn.finishedAt) ?? now
 return start === null ? null : formatDuration(end - start)
}
export function shouldShowEvidenceWarning(answer: NonNullable<ChatTurn['answer']>) {
 return answer.insufficientEvidence && Boolean(
  answer.sources.length || answer.entityCards.length || answer.archiveCards.length || answer.media.length || answer.actions?.length
 )
}

function Composer() {
 const { t } = useTranslation('chatDesign'); const state = useChatDesign(); const active = state.conversations.find(item => item.id === state.activeId)!
 const unavailable = state.availability?.available === false
 const busy = active.turns.some(turn => ACTIVE.has(turn.status)) || state.loading
 return <form className="chat-composer" onSubmit={event => { event.preventDefault(); submitDraft() }}>
  <textarea aria-label={t('placeholder')} placeholder={unavailable ? t('unavailable') : busy ? t('queuePlaceholder') : t('placeholder')} value={active.draft} rows={3} disabled={unavailable}
   onChange={event => updateDraft(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); submitDraft() } }} />
  <div className="chat-composer-tools"><span><i /> EthnoWear <small>· {busy ? t('processing') : t('grounded')}{active.queue.length ? ` · ${t('queuedCount', { count: active.queue.length })}` : ''}</small></span>
   <span className="chat-composer-actions">{busy && <Button className="chat-cancel" size="small" startIcon={<StopCircleOutlinedIcon />} onClick={() => void cancelActiveTurn()}>{t('cancel')}</Button>}
    <Tooltip title={busy ? t('addToQueue') : t('send')}><span><IconButton type="submit" aria-label={busy ? t('addToQueue') : t('send')} disabled={unavailable || !active.draft.trim()} className="chat-send"><ArrowUpwardIcon fontSize="small" /></IconButton></span></Tooltip></span>
  </div>
 </form>
}

function Progress({ turn }: { turn: ChatTurn }) {
 const { t } = useTranslation('chatDesign')
 const duration = useTurnDuration(turn)
 const stages = [...new Set(turn.progress.map(item => item.stage).concat(turn.stage))]
 return <div className="chat-progress" role="status"><div className="chat-progress-title"><CircularProgress size={15} thickness={5} /><span>{t('processing')}</span>{duration && <time>{duration}</time>}</div>
  <ol>{stages.map(stage => <li key={stage} className={stage === turn.stage ? 'is-current' : ''}>{t(`stages.${stage}`)}</li>)}</ol></div>
}

function Answer({ turn, compact, copied, onCopy, onNavigate }: { turn: ChatTurn; compact: boolean; copied: string | null; onCopy: (id: string, text: string) => void; onNavigate?: () => void }) {
 const { t } = useTranslation('chatDesign'); const answer = turn.answer; const navigate = useNavigate()
 const duration = useTurnDuration(turn)
 if (ACTIVE.has(turn.status)) return <Progress turn={turn} />
 if (turn.status === 'FAILED') return <div className="chat-answer-state is-error"><strong>{t('failed')}</strong><p>{errorText(t, turn.errorCode)}</p><Button size="small" startIcon={<ReplayIcon />} onClick={() => retryTurn(turn.id)}>{t('retry')}</Button></div>
 if (turn.status === 'CANCELLED') return <div className="chat-answer-state"><p>{t('cancelled')}</p></div>
 if (!answer) return <Progress turn={turn} />
 return <article className="chat-answer">
  <header><span>EthnoWear{duration && <time>{t('answeredIn', { duration })}</time>}</span><Tooltip title={copied === turn.id ? t('copied') : t('copyAnswer')}><IconButton size="small" aria-label={t('copyAnswer')} onClick={() => onCopy(turn.id, answer.answer)}><ContentCopyIcon sx={{ fontSize: 15 }} /></IconButton></Tooltip></header>
  {shouldShowEvidenceWarning(answer) && <div className="chat-evidence-warning">{t('insufficientEvidence')}</div>}
  <p className="chat-answer-text">{answer.answer}</p>
  {!!answer.actions?.length && <section className="chat-actions" aria-label={t('archiveActions')}>
   {answer.actions.map((action, index) => {
    const path = archiveActionPath(action)
    return path ? <Button key={action.type + ':' + action.target + ':' + index} variant="outlined" endIcon={<ArrowOutwardIcon />}
     onClick={() => { onNavigate?.(); navigate(path) }}>{action.label}</Button> : null
   })}
  </section>}
  {!!answer.sources.length && <section className="chat-sources" aria-label={t('sources')}><strong>{t('sources')}</strong><ol>{groupChatSources(answer.sources).map(({ source, referenceNumbers }) => <li key={source.citationId}><span>[{referenceNumbers.join(', ')}]</span><div>{source.title}{source.author ? <small>{source.author}</small> : null}</div></li>)}</ol></section>}
  {!compact && (!!answer.entityCards.length || !!answer.archiveCards.length) && <section className="chat-results" aria-label={t('related')}>
   {answer.entityCards.map(card => <Link key={`${card.entityType}:${card.localName}`} to={entityPath(card.entityType, card.localName)}><span className="chat-result-thumb">{card.representativeMediaAssetId ? <img src={apiUrl(`/api/media/${card.representativeMediaAssetId}/content`)} alt="" /> : <LocalFloristOutlinedIcon />}</span><span><small>{t(`entities.${card.entityType}`)}</small><strong>{card.label}</strong></span><ArrowOutwardIcon fontSize="small" /></Link>)}
   {answer.archiveCards.map(card => <Link key={card.archiveItemId} to={archiveItemPath(card.archiveItemId)}><span className="chat-result-thumb">{card.representativeMediaAssetId ? <img src={apiUrl(`/api/media/${card.representativeMediaAssetId}/content`)} alt="" /> : <ArchitectureIcon />}</span><span><small>{t('archiveResult')}</small><strong>{card.title}</strong></span><ArrowOutwardIcon fontSize="small" /></Link>)}
  </section>}
  {!!answer.media.length && <section className="chat-media" aria-label={t('media')}>{answer.media.map(media => {
   const content = <><img src={apiUrl(media.contentUrl)} alt={media.caption ?? ''} /><span>{media.caption || t('mediaItem')}</span></>
   const path = media.archiveItemId ? archiveItemPath(media.archiveItemId) : media.entityType && media.entityLocalName ? entityPath(media.entityType, media.entityLocalName) : null
   return path ? <Link key={media.mediaAssetId} to={path}>{content}</Link> : <figure key={media.mediaAssetId}>{content}</figure>
  })}</section>}
 </article>
}

function QueuedQuestion({ question, position }: { question: ChatQueuedQuestion; position: number }) {
 const { t } = useTranslation('chatDesign')
 const [editing, setEditing] = useState(false)
 const [draft, setDraft] = useState(question.text)
 function save() { if (editQueuedQuestion(question.id, draft)) setEditing(false) }
 return <article className="chat-queued-item">
  <header><span>{t('queuedPosition', { position })}</span><div>
   {editing
    ? <><Button size="small" onClick={() => { setDraft(question.text); setEditing(false) }}>{t('cancelAction')}</Button><Button size="small" variant="contained" disabled={!draft.trim()} onClick={save}>{t('saveQueueEdit')}</Button></>
    : <><Tooltip title={t('editQueued')}><IconButton size="small" aria-label={t('editQueued')} onClick={() => setEditing(true)}><EditOutlinedIcon fontSize="small" /></IconButton></Tooltip><Tooltip title={t('removeQueued')}><IconButton size="small" aria-label={t('removeQueued')} onClick={() => removeQueuedQuestion(question.id)}><DeleteOutlineIcon fontSize="small" /></IconButton></Tooltip></>}
  </div></header>
  {editing ? <TextField autoFocus fullWidth multiline minRows={2} value={draft} onChange={event => setDraft(event.target.value)} slotProps={{ htmlInput: { maxLength: 4000 } }} /> : <p>{question.text}</p>}
 </article>
}

function Messages({ compact = false, onNavigate }: { compact?: boolean; onNavigate?: () => void }) {
	 const { t } = useTranslation('chatDesign'); const state = useChatDesign(); const active = state.conversations.find(item => item.id === state.activeId)!
	 const bottom = useRef<HTMLDivElement>(null); const [copied, setCopied] = useState<string | null>(null)
	 const latestTurnStatus = active.turns.at(-1)?.status
	 useEffect(() => { bottom.current?.scrollIntoView?.({ block: 'nearest' }) }, [active.id, active.turns.length, latestTurnStatus])
 const prompts = [{ key: 'p1', label: 'region', Icon: PlaceOutlinedIcon }, { key: 'p2', label: 'motif', Icon: LocalFloristOutlinedIcon }, { key: 'p3', label: 'technique', Icon: ArchitectureIcon }, { key: 'p4', label: 'color', Icon: PaletteOutlinedIcon }]
 const copy = (id: string, value: string) => { void navigator.clipboard.writeText(value).then(() => setCopied(id)).catch(() => setCopied(null)) }
 if (!active.turns.length) return <div className={'chat-welcome' + (compact ? ' is-compact' : '')}>
  <img src="/logo_v3.png" alt="" /><p className="chat-eyebrow">{t('subtitle')}</p><h1>{t('question')}</h1><p className="chat-intro">{t('intro')}</p>
  <div className="chat-prompts">{prompts.slice(0, compact ? 2 : 4).map(({ key, label, Icon }) => <button key={key} onClick={() => updateDraft(t(key))}><span className={'chat-topic ' + label}><Icon fontSize="small" /></span><span><small>{t(label)}</small><strong>{t(key)}</strong></span><ArrowOutwardIcon fontSize="small" /></button>)}</div>
 </div>
 return <div className="chat-messages">{active.turns.map(turn => <section className="chat-turn" key={turn.id}>
  <article className="chat-message"><header><span>{t('you')}</span><Tooltip title={copied === `user:${turn.id}` ? t('copied') : t('copy')}><IconButton size="small" aria-label={t('copy')} onClick={() => copy(`user:${turn.id}`, turn.userMessage)}><ContentCopyIcon sx={{ fontSize: 15 }} /></IconButton></Tooltip></header><p>{turn.userMessage}</p></article>
  <Answer turn={turn} compact={compact} copied={copied} onCopy={copy} onNavigate={onNavigate} />
 </section>)}{!!active.queue.length && <section className="chat-queue" aria-label={t('queue')}><header><strong>{t('queue')}</strong><span>{active.queue.length}</span></header>{active.queue.map((question, index) => <QueuedQuestion key={question.id} question={question} position={index + 1} />)}</section>}<div ref={bottom} /></div>
}

function ChatInitializer() { useEffect(() => { void initializeChat(); const changed = () => changeChatOwner(); window.addEventListener('ethnowear-public-owner-changed', changed); return () => window.removeEventListener('ethnowear-public-owner-changed', changed) }, []); return null }

function ChatHistoryItem({ item, active, onSelect }: { item: ChatConversation; active: boolean; onSelect: () => void }) {
 const { t } = useTranslation('chatDesign')
 const title = item.title || item.turns[0]?.userMessage || item.draft || t('new')
 const [anchor, setAnchor] = useState<HTMLElement | null>(null)
 const [action, setAction] = useState<'rename' | 'delete' | null>(null)
 const [draft, setDraft] = useState(title)
 const [pending, setPending] = useState(false)
 const activeTurn = item.turns.some(turn => ACTIVE.has(turn.status))
 function closeAction() { if (!pending) { setAction(null); setDraft(title) } }
 async function confirm() {
  setPending(true)
  const succeeded = action === 'rename'
   ? await renameConversation(item.id, draft)
   : await deleteConversation(item.id)
  setPending(false)
  if (succeeded) setAction(null)
 }
 return <div className="chat-history-row" data-active={active || undefined}>
  <button className="chat-history-select" aria-current={active ? 'page' : undefined} onClick={onSelect}><ChatBubbleOutlineIcon fontSize="small" /><span>{title}</span></button>
  {!item.local && <><Tooltip title={t('options')}><IconButton className="chat-history-options" size="small" aria-label={t('optionsFor', { title })} aria-controls={anchor ? `chat-menu-${item.id}` : undefined} aria-haspopup="menu" onClick={event => setAnchor(event.currentTarget)}><MoreVertIcon fontSize="small" /></IconButton></Tooltip>
   <Menu id={`chat-menu-${item.id}`} anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}>
    <MenuItem onClick={() => { setAnchor(null); setDraft(title); setAction('rename') }}><EditOutlinedIcon fontSize="small" />{t('rename')}</MenuItem>
    <MenuItem disabled={activeTurn} onClick={() => { setAnchor(null); setAction('delete') }} sx={{ color: 'error.main' }}><DeleteOutlineIcon fontSize="small" />{t('delete')}</MenuItem>
   </Menu>
  </>}
  <Dialog open={action === 'rename'} onClose={closeAction} fullWidth maxWidth="xs">
   <DialogTitle sx={{ color: 'text.primary' }}>{t('renameTitle')}</DialogTitle><DialogContent><TextField autoFocus fullWidth value={draft} onChange={event => setDraft(event.target.value)} label={t('conversationTitle')} slotProps={{ htmlInput: { maxLength: 300 } }} sx={{ mt: 1 }} /></DialogContent>
   <DialogActions><Button onClick={closeAction} disabled={pending}>{t('cancelAction')}</Button><Button variant="contained" onClick={() => void confirm()} disabled={pending || !draft.trim()}>{t('saveRename')}</Button></DialogActions>
  </Dialog>
  <Dialog open={action === 'delete'} onClose={closeAction} fullWidth maxWidth="xs">
   <DialogTitle sx={{ color: 'text.primary' }}>{t('deleteTitle')}</DialogTitle><DialogContent><DialogContentText sx={{ color: 'text.primary' }}>{t('deleteDescription', { title })}</DialogContentText></DialogContent>
   <DialogActions><Button onClick={closeAction} disabled={pending}>{t('cancelAction')}</Button><Button color="error" variant="contained" onClick={() => void confirm()} disabled={pending}>{t('confirmDelete')}</Button></DialogActions>
  </Dialog>
 </div>
}

export function ChatPage() {
 const { t } = useTranslation('chatDesign'); const state = useChatDesign()
 const mobile = useMediaQuery('(max-width:700px)', { noSsr: true })
 const [sidebar, setSidebar] = useState(() => !mobile)
 const [fontScale, setFontScale] = useState(100)
 const { profile } = usePublicAuth()
 const [loginOpen, setLoginOpen] = useState(false)
 const [loginPending, setLoginPending] = useState(false)
 const pageRef = useRef<HTMLElement>(null)
 return <section ref={pageRef} className="chat-page" style={{ '--chat-font-scale': fontScale / 100 } as CSSProperties}><ChatInitializer />
  <Drawer variant={mobile ? 'temporary' : 'persistent'} open={sidebar} onClose={() => setSidebar(false)} container={() => pageRef.current} ModalProps={{ disablePortal: true, disableScrollLock: true, disableEnforceFocus: true }} sx={{ position: mobile ? 'absolute' : 'relative', width: mobile ? undefined : sidebar ? 320 : 0, flexShrink: 0, transition: 'width 225ms ease', zIndex: 10, overflow: mobile ? undefined : 'hidden' }} slotProps={{ backdrop: { sx: { position: 'absolute' } }, paper: { sx: { position: 'absolute', width: 320, maxWidth: mobile ? '90%' : 'none', height: '100%' }, 'aria-label': t('history') } }}>
  <aside className="chat-sidebar chat-history-drawer"><div className="chat-sidebar-brand"><img src="/logo_v3.png" alt="" /><div><strong>EthnoWear</strong><small>{t('nav')}</small></div><Tooltip title={t('close')}><IconButton aria-label={t('close')} onClick={() => setSidebar(false)} sx={{ ml: 'auto' }}><CloseIcon /></IconButton></Tooltip></div>
   <button className="chat-new" onClick={() => { newConversation(); if (mobile) setSidebar(false) }}><AddIcon fontSize="small" />{t('new')}</button>
   {!profile && <div className="chat-history-login"><p>{t('historyLoginMessage')}</p><Button variant="outlined" fullWidth startIcon={<LoginOutlinedIcon />} onClick={() => setLoginOpen(true)}>{t('historyLogin')}</Button></div>}
   <div className="chat-history-label">{t('conversations')}<span>{state.conversations.filter(item => !item.local || item.turns.length).length}</span></div>
   <nav aria-label={t('conversations')}>{state.conversations.map(item => <ChatHistoryItem key={item.id} item={item} active={item.id === state.activeId} onSelect={() => { void selectConversation(item.id); if (mobile) setSidebar(false) }} />)}</nav>
   <Link className="chat-archive" to="/archive">{t('archive')}<ArrowOutwardIcon fontSize="small" /></Link><small className="chat-local">{t('historyOwner')}</small>
  </aside></Drawer>
  <Dialog open={loginOpen && !profile} onClose={() => { if (!loginPending) setLoginOpen(false) }} fullWidth maxWidth="xs">
   <DialogTitle sx={{ color: 'text.primary' }}>{t('historyLogin')}</DialogTitle>
   <DialogContent><PublicAccount loginPanel onSuccess={() => setLoginOpen(false)} onPendingChange={setLoginPending} /></DialogContent>
   <DialogActions><Button disabled={loginPending} onClick={() => setLoginOpen(false)}>{t('cancelAction')}</Button></DialogActions>
  </Dialog>
  <div className="chat-workspace"><header className="chat-workspace-header"><div><Tooltip title={t('history')}><IconButton aria-label={t('history')} aria-expanded={sidebar} onClick={() => setSidebar(!sidebar)}><MenuIcon /></IconButton></Tooltip><strong>{t('nav')}</strong><span>EthnoWear</span></div><div className="chat-font-controls" role="group" aria-label={t('fontSize')}><Tooltip title={t('smallerText')}><span><IconButton aria-label={t('smallerText')} disabled={fontScale <= 80} onClick={() => setFontScale(value => value - 10)}><RemoveIcon /></IconButton></span></Tooltip><Tooltip title={t('resetText')}><Button onClick={() => setFontScale(100)} aria-label={t('resetText')}>{fontScale}%</Button></Tooltip><Tooltip title={t('largerText')}><span><IconButton aria-label={t('largerText')} disabled={fontScale >= 150} onClick={() => setFontScale(value => value + 10)}><AddIcon /></IconButton></span></Tooltip></div></header>
   {state.availability?.available === false && <div className="chat-global-error" role="alert"><strong>{t('unavailable')}</strong><br />{state.availability.unavailableCodes.map(code => errorText(t, code)).join(' ')}</div>}
   {state.errorCode && <div className="chat-global-error" role="alert">{errorText(t, state.errorCode)}</div>}
   <div className="chat-scroll">{state.loading && !state.ready ? <CircularProgress className="chat-loader" size={26} /> : <Messages />}</div><div className="chat-composer-wrap"><Composer /></div></div>
 </section>
}
export function QuickChat() {
 const { t } = useTranslation('chatDesign'); const { pathname } = useLocation(); const navigate = useNavigate(); const [open, setOpen] = useState(false)
 if (pathname.startsWith('/chat') || pathname.startsWith('/management') || pathname.startsWith('/account')) return null
 function expand() { setOpen(false); navigate('/chat') }
 return <><Tooltip title={t('open')} placement="left"><button className="chat-launcher" aria-label={t('open')} aria-haspopup="dialog" aria-expanded={open} onClick={() => setOpen(true)}><img src="/logo_v3.png" alt="" /><span><ChatBubbleOutlineIcon sx={{ fontSize: 13 }} /></span></button></Tooltip>
  <Dialog className="quick-chat-dialog" open={open} onClose={() => setOpen(false)} maxWidth={false} aria-labelledby="quick-chat-title"><ChatInitializer />
   <header className="quick-chat-header"><img src="/logo_v3.png" alt="" /><div><strong id="quick-chat-title">EthnoWear</strong><small>{t('nav')}</small></div><Tooltip title={t('full')}><IconButton aria-label={t('full')} onClick={expand}><ArrowOutwardIcon /></IconButton></Tooltip><Tooltip title={t('close')}><IconButton aria-label={t('close')} onClick={() => setOpen(false)}><CloseIcon /></IconButton></Tooltip></header>
   <div className="quick-chat-scroll"><Messages compact onNavigate={() => setOpen(false)} /></div><div className="quick-chat-composer"><Composer /></div><button className="quick-chat-continue" onClick={expand}>{t('full')}<ArrowOutwardIcon fontSize="small" /></button>
  </Dialog></>
}

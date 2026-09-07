import { useEffect, useRef, useState } from 'react'
import { Alert, Autocomplete, Box, Button, CircularProgress, Divider, IconButton, ListItemIcon, ListItemText, Menu, MenuItem, Paper, Stack, Tab, Tabs, TextField, Tooltip, Typography } from '@mui/material'
import CheckOutlinedIcon from '@mui/icons-material/CheckOutlined'
import CloseOutlinedIcon from '@mui/icons-material/CloseOutlined'
import ReplayOutlinedIcon from '@mui/icons-material/ReplayOutlined'
import RestartAltOutlinedIcon from '@mui/icons-material/RestartAltOutlined'
import SaveOutlinedIcon from '@mui/icons-material/SaveOutlined'
import FitScreenIcon from '@mui/icons-material/FitScreen'
import ZoomInIcon from '@mui/icons-material/ZoomIn'
import ZoomOutIcon from '@mui/icons-material/ZoomOut'
import ImageSearchOutlinedIcon from '@mui/icons-material/ImageSearchOutlined'
import DeleteOutlineOutlinedIcon from '@mui/icons-material/DeleteOutlineOutlined'
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined'
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import MoreVertIcon from '@mui/icons-material/MoreVert'
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown'
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import AddIcon from '@mui/icons-material/Add'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
    approvePageTranscription,
    changePageSourceProvenance,
    changePageProvenanceTrust,
    documentQueryKeys,
    getCurrentPageQuality,
    getDocumentPage,
    getDocument,
    getDocumentPageWorkflow,
    queuePageReprocessing,
    rejectPageTranscription,
    resetPageTranscriptionFromCurrentOcr,
    retireDocumentPage,
    savePageTranscription,
    startPageImageExtraction,
    updateDocumentPageMetadata,
} from '../../../api/DocumentAdminApi'
import { sourceReferencesApi, sourcesApi } from '../../../api/ArchiveAdminApi'
import { apiErrorMessage } from '../../../api/http'
import AdminModal from '../AdminModal'
import ConfirmDialog from '../ConfirmDialog'
import { useAdminAuth } from '../../../app/adminAuth'
import { administratorRoles, hasAnyRole, processingMutationRoles, reviewRoles } from '../../../app/permissions'
import DocumentStatusChip from './DocumentStatusChip'
import QualityResultSummary from './QualityResultSummary'
import ImageViewerDialog from '../../common/ImageViewerDialog'
import PageWorkflowProgress from './PageWorkflowProgress'
import DocumentPageHistoryPanel, { type PageHistoryKind } from './DocumentPageHistoryPanel'
import type { SourceDetails, SourceReferenceDetails } from '../../../types/archive'
import type { DocumentPageDetails, DocumentPageReview, ProvenanceTrustState } from '../../../types/document'
import VisionAssessmentPanel, { VisionAssessmentHistoryPanel } from './VisionAssessmentPanel'
import SynchronizedTextDiff from './SynchronizedTextDiff'
import ResizableSplitPane from '../../common/ResizableSplitPane'
import DocumentPageFiguresWorkspace from './DocumentPageFiguresWorkspace'
import { useAdminMediaContent } from '../media/useAdminMediaContent'
import SourceReferenceCreateDialog from '../archive-editor/SourceReferenceCreateDialog'

type Props = {
    open: boolean
    documentId: number
    pageId: number | null
    onClose: () => void
    onChanged?: () => void
    hasPrevious?: boolean
    hasNext?: boolean
    navigationPending?: boolean
    onPrevious?: () => void
    onNext?: () => void
}

const MIN_ZOOM = 0.5
const MAX_ZOOM = 3
const ZOOM_STEP = 0.25
export default function DocumentPageReviewDialog({ open, documentId, pageId, onClose, onChanged, hasPrevious = false, hasNext = false, navigationPending = false, onPrevious, onNext }: Props) {
    const { t } = useTranslation()
    const { admin } = useAdminAuth()
    const queryClient = useQueryClient()
    const canProcess = Boolean(admin && hasAnyRole(admin.roles, processingMutationRoles))
    const canReview = Boolean(admin && hasAnyRole(admin.roles, reviewRoles))
    const isAdministrator = Boolean(admin && hasAnyRole(admin.roles, administratorRoles))
    const auditReason = t('documents.sourceReference.reviewAuditReason', {
        username: admin?.username ?? 'unknown',
        role: admin?.roles.map(role => t(`auth.roles.${role}`)).join(', ') ?? 'unknown',
    })
    const validIds = documentId > 0 && pageId !== null && pageId > 0
    const documentQuery = useQuery({
        queryKey: documentQueryKeys.detail(documentId),
        queryFn: ({ signal }) => getDocument(documentId, signal),
        enabled: open && documentId > 0,
    })
    const detailQuery = useQuery({
        queryKey: documentQueryKeys.page(documentId, pageId ?? 0),
        queryFn: ({ signal }) => getDocumentPage(documentId, pageId!, signal),
        enabled: open && validIds,
    })
    const qualityQuery = useQuery({
        queryKey: documentQueryKeys.pageQuality(documentId, pageId ?? 0),
        queryFn: ({ signal }) => getCurrentPageQuality(documentId, pageId!, signal),
        enabled: open && validIds,
    })
    const workflowQuery = useQuery({
        queryKey: documentQueryKeys.pageWorkflow(documentId, pageId ?? 0),
        queryFn: ({ signal }) => getDocumentPageWorkflow(documentId, pageId!, signal),
        enabled: open && validIds,
    })
    const sourceOptionsQuery = useQuery({
        queryKey: ['admin', 'document-page-source-options'],
        queryFn: async ({ signal }) => {
            const [references, sources] = await Promise.all([
                sourceReferencesApi.findAll({ page: 0, size: 1000 }, signal),
                sourcesApi.findAll({ page: 0, size: 1000 }, signal),
            ])
            return { references: references.content, sources: sources.content }
        },
        enabled: open && validIds,
        staleTime: 5 * 60 * 1000,
    })
    const [draft, setDraft] = useState<string | null>(null)
    const [pending, setPending] = useState<'save' | 'approve' | 'reject' | 'reprocess' | 'extract' | 'delete' | 'resetCorrectedText' | 'metadata' | 'source' | 'trust' | null>(null)
    const [confirmAction, setConfirmAction] = useState<'reject' | 'reprocess' | 'extract' | 'delete' | 'resetCorrectedText' | null>(null)
    const [deleteReason, setDeleteReason] = useState('')
    const [rejectionReason, setRejectionReason] = useState('')
    const [workspaceTab, setWorkspaceTab] = useState<'edit' | 'details' | 'figures'>('edit')
    const [editTab, setEditTab] = useState<'editor' | 'compare'>('editor')
    const [supportTab, setSupportTab] = useState<'checks' | 'visionHistory' | PageHistoryKind>('checks')
    const [inspectorOpen, setInspectorOpen] = useState(false)
    const [moreAnchor, setMoreAnchor] = useState<HTMLElement | null>(null)
    const [unsavedAction, setUnsavedAction] = useState<'close' | 'previous' | 'next' | null>(null)
    const [confirmUnchangedApproval, setConfirmUnchangedApproval] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [notice, setNotice] = useState<string | null>(null)
    const [printedPageNumber, setPrintedPageNumber] = useState('')
    const [sourceReferenceId, setSourceReferenceId] = useState('')
    const [sourceReason, setSourceReason] = useState('')
    const [sourceReferenceCreateOpen, setSourceReferenceCreateOpen] = useState(false)
    const [provenanceTrust, setProvenanceTrust] = useState<ProvenanceTrustState>('UNKNOWN')
    const [provenanceReason, setProvenanceReason] = useState('')
    const [zoom, setZoom] = useState(1)
    const [imagePreviewOpen, setImagePreviewOpen] = useState(false)
    const editorRef = useRef<HTMLTextAreaElement | null>(null)
    const detail = detailQuery.data
    const initialText = detail?.correctedText ?? detail?.rawOcrText ?? ''
    const displayedText = draft ?? initialText
    const dirty = draft !== null && draft !== initialText
    const textWorkspace = workspaceTab === 'edit'

    function openTextEditor(startOffset: number | null, endOffset: number | null, originalText: string | null) {
        setWorkspaceTab('edit')
        setEditTab('editor')
        window.requestAnimationFrame(() => {
            const editor = editorRef.current
            if (!editor) return
            const fallbackStart = originalText ? displayedText.indexOf(originalText) : -1
            const start = startOffset !== null && startOffset >= 0 && startOffset <= displayedText.length ? startOffset : fallbackStart
            const end = endOffset !== null && start >= 0 && endOffset > start && endOffset <= displayedText.length
                ? endOffset
                : start >= 0 ? start + (originalText?.length ?? 0) : 0
            editor.focus()
            if (start >= 0) editor.setSelectionRange(start, end)
        })
    }

    useEffect(() => {
        setDraft(null)
        setError(null)
        setNotice(null)
        setZoom(1)
        setImagePreviewOpen(false)
        setSupportTab('checks')
        setInspectorOpen(false)
        setMoreAnchor(null)
    }, [pageId])

    useEffect(() => {
        if (!detail) return
        setPrintedPageNumber(detail.summary.printedPageNumber ?? '')
        setSourceReferenceId(String(detail.summary.sourceReferenceId ?? documentQuery.data?.summary.defaultSourceReferenceId ?? ''))
        setSourceReason(auditReason)
        setProvenanceTrust(detail.summary.provenanceTrustState === 'UNKNOWN' ? documentQuery.data?.summary.provenanceTrustState ?? 'UNKNOWN' : detail.summary.provenanceTrustState)
        setProvenanceReason(auditReason)
    }, [auditReason, detail?.summary.id, documentQuery.data?.summary.id])

    useEffect(() => {
        if (!detail || detail.summary.sourceReferenceId || documentQuery.data?.summary.defaultSourceReferenceId) return
        const sourceId = documentQuery.data?.summary.sourceId
        if (!sourceId) return
        const matches = (sourceOptionsQuery.data?.references ?? []).filter(reference => reference.sourceId === sourceId)
        if (matches.length === 1) setSourceReferenceId(current => current || String(matches[0].id))
    }, [detail?.summary.id, documentQuery.data?.summary.sourceId, sourceOptionsQuery.data])

    function closeImmediately() {
        setDraft(null)
        setError(null)
        setNotice(null)
        setZoom(1)
        setImagePreviewOpen(false)
        setConfirmAction(null)
        setConfirmUnchangedApproval(false)
        setMoreAnchor(null)
        setDeleteReason('')
        setRejectionReason('')
        onClose()
    }

    function requestNavigation(action: 'close' | 'previous' | 'next') {
        if (dirty) { setUnsavedAction(action); return }
        navigateImmediately(action)
    }

    function navigateImmediately(action: 'close' | 'previous' | 'next') {
        if (action === 'close') closeImmediately()
        if (action === 'previous') onPrevious?.()
        if (action === 'next') onNext?.()
    }

    async function refreshPage() {
        if (pageId === null) return
        await Promise.all([
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.page(documentId, pageId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.pagesRoot(documentId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.detail(documentId), exact: true }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.progress(documentId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.indexing(documentId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.chunkEligibility(documentId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.generatedChunksRoot(documentId) }),
        ])
        await Promise.all([detailQuery.refetch(), qualityQuery.refetch(), workflowQuery.refetch()])
        onChanged?.()
    }

    async function runSave() {
        if (pageId === null) return
        setPending('save')
        setError(null)
        try {
            await savePageTranscription(pageId, displayedText)
            setDraft(null)
            await refreshPage()
        } catch (caught) {
            setError(apiErrorMessage(caught, t('documents.pageWorkspace.actionFailed')))
        } finally {
            setPending(null)
        }
    }

    function applySuccessfulApproval(review: DocumentPageReview) {
        if (pageId === null) return
        queryClient.setQueryData<DocumentPageDetails>(documentQueryKeys.page(documentId, pageId), current => current ? {
            ...current,
            summary: {
                ...current.summary,
                reviewState: review.newReviewState,
                transcriptionApprovalState: review.newApprovalState,
            },
        } : current)
    }

    async function runApproval() {
        if (pageId === null || !detail) return
        setPending('approve')
        setError(null)
        setNotice(null)
        try {
            const defaultSourceReferenceId = documentQuery.data?.summary.defaultSourceReferenceId ?? null
            if (detail.summary.sourceReferenceId === null && defaultSourceReferenceId !== null && Number(sourceReferenceId) === defaultSourceReferenceId) {
                await changePageSourceProvenance(pageId, {
                    sourceReferenceId: defaultSourceReferenceId,
                    provenanceStatus: 'KNOWN_SOURCE',
                    provenanceTrustState: detail.summary.provenanceTrustState,
                    note: detail.provenanceNote,
                    reason: sourceReason.trim() || t('documents.pageReview.sourceInheritedReason'),
                })
            }
            const review = await approvePageTranscription(pageId, null)
            setDraft(null)
            setConfirmUnchangedApproval(false)
            await refreshPage()
            applySuccessfulApproval(review)
            if (detail.summary.indexingState === 'NOT_ELIGIBLE')
                setNotice(t('documents.pageReview.approvedIndexingBlocked'))
        } catch (caught) {
            setError(apiErrorMessage(caught, t('documents.pageWorkspace.actionFailed')))
        } finally {
            setPending(null)
        }
    }

    async function runDetailsSave() {
        if (pageId === null || !detail || pending !== null) return
        setPending('metadata'); setError(null); setNotice(null)
        try {
            if (canProcess && printedPageNumber.trim() !== (detail.summary.printedPageNumber ?? '')) {
                const latest = await getDocumentPage(documentId, pageId)
                await updateDocumentPageMetadata(documentId, pageId, latest.summary.versionToken, {
                    printedPageNumber: printedPageNumber.trim() || null,
                    printedPageSort: latest.summary.printedPageSort,
                    pageLabel: latest.summary.pageLabel,
                })
            }
            if (canReview && sourceReferenceId && Number(sourceReferenceId) !== detail.summary.sourceReferenceId) {
                await changePageSourceProvenance(pageId, {
                    sourceReferenceId: Number(sourceReferenceId),
                    provenanceStatus: 'KNOWN_SOURCE',
                    provenanceTrustState: provenanceTrust,
                    note: detail.provenanceNote,
                    reason: sourceReason.trim() || auditReason,
                })
            } else if (canReview && provenanceTrust !== detail.summary.provenanceTrustState) {
                await changePageProvenanceTrust(pageId, { provenanceTrustState: provenanceTrust, reason: provenanceReason.trim() || auditReason })
            }
            await refreshPage()
            setNotice(t('documents.pageReview.metadataSaved'))
        } catch (caught) {
            setError(apiErrorMessage(caught, t('documents.pageReview.metadataFailed')))
        } finally { setPending(null) }
    }

    async function runWorkflowAction() {
        if (pageId === null || !confirmAction || !detail) return
        const action = confirmAction
        setPending(action)
        setError(null)
        try {
            if (action === 'reprocess') await queuePageReprocessing(pageId)
            if (action === 'extract') await startPageImageExtraction(documentId, pageId)
            if (action === 'delete') await retireDocumentPage(documentId, pageId, detail.summary.versionToken, deleteReason.trim())
            if (action === 'resetCorrectedText') await resetPageTranscriptionFromCurrentOcr(pageId)
            if (action === 'reject') await rejectPageTranscription(pageId, rejectionReason.trim())
            if (action === 'resetCorrectedText') setDraft(null)
            setConfirmAction(null)
            setDeleteReason('')
            setRejectionReason('')
            if (action === 'delete') {
                setPending(null)
                closeImmediately()
                return
            }
            await refreshPage()
        } catch (caught) {
            setError(apiErrorMessage(caught, t('documents.pageWorkspace.actionFailed')))
            setConfirmAction(null)
        } finally {
            setPending(null)
        }
    }

    const summary = detail?.summary
    const previewId = summary?.previewMediaAssetId ?? detail?.media[0]?.mediaAssetId
    const previewContent = useAdminMediaContent(open ? previewId : null)
    const title = summary
        ? t('documents.pageWorkspace.title', { page: summary.printedPageNumber ?? summary.pageLabel ?? summary.pageSequence })
        : t('documents.pageWorkspace.reviewTitle')
    const activeOcr = workflowQuery.data?.steps.some(step => step.step === 'OCR' && ['QUEUED', 'IN_PROGRESS'].includes(step.status)) ?? false
    const activeExtraction = workflowQuery.data?.steps.some(step => step.step === 'IMAGE_EXTRACTION' && ['QUEUED', 'IN_PROGRESS'].includes(step.status)) ?? false
    const alreadyApproved = summary?.transcriptionApprovalState === 'APPROVED'
    const unchangedFromOcr = Boolean(detail?.rawOcrText && displayedText === detail.rawOcrText)
    const visionQuality = qualityQuery.data?.find(item => item.assessorType === 'VISION_MODEL' || item.assessmentType.includes('VISION'))
    const ocrQuality = qualityQuery.data?.find(item => item.assessorType === 'DETERMINISTIC' && !item.assessmentType.includes('VISION'))

    function openWorkflowAction(action: 'reject' | 'reprocess' | 'extract' | 'delete' | 'resetCorrectedText') {
        setMoreAnchor(null)
        setConfirmAction(action)
    }

    return <>
    <AdminModal
        open={open}
        title={title}
        description={summary && <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mt: 1, alignItems: { md: 'center' } }}>
            <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
                <DocumentStatusChip kind="review" value={summary.reviewState} />
                <DocumentStatusChip kind="transcription" value={summary.transcriptionApprovalState} />
                <DocumentStatusChip kind="indexing" value={summary.indexingState} />
            </Stack>
            <Box sx={{ minWidth: 100 }}><PageWorkflowProgress progress={workflowQuery.data} loading={workflowQuery.isPending} compact /></Box>
        </Stack>}
        onClose={() => requestNavigation('close')}
        closeDisabled={pending !== null}
        maxWidth="xl"
        workspace
        headerActions={<>
            <Tooltip title={t('documents.pageWorkspace.previous')}><span><IconButton aria-label={t('documents.pageWorkspace.previous')} disabled={!hasPrevious || navigationPending || pending !== null} onClick={() => requestNavigation('previous')}><ChevronLeftIcon /></IconButton></span></Tooltip>
            <Tooltip title={t('documents.pageWorkspace.next')}><span><IconButton aria-label={t('documents.pageWorkspace.next')} disabled={!hasNext || navigationPending || pending !== null} onClick={() => requestNavigation('next')}><ChevronRightIcon /></IconButton></span></Tooltip>
        </>}
        actions={detail && <>
            <Button onClick={() => requestNavigation('close')} disabled={pending !== null}>{t('admin.cancel')}</Button>
            <Button endIcon={<MoreVertIcon />} onClick={event => setMoreAnchor(event.currentTarget)} disabled={pending !== null}>{t('documents.pageReview.moreActions')}</Button>
            <Menu anchorEl={moreAnchor} open={Boolean(moreAnchor)} onClose={() => setMoreAnchor(null)}>
                <MenuItem onClick={() => { setDraft(null); setMoreAnchor(null) }} disabled={!dirty}>
                    <ListItemIcon><RestartAltOutlinedIcon fontSize="small" /></ListItemIcon><ListItemText>{t('documents.pageWorkspace.reset')}</ListItemText>
                </MenuItem>
                {canReview && <MenuItem onClick={() => openWorkflowAction('reject')} disabled={dirty || !detail.correctedText}>
                    <ListItemIcon><CloseOutlinedIcon fontSize="small" /></ListItemIcon><ListItemText>{t('documents.pageReview.reject')}</ListItemText>
                </MenuItem>}
                {(canProcess || isAdministrator) && <Divider />}
                {canProcess && <MenuItem onClick={() => openWorkflowAction('extract')} disabled={activeExtraction}>
                    <ListItemIcon><ImageSearchOutlinedIcon fontSize="small" /></ListItemIcon><ListItemText>{t('documents.pageWorkspace.extractImage')}</ListItemText>
                </MenuItem>}
                {canProcess && <MenuItem onClick={() => openWorkflowAction('reprocess')} disabled={activeOcr}>
                    <ListItemIcon><ReplayOutlinedIcon fontSize="small" /></ListItemIcon><ListItemText>{t('documents.pageWorkspace.reprocess')}</ListItemText>
                </MenuItem>}
                {isAdministrator && <MenuItem onClick={() => openWorkflowAction('resetCorrectedText')}>
                    <ListItemIcon><HistoryOutlinedIcon fontSize="small" /></ListItemIcon><ListItemText>{t('documents.pageWorkspace.resetCorrectedText')}</ListItemText>
                </MenuItem>}
                {canProcess && <Divider />}
                {canProcess && <MenuItem onClick={() => openWorkflowAction('delete')} sx={{ color: 'error.main' }}>
                    <ListItemIcon sx={{ color: 'inherit' }}><DeleteOutlineOutlinedIcon fontSize="small" /></ListItemIcon><ListItemText>{t('documents.pageWorkspace.delete')}</ListItemText>
                </MenuItem>}
            </Menu>
            <Box sx={{ flex: 1 }} />
            {canReview && alreadyApproved && <Button variant="contained" startIcon={<CheckOutlinedIcon />} disabled>{t('documents.pageReview.approved')}</Button>}
            {canReview && !alreadyApproved && <Button variant="contained" startIcon={<CheckOutlinedIcon />} onClick={() => unchangedFromOcr ? setConfirmUnchangedApproval(true) : void runApproval()} disabled={pending !== null || dirty || displayedText.trim() === ''}>{t('documents.pageWorkspace.approve')}</Button>}
        </>}
    >
        {error && <Alert severity="error" onClose={() => setError(null)} sx={{ mb: 2 }}>{error}</Alert>}
        {notice && <Alert severity="info" onClose={() => setNotice(null)} sx={{ mb: 2 }}>{notice}</Alert>}
        {detailQuery.isPending && <Box sx={{ minHeight: 540, display: 'grid', placeItems: 'center' }}><CircularProgress /></Box>}
        {detailQuery.isError && <Alert severity="error">{apiErrorMessage(detailQuery.error, t('documents.pageWorkspace.loadFailed'))}</Alert>}
        {detail && <Stack spacing={1}>
            <ResizableSplitPane
                label={t('documents.pageReview.resizePanels')}
                initialPercent={40}
                first={<Paper variant="outlined" sx={{ height: { md: 'calc(100dvh - 230px)' }, minHeight: 420, overflow: 'hidden', position: 'relative', bgcolor: 'grey.100' }}>
                {previewContent.url && <Box sx={{ position: 'absolute', top: 12, right: 12, zIndex: 2 }}>
                    <Stack direction="row" sx={{ overflow: 'hidden', bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 1, boxShadow: 1, '& .MuiIconButton-root': { width: 36, height: 36, borderRadius: 0 } }}>
                        <Tooltip title={t('pdfViewer.zoomOut')}><span><IconButton disabled={zoom <= MIN_ZOOM} onClick={() => setZoom(value => Math.max(MIN_ZOOM, value - ZOOM_STEP))}><ZoomOutIcon fontSize="small" /></IconButton></span></Tooltip>
                        <Typography variant="body2" sx={{ width: 64, display: 'grid', placeItems: 'center', borderLeft: 1, borderRight: 1, borderColor: 'divider', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{Math.round(zoom * 100)}%</Typography>
                        <Tooltip title={t('pdfViewer.zoomIn')}><span><IconButton disabled={zoom >= MAX_ZOOM} onClick={() => setZoom(value => Math.min(MAX_ZOOM, value + ZOOM_STEP))}><ZoomInIcon fontSize="small" /></IconButton></span></Tooltip>
                        <Tooltip title={t('pdfViewer.fit')}><span><IconButton disabled={zoom === 1} onClick={() => setZoom(1)} sx={{ borderLeft: 1, borderColor: 'divider' }}><FitScreenIcon fontSize="small" /></IconButton></span></Tooltip>
                        <Tooltip title={t('imageViewer.open', { title: t('documents.pageWorkspace.preview') })}><IconButton aria-label={t('imageViewer.open', { title: t('documents.pageWorkspace.preview') })} onClick={() => setImagePreviewOpen(true)} sx={{ borderLeft: 1, borderColor: 'divider' }}><VisibilityOutlinedIcon fontSize="small" /></IconButton></Tooltip>
                    </Stack>
                </Box>}
                <Box sx={{ width: '100%', height: '100%', overflow: 'auto' }}>
                    {previewContent.isPending && previewId
                        ? <Box sx={{ minHeight: 420, display: 'grid', placeItems: 'center' }}><CircularProgress /></Box>
                        : previewContent.url
                            ? <Box sx={{ width: `${zoom * 100}%`, height: `${zoom * 100}%`, minWidth: '100%', minHeight: 420, mx: 'auto', display: 'grid', placeItems: 'center' }}><Box component="img" src={previewContent.url} alt={t('documents.pageWorkspace.preview')} sx={{ display: 'block', width: '100%', height: '100%', objectFit: 'contain' }} /></Box>
                            : <Box sx={{ minHeight: 420, display: 'grid', placeItems: 'center' }}><Typography color="text.secondary">{t('documents.pageWorkspace.noPreview')}</Typography></Box>}
                </Box>
                {previewContent.url && <ImageViewerDialog open={imagePreviewOpen} src={previewContent.url} alt={t('documents.pageWorkspace.preview')} onClose={() => setImagePreviewOpen(false)} />}
            </Paper>}
                second={<Box sx={{ height: { md: 'calc(100dvh - 230px)' }, minHeight: { xs: 720, md: 640 }, display: 'grid', gridTemplateRows: { md: !textWorkspace ? 'minmax(0, 1fr)' : inspectorOpen ? 'minmax(360px, 1fr) minmax(210px, .72fr)' : 'minmax(0, 1fr) auto' }, gap: 1, overflow: 'hidden' }}>
                <Paper variant="outlined" sx={{ minHeight: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ px: 1.5, borderBottom: 1, borderColor: 'divider', alignItems: { sm: 'center' }, justifyContent: 'space-between' }}>
                        <Tabs value={workspaceTab} onChange={(_, value) => setWorkspaceTab(value)} sx={{ minHeight: 48, '& .MuiTab-root': { minHeight: 48 } }}>
                            <Tab value="edit" label={t('documents.pageReview.workspaceTabs.edit')} />
                            <Tab value="details" label={t('documents.pageReview.workspaceTabs.details')} />
                            <Tab value="figures" label={t('documents.pageReview.workspaceTabs.figures')} />
                        </Tabs>
                    </Stack>
                    {workspaceTab === 'edit' && <Stack sx={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
                        <Tabs value={editTab} onChange={(_, value) => setEditTab(value)} sx={{ px: 1.5, minHeight: 40, borderBottom: 1, borderColor: 'divider', '& .MuiTab-root': { minHeight: 40, py: .75, fontSize: '.8rem' } }}>
                            <Tab value="editor" label={t('documents.pageReview.editTabs.editor')} />
                            <Tab value="compare" label={t('documents.pageReview.editTabs.compare')} />
                        </Tabs>
                        {editTab === 'editor' && <Stack spacing={1.5} sx={{ flex: 1, minHeight: 0, p: 1.5, overflow: 'auto' }}>
                            {ocrQuality && <Stack direction="row" spacing={2} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap', flexShrink: 0 }}>
                                <Tooltip title={t('documents.qualityExplanation')}>
                                    <Button variant="outlined" onClick={() => { setSupportTab('checks'); setInspectorOpen(true) }}>
                                        {t('documents.pageReview.tabs.checks')}
                                    </Button>
                                </Tooltip>
                                <QualityResultSummary compact quality={{ assessmentId: ocrQuality.id, score: ocrQuality.overallScore, percentage: ocrQuality.percentage, qualityLevel: ocrQuality.qualityStatus, passedChecks: ocrQuality.passedChecks.length, failedChecks: ocrQuality.failedChecks.length, assessedAt: ocrQuality.createdAt }} />
                                <Typography variant="body2" color="text.secondary">{t('documents.quality.passed', { count: ocrQuality.passedChecks.length })} · {t('documents.quality.failed', { count: ocrQuality.failedChecks.length })}</Typography>
                            </Stack>}
                            <Box sx={{ height: { xs: 420, md: '58%' }, minHeight: 320, maxHeight: 620, flexShrink: 0 }}>
                                <TextField inputRef={editorRef} multiline fullWidth label={t('documents.pageWorkspace.correctedText')} value={displayedText} onChange={event => setDraft(event.target.value)} onKeyDown={event => {
                                    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 's') {
                                        event.preventDefault()
                                        if (canProcess && dirty && pending === null && displayedText.trim()) void runSave()
                                    }
                                }} disabled={pending !== null} helperText={dirty ? t('documents.pageWorkspace.unsaved') : t('documents.pageWorkspace.editHelp')} sx={{ height: '100%', '& .MuiInputBase-root': { alignItems: 'flex-start', height: 'calc(100% - 24px)' }, '& textarea': { height: '100% !important', overflow: 'auto !important' } }} />
                            </Box>
                            {canProcess && <Box sx={{ display: 'flex', justifyContent: 'flex-end', flexShrink: 0 }}><Button variant="contained" startIcon={<SaveOutlinedIcon />} onClick={() => void runSave()} disabled={pending !== null || !dirty || displayedText.trim() === ''}>{t('documents.pageWorkspace.save')}</Button></Box>}
                            {dirty && summary?.transcriptionApprovalState === 'APPROVED' && <Alert severity="warning">{t('documents.pageReview.approvedChangeWarning')}</Alert>}
                            {!detail.rawOcrText && <Alert severity="info">{t('documents.pageWorkspace.noOcr')}</Alert>}
                            <Divider />
                            {pageId !== null && <Box sx={{ flex: 1, minHeight: 220, overflow: 'auto', pr: .5 }}>
                                <VisionAssessmentPanel
                                    documentId={documentId}
                                    pageId={pageId}
                                    workflow={workflowQuery.data}
                                    visionQuality={visionQuality}
                                    canProcess={canProcess}
                                    canApplySuggestions={canProcess || canReview}
                                    hasUnsavedChanges={dirty}
                                    currentText={detail.rawOcrText ?? ''}
                                    editableText={displayedText}
                                    onEditableTextChange={setDraft}
                                    onOpenTextEditor={openTextEditor}
                                />
                            </Box>}
                        </Stack>}
                        {editTab === 'compare' && <Stack spacing={1} sx={{ flex: 1, minHeight: 0, p: 1.5, overflow: 'hidden' }}>
                            <Box sx={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
                                {detail.rawOcrText && <SynchronizedTextDiff
                                    original={detail.rawOcrText}
                                    suggested={displayedText}
                                    originalTitle={t('documents.pageReview.comparisonSource.originalOcr')}
                                    suggestedTitle={t('documents.pageReview.comparisonSource.correctedText')}
                                    synchronizedLabel={t('documents.pageReview.unifiedComparison')}
                                    changesLabel={count => t('documents.vision.changedParts', { count })}
                                    copySuggestedLabel={t('documents.pageReview.copySuggested')}
                                    copiedSuggestedLabel={t('documents.pageReview.copiedSuggested')}
                                    fillAvailable
                                    layout="unified"
                                />}
                                {!detail.rawOcrText && <Alert severity="info">{t('documents.pageWorkspace.noOcr')}</Alert>}
                            </Box>
                        </Stack>}
                    </Stack>}
                    {workspaceTab === 'details' && <Stack spacing={2.5} sx={{ flex: 1, minHeight: 0, p: 2, overflow: 'auto' }}>
                        <Box>
                            <Typography variant="h6" sx={{ fontWeight: 700, mb: 1.5 }}>{t('documents.pageReview.pageIdentity')}</Typography>
                            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'flex-start' } }}>
                                <TextField fullWidth size="small" label={t('documents.pageReview.printedPageNumber')} value={printedPageNumber} onChange={event => setPrintedPageNumber(event.target.value.slice(0, 50))} disabled={!canProcess || pending !== null} helperText={t('documents.pageReview.printedPageHelp')} />
                            </Stack>
                        </Box>
                        <Divider />
                        <Box>
                            <Typography variant="h6" sx={{ fontWeight: 700, mb: 1.5 }}>{t('documents.pageReview.provenanceTitle')}</Typography>
                            <Stack spacing={1.5}>
                                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'flex-start' } }}>
                                    <Autocomplete fullWidth size="small"
                                        options={(sourceOptionsQuery.data?.references ?? []).filter(reference => !documentQuery.data?.summary.sourceId || reference.sourceId === documentQuery.data.summary.sourceId || String(reference.id) === sourceReferenceId)}
                                        value={(sourceOptionsQuery.data?.references ?? []).find(reference => String(reference.id) === sourceReferenceId) ?? null}
                                        getOptionLabel={reference => sourceReferenceLabel(reference, sourceOptionsQuery.data?.sources ?? [])}
                                        isOptionEqualToValue={(option, value) => option.id === value.id}
                                        onChange={(_, reference) => setSourceReferenceId(reference ? String(reference.id) : '')}
                                        disabled={!canReview || pending !== null || sourceOptionsQuery.isPending}
                                        renderInput={params => <TextField {...params} label={t('documents.pageReview.sourceReference')} helperText={detail.summary.sourceReferenceId === null && Boolean(sourceReferenceId) ? t('documents.pageReview.sourceInheritedHelp') : t('documents.pageReview.sourceReferenceHelp')} />}
                                    />
                                    {canReview && <Button variant="outlined" startIcon={<AddIcon />} onClick={() => setSourceReferenceCreateOpen(true)} disabled={pending !== null || sourceOptionsQuery.isPending} sx={{ whiteSpace: 'nowrap' }}>{t('curator.source.createCitation')}</Button>}
                                </Stack>
                                {canReview && <TextField fullWidth size="small" required label={t('documents.pageReview.sourceReason')} value={sourceReason} onChange={event => setSourceReason(event.target.value.slice(0, 1000))} disabled={pending !== null} />}
                                <Divider />
                                <TextField select fullWidth size="small" label={t('documents.pageReview.provenanceTrust')} value={provenanceTrust} onChange={event => setProvenanceTrust(event.target.value as ProvenanceTrustState)} disabled={!canReview || pending !== null}>
                                    {(['UNKNOWN', 'UNTRUSTED', 'PARTIAL', 'TRUSTED', 'VERIFIED'] as ProvenanceTrustState[]).map(value => <MenuItem key={value} value={value}>{t(`documents.pageReview.trust.${value}`)}</MenuItem>)}
                                </TextField>
                                {canReview && <TextField fullWidth size="small" required multiline minRows={2} label={t('documents.pageReview.provenanceReason')} value={provenanceReason} onChange={event => setProvenanceReason(event.target.value.slice(0, 1000))} disabled={pending !== null} />}
                                {(canReview || canProcess) && <Button variant="contained" startIcon={<SaveOutlinedIcon />} onClick={() => void runDetailsSave()} disabled={pending !== null} sx={{ alignSelf: 'flex-end' }}>{t('forms.save')}</Button>}
                            </Stack>
                        </Box>
                    </Stack>}
                    {workspaceTab === 'figures' && pageId !== null && <Box sx={{ flex: 1, minHeight: 0, overflow: 'hidden' }}><DocumentPageFiguresWorkspace documentId={documentId} pageId={pageId} sourceReferenceId={Number(sourceReferenceId) || documentQuery.data?.summary.defaultSourceReferenceId} /></Box>}
                </Paper>
                {textWorkspace && <Paper variant="outlined" sx={{ minHeight: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                    <Stack direction="row" sx={{ alignItems: 'center', borderBottom: inspectorOpen ? 1 : 0, borderColor: 'divider' }}>
                        <Tabs value={supportTab} onChange={(_, value) => { setSupportTab(value); setInspectorOpen(true) }} variant="scrollable" scrollButtons="auto" sx={{ flex: 1, minWidth: 0, minHeight: 40, '& .MuiTab-root': { minHeight: 40, py: .75, fontSize: '.8rem' } }}>
                            <Tab value="checks" label={t('documents.pageReview.tabs.checks')} />
                            <Tab value="visionHistory" label={t('documents.pageReview.history.vision.title')} />
                            <Tab value="ocr" label={t('documents.pageReview.history.ocr.title')} />
                            <Tab value="reviews" label={t('documents.pageReview.history.reviews.title')} />
                            <Tab value="provenance" label={t('documents.pageReview.history.provenance.title')} />
                        </Tabs>
                        <Tooltip title={t(inspectorOpen ? 'documents.pageReview.collapseInspector' : 'documents.pageReview.expandInspector')}>
                            <IconButton aria-label={t(inspectorOpen ? 'documents.pageReview.collapseInspector' : 'documents.pageReview.expandInspector')} onClick={() => setInspectorOpen(value => !value)} sx={{ mr: .5 }}>
                                {inspectorOpen ? <KeyboardArrowDownIcon /> : <KeyboardArrowUpIcon />}
                            </IconButton>
                        </Tooltip>
                    </Stack>
                    {inspectorOpen && <Box sx={{ p: 1.5, minHeight: 0, overflow: 'auto' }}>
                        {supportTab === 'checks' && <Stack spacing={1.5}>
                            <Typography variant="body2" color="text.secondary">{t('documents.qualityExplanation')}</Typography>
                            {qualityQuery.isPending && <CircularProgress size={24} />}
                            {qualityQuery.data?.length === 0 && <Typography color="text.secondary">{t('documents.pageWorkspace.noQuality')}</Typography>}
                            {qualityQuery.data?.map(item => <Stack key={item.id} spacing={1}>
                                <QualityResultSummary quality={{ assessmentId: item.id, score: item.overallScore, percentage: item.percentage, qualityLevel: item.qualityStatus, passedChecks: item.passedChecks.length, failedChecks: item.failedChecks.length, assessedAt: item.createdAt }} />
                                {item.summary && <Typography color="text.secondary">{item.summary}</Typography>}
                            </Stack>)}
                            <Divider />
                            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}>
                                <Box><Typography sx={{ fontWeight: 700 }}>{t('documents.chunkIndexStatus')}</Typography><Typography variant="body2" color="text.secondary">{t('documents.chunkIndexHelp')}</Typography></Box>
                                <DocumentStatusChip kind="indexing" value={detail.summary.indexingState} />
                            </Stack>
                        </Stack>}
                        {supportTab === 'visionHistory' && pageId !== null && <VisionAssessmentHistoryPanel documentId={documentId} pageId={pageId} />}
                        {supportTab !== 'checks' && supportTab !== 'visionHistory' && pageId !== null && <DocumentPageHistoryPanel documentId={documentId} pageId={pageId} kind={supportTab} />}
                    </Box>}
                </Paper>}
            </Box>}
            />
        </Stack>}
        <ConfirmDialog open={confirmAction !== null} title={t(confirmAction === 'reject' ? 'documents.pageReview.rejectConfirm.title' : `documents.pageWorkspace.confirm.${confirmAction}.title`)} confirmLabel={t(confirmAction === 'reject' ? 'documents.pageReview.rejectConfirm.confirm' : `documents.pageWorkspace.confirm.${confirmAction}.confirm`)} confirmColor={confirmAction === 'delete' || confirmAction === 'resetCorrectedText' || confirmAction === 'reject' ? 'error' : 'primary'} pending={pending !== null} confirmDisabled={(confirmAction === 'delete' && !deleteReason.trim()) || (confirmAction === 'reject' && !rejectionReason.trim())} onCancel={() => { setConfirmAction(null); setDeleteReason(''); setRejectionReason('') }} onConfirm={() => void runWorkflowAction()}>
            <Stack spacing={2}>
                <Typography>{t(confirmAction === 'reject' ? 'documents.pageReview.rejectConfirm.description' : `documents.pageWorkspace.confirm.${confirmAction}.description`)}</Typography>
                {confirmAction === 'delete' && <TextField autoFocus required multiline minRows={2} label={t('documents.pageWorkspace.deleteReason')} value={deleteReason} onChange={event => setDeleteReason(event.target.value.slice(0, 500))} />}
                {confirmAction === 'reject' && <TextField autoFocus required multiline minRows={3} label={t('documents.pageReview.rejectionReason')} value={rejectionReason} onChange={event => setRejectionReason(event.target.value.slice(0, 1000))} />}
            </Stack>
        </ConfirmDialog>
        <ConfirmDialog open={confirmUnchangedApproval} title={t('documents.pageReview.unchangedApproval.title')} confirmLabel={t('documents.pageReview.unchangedApproval.confirm')} confirmColor="primary" pending={pending !== null} onCancel={() => setConfirmUnchangedApproval(false)} onConfirm={() => void runApproval()}>{t('documents.pageReview.unchangedApproval.description')}</ConfirmDialog>
        <ConfirmDialog open={unsavedAction !== null} title={t('documents.pageReview.unsavedConfirm.title')} confirmLabel={t('documents.pageReview.unsavedConfirm.confirm')} confirmColor="error" onCancel={() => setUnsavedAction(null)} onConfirm={() => { const action = unsavedAction; setUnsavedAction(null); setDraft(null); if (action) navigateImmediately(action) }}>{t('documents.pageReview.unsavedConfirm.description')}</ConfirmDialog>
    </AdminModal>
    {sourceReferenceCreateOpen && <SourceReferenceCreateDialog
            sources={sourceOptionsQuery.data?.sources ?? []}
            initialSourceId={documentQuery.data?.summary.sourceId}
            onClose={() => setSourceReferenceCreateOpen(false)}
            onCreated={reference => {
                queryClient.setQueryData<{ references: SourceReferenceDetails[]; sources: SourceDetails[] }>(['admin', 'document-page-source-options'], current => current
                    ? { ...current, references: [...current.references.filter(item => item.id !== reference.id), reference] }
                    : current)
                setSourceReferenceId(String(reference.id))
                setSourceReferenceCreateOpen(false)
                void queryClient.invalidateQueries({ queryKey: ['admin', 'source-references'] })
            }}
    />}
    </>
}

function sourceReferenceLabel(reference: SourceReferenceDetails, sources: SourceDetails[]) {
    const source = sources.find(item => item.id === reference.sourceId)
    return [source?.title, reference.chapter, reference.pageFrom ? `с. ${reference.pageFrom}` : null, reference.locator]
        .filter(Boolean)
        .join(' · ') || `#${reference.id}`
}

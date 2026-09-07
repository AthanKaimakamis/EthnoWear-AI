import { useEffect, useState } from 'react'
import { Alert, Box, Button, Checkbox, CircularProgress, Divider, FormControl, IconButton, InputLabel, LinearProgress, MenuItem, Paper, Select, Skeleton, Stack, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, Tabs, TextField, Tooltip, Typography } from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import RateReviewOutlinedIcon from '@mui/icons-material/RateReviewOutlined'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import AutorenewOutlinedIcon from '@mui/icons-material/AutorenewOutlined'
import CloudUploadOutlinedIcon from '@mui/icons-material/CloudUploadOutlined'
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import SaveOutlinedIcon from '@mui/icons-material/SaveOutlined'
import { updateDocumentMetadata } from '../../api/DocumentAdminApi'
import DeleteOutlineOutlinedIcon from '@mui/icons-material/DeleteOutlineOutlined'
import { useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { deleteDocument, documentQueryKeys, getDocument, getDocumentPageWorkflow, listProcessingJobs, listDocumentPages, processingQueryKeys, retireDocumentPage, retryProcessingJob, retryProcessingJobs, uploadDocumentThumbnail } from '../../api/DocumentAdminApi'
import { apiErrorMessage } from '../../api/http'
import { apiEnumLabel } from '../../app/apiEnumLabels'
import { useAdminAuth } from '../../app/adminAuth'
import { administratorRoles, hasAnyRole, processingMutationRoles } from '../../app/permissions'
import DocumentStatusChip from '../../components/admin/document/DocumentStatusChip'
import DocumentPageReviewDialog from '../../components/admin/document/DocumentPageReviewDialog'
import QualityResultSummary from '../../components/admin/document/QualityResultSummary'
import ProcessingJobResultSummary from '../../components/admin/document/ProcessingJobResultSummary'
import { documentJobTypes } from '../../components/admin/document/documentOptions'
import SortableTableCell from '../../components/admin/table/SortableTableCell'
import PdfViewerDialog from '../../components/admin/PdfViewerDialog'
import ConfirmDialog from '../../components/admin/ConfirmDialog'
import PageWorkflowProgress from '../../components/admin/document/PageWorkflowProgress'
import ProcessingJobDetailsDialog from '../../components/admin/document/ProcessingJobDetailsDialog'
import ProcessingJobErrorSummary from '../../components/admin/document/ProcessingJobErrorSummary'
import { canManuallyRetryJob, isAutomaticRetryPending } from '../../components/admin/document/processingJobPresentation'
import DocumentChunkManagement from '../../components/admin/document/DocumentChunkManagement'
import DocumentCleanupPanel from '../../components/admin/document/DocumentCleanupPanel'
import DocumentFiguresPanel from '../../components/admin/document/DocumentFiguresPanel'
import DocumentDefaultSourceReferenceEditor from '../../components/admin/document/DocumentDefaultSourceReferenceEditor'
import type { DocumentJobType, DocumentPageSummary, DocumentProcessingJob, DocumentProgress, DocumentSummary, ProcessingJobResult, ProcessingJobSummary } from '../../types/document'
import AdminMediaThumbnail from '../../components/admin/media/AdminMediaThumbnail'
import { useAdminMediaContent } from '../../components/admin/media/useAdminMediaContent'

type DetailTab = 'overview' | 'pages' | 'figures' | 'jobs' | 'metadata' | 'workflow' | 'chunks' | 'indexing' | 'cleanup'
const pageSorts = ['pageSequence', 'printedPageSort', 'processingState', 'reviewState', 'currentQualityScore'] as const
const jobSorts = ['jobType', 'status', 'attemptCount', 'createdAt'] as const
type PageSort = typeof pageSorts[number]
type JobSort = typeof jobSorts[number]
type SortDirection = 'asc' | 'desc'
type TableState<T> = { page: number; size: number; sort: T; direction: SortDirection }
type JobTableState = TableState<JobSort> & { jobType: DocumentJobType | '' }

export default function DocumentDetailPage() {
    const { t } = useTranslation()
    const navigate = useNavigate()
    const documentId = Number(useParams().documentId)
    const [params, setParams] = useSearchParams()
    const [pdfPreviewOpen, setPdfPreviewOpen] = useState(false)
    const [reviewPageId, setReviewPageId] = useState<number | null>(null)
    const [thumbnailUploading, setThumbnailUploading] = useState(false)
    const [thumbnailError, setThumbnailError] = useState<string | null>(null)
    const [titleDraft, setTitleDraft] = useState<string | null>(null)
    const [savingTitle, setSavingTitle] = useState(false)
    const [deletePageTarget, setDeletePageTarget] = useState<DocumentPageSummary | null>(null)
    const [deletePageReason, setDeletePageReason] = useState('')
    const [deletingPage, setDeletingPage] = useState(false)
    const [deleteDocumentOpen, setDeleteDocumentOpen] = useState(false)
    const [deleteDocumentConfirmation, setDeleteDocumentConfirmation] = useState('')
    const [deletingDocument, setDeletingDocument] = useState(false)
    const [deleteDocumentError, setDeleteDocumentError] = useState<string | null>(null)
    const [pageActionError, setPageActionError] = useState<string | null>(null)
    const [pageNavigationPending, setPageNavigationPending] = useState(false)
    const [overviewJobType, setOverviewJobType] = useState<DocumentJobType | ''>('')
    const [pagesTable, setPagesTable] = useState<TableState<PageSort>>({ page: 0, size: 20, sort: 'pageSequence', direction: 'asc' })
    const [jobsTable, setJobsTable] = useState<JobTableState>({ page: 0, size: 20, sort: 'createdAt', direction: 'desc', jobType: '' })
    const tab = (params.get('tab') ?? 'overview') as DetailTab
    const pageRequest = { page: pagesTable.page, size: pagesTable.size, sort: `${pagesTable.sort},${pagesTable.direction}` }
    const detailQuery = useQuery({ queryKey: documentQueryKeys.detail(documentId), queryFn: ({ signal }) => getDocument(documentId, signal), enabled: Number.isInteger(documentId) && documentId > 0 })
    const pagesQuery = useQuery({ queryKey: documentQueryKeys.pages(documentId, pageRequest), queryFn: ({ signal }) => listDocumentPages(documentId, pageRequest, signal), enabled: tab === 'pages' && documentId > 0 })
    const firstPageQuery = useQuery({ queryKey: documentQueryKeys.pages(documentId, { page: 0, size: 1, sort: 'pageSequence,asc' }), queryFn: ({ signal }) => listDocumentPages(documentId, { page: 0, size: 1, sort: 'pageSequence,asc' }, signal), enabled: documentId > 0 && Boolean(detailQuery.data) && !detailQuery.data?.summary.thumbnailMediaAssetId })
    const jobsQuery = useQuery({ queryKey: processingQueryKeys.jobs({ documentId, jobType: jobsTable.jobType || undefined, page: jobsTable.page, size: jobsTable.size, sort: `${jobsTable.sort},${jobsTable.direction}` }), queryFn: ({ signal }) => listProcessingJobs({ documentId, jobType: jobsTable.jobType || undefined, page: jobsTable.page, size: jobsTable.size, sort: `${jobsTable.sort},${jobsTable.direction}` }, signal), enabled: tab === 'jobs' && documentId > 0 })
    const pageWorkflowQueries = useQueries({ queries: (pagesQuery.data?.content ?? []).map(page => ({ queryKey: documentQueryKeys.pageWorkflow(documentId, page.id), queryFn: ({ signal }: { signal: AbortSignal }) => getDocumentPageWorkflow(documentId, page.id, signal), enabled: tab === 'pages' })) })
    const queryClient = useQueryClient()
    const { admin } = useAdminAuth()
    const canManageProcessing = Boolean(admin && hasAnyRole(admin.roles, processingMutationRoles))
    const isAdministrator = Boolean(admin && hasAnyRole(admin.roles, administratorRoles))

    if (detailQuery.isPending) return <Stack spacing={2}><Skeleton width={280} height={48} /><Skeleton variant="rounded" height={180} /><Skeleton variant="rounded" height={320} /></Stack>
    if (detailQuery.isError) return <Alert severity="error" action={<Button onClick={() => navigate('/management/documents')}>{t('documents.detail.back')}</Button>}>{apiErrorMessage(detailQuery.error, t('documents.loadFailed'))}</Alert>
    const detail = detailQuery.data
    const summary = detail.summary
    const thumbnailId = summary.thumbnailMediaAssetId ?? firstPageQuery.data?.content[0]?.previewMediaAssetId
    async function saveTitle() {
        if (!titleDraft?.trim() || savingTitle) return
        setSavingTitle(true)
        setThumbnailError(null)
        try {
            await updateDocumentMetadata(documentId, { title: titleDraft.trim(), author: summary.author, publisher: summary.publisher, publicationYear: summary.publicationYear, language: summary.language, defaultSourceReferenceId: summary.defaultSourceReferenceId, notes: detail.notes })
            await queryClient.invalidateQueries({ queryKey: documentQueryKeys.lists() })
            await detailQuery.refetch()
            setTitleDraft(null)
        } catch (error) { setThumbnailError(apiErrorMessage(error, t('documents.loadFailed'))) }
        finally { setSavingTitle(false) }
    }
    const visiblePages = pagesQuery.data?.content ?? []
    const reviewPageIndex = visiblePages.findIndex(page => page.id === reviewPageId)
    const hasPreviousReviewPage = reviewPageIndex > 0 || (reviewPageIndex === 0 && pagesTable.page > 0)
    const hasNextReviewPage = reviewPageIndex >= 0 && (reviewPageIndex < visiblePages.length - 1 || (pagesTable.page + 1) * pagesTable.size < (pagesQuery.data?.totalElements ?? 0))
    const refreshJobs = () => { void detailQuery.refetch(); if (tab === 'jobs') void jobsQuery.refetch() }

    function selectTab(value: DetailTab) { setParams(value === 'overview' ? {} : { tab: value }) }
    function changePageSort(property: PageSort) {
        setPagesTable(current => ({ ...current, page: 0, sort: property, direction: property === current.sort && current.direction === 'asc' ? 'desc' : 'asc' }))
    }
    function changeJobSort(property: JobSort) {
        setJobsTable(current => ({ ...current, page: 0, sort: property, direction: property === current.sort && current.direction === 'asc' ? 'desc' : 'asc' }))
    }
    async function selectThumbnail(file?: File) {
        if (!file) return
        setThumbnailUploading(true)
        setThumbnailError(null)
        try {
            await uploadDocumentThumbnail(documentId, file)
            await detailQuery.refetch()
        } catch (caught) {
            setThumbnailError(apiErrorMessage(caught, t('documents.detail.thumbnailFailed')))
        } finally {
            setThumbnailUploading(false)
        }
    }
    async function deletePage() {
        if (!deletePageTarget || deletingPage || !deletePageReason.trim()) return
        setDeletingPage(true)
        setPageActionError(null)
        try {
            await retireDocumentPage(documentId, deletePageTarget.id, deletePageTarget.versionToken, deletePageReason.trim())
            setDeletePageTarget(null)
            setDeletePageReason('')
            await Promise.all([
                queryClient.invalidateQueries({ queryKey: documentQueryKeys.detail(documentId) }),
                queryClient.invalidateQueries({ queryKey: documentQueryKeys.pagesRoot(documentId) }),
            ])
        } catch (caught) {
            setPageActionError(apiErrorMessage(caught, t('documents.detail.deletePageFailed')))
        } finally {
            setDeletingPage(false)
        }
    }
    async function removeDocument() {
        if (deletingDocument || deleteDocumentConfirmation !== summary.title) return
        setDeletingDocument(true)
        setDeleteDocumentError(null)
        try {
            await deleteDocument(documentId)
            queryClient.removeQueries({ queryKey: documentQueryKeys.detail(documentId) })
            await queryClient.invalidateQueries({ queryKey: documentQueryKeys.lists() })
            navigate('/management/documents', { replace: true })
        } catch (caught) {
            setDeleteDocumentError(apiErrorMessage(caught, t('documents.deleteDocument.failed')))
        } finally {
            setDeletingDocument(false)
        }
    }
    async function navigateReviewPage(direction: -1 | 1) {
        if (reviewPageId === null || pageNavigationPending) return
        const currentIndex = visiblePages.findIndex(page => page.id === reviewPageId)
        const adjacent = visiblePages[currentIndex + direction]
        if (adjacent) {
            setReviewPageId(adjacent.id)
            return
        }
        const targetPage = pagesTable.page + direction
        if (targetPage < 0) return
        setPageNavigationPending(true)
        setPageActionError(null)
        try {
            const targetRequest = { ...pageRequest, page: targetPage }
            const result = await queryClient.fetchQuery({
                queryKey: documentQueryKeys.pages(documentId, targetRequest),
                queryFn: ({ signal }) => listDocumentPages(documentId, targetRequest, signal),
            })
            const target = direction > 0 ? result.content[0] : result.content[result.content.length - 1]
            if (!target) return
            setPagesTable(current => ({ ...current, page: targetPage }))
            setReviewPageId(target.id)
        } catch (caught) {
            setPageActionError(apiErrorMessage(caught, t('documents.detail.pageNavigationFailed')))
        } finally {
            setPageNavigationPending(false)
        }
    }
    return <Stack spacing={3}>
        <Box><Button component={Link} to="/management/documents" startIcon={<ArrowBackIcon />}>{t('documents.detail.back')}</Button></Box>
        <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2} sx={{ justifyContent: 'space-between' }}>
            <Box sx={{ minWidth: 0, flex: 1 }}>{titleDraft !== null ? <Stack spacing={1}><TextField fullWidth autoFocus label={t('documents.uploadDialog.titleField')} value={titleDraft} disabled={savingTitle} onChange={event => setTitleDraft(event.target.value)} /><Stack direction="row" spacing={1}><Button variant="contained" startIcon={<SaveOutlinedIcon />} disabled={savingTitle || !titleDraft.trim()} onClick={() => void saveTitle()}>{savingTitle ? t('forms.saving') : t('forms.save')}</Button><Button disabled={savingTitle} onClick={() => setTitleDraft(null)}>{t('admin.cancel')}</Button></Stack></Stack> : <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><Typography component="h1" variant="h4" sx={{ fontWeight: 800, color: 'text.primary', overflowWrap: 'anywhere' }}>{summary.title}</Typography>{canManageProcessing && <IconButton aria-label={t('admin.edit')} onClick={() => setTitleDraft(summary.title)}><EditOutlinedIcon /></IconButton>}</Stack>}<Typography color="text.secondary">{[summary.author, summary.publisher, summary.publicationYear].filter(Boolean).join(' · ')}</Typography></Box>
            <Stack spacing={1} sx={{ alignItems: { lg: 'flex-end' } }}>
                <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}><DocumentStatusChip kind="processing" value={summary.processingState} /><DocumentStatusChip kind="review" value={summary.reviewState} /><DocumentStatusChip kind="trust" value={summary.provenanceTrustState} /><DocumentStatusChip kind="indexing" value={summary.indexingState} /></Stack>
                {isAdministrator && <Button color="error" variant="outlined" startIcon={<DeleteOutlineOutlinedIcon />} onClick={() => setDeleteDocumentOpen(true)}>{t('documents.deleteDocument.action')}</Button>}
            </Stack>
        </Stack>
        {thumbnailError && <Alert severity="error" onClose={() => setThumbnailError(null)} sx={{ whiteSpace: 'pre-line' }}>{thumbnailError}</Alert>}
        {pageActionError && <Alert severity="error" onClose={() => setPageActionError(null)} sx={{ whiteSpace: 'pre-line' }}>{pageActionError}</Alert>}
        <Paper variant="outlined" sx={{ p: 2 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: { sm: 'center' } }}>
                <Box sx={{ width: 112, height: 144, flexShrink: 0, bgcolor: 'grey.100', border: '1px solid', borderColor: 'divider', display: 'grid', placeItems: 'center', overflow: 'hidden' }}>
                    {thumbnailId
                        ? <AdminMediaThumbnail mediaAssetId={thumbnailId} alt={t('documents.detail.thumbnail')} sx={{ width: '100%', height: '100%' }} />
                        : <ImageOutlinedIcon color="disabled" sx={{ fontSize: 42 }} />}
                </Box>
                <Box sx={{ flex: 1 }}>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>{t('documents.detail.thumbnail')}</Typography>
                    <Typography color="text.secondary" sx={{ mb: 1.5 }}>{t('documents.detail.thumbnailHelp')}</Typography>
                    <Button component="label" variant="outlined" startIcon={<CloudUploadOutlinedIcon />} disabled={thumbnailUploading}>
                        {thumbnailUploading ? t('documents.detail.thumbnailUploading') : summary.thumbnailMediaAssetId ? t('documents.detail.changeThumbnail') : t('documents.detail.uploadThumbnail')}
                        <input hidden type="file" accept="image/jpeg,image/png,image/webp" onChange={event => void selectThumbnail(event.target.files?.[0])} />
                    </Button>
                </Box>
                {summary.originalMediaAssetId && <Button variant="outlined" onClick={() => setPdfPreviewOpen(true)} startIcon={<VisibilityOutlinedIcon />} sx={{ alignSelf: { xs: 'stretch', sm: 'flex-start' }, ml: { sm: 'auto' }, flexShrink: 0 }}>{t('pdfViewer.preview')}</Button>}
            </Stack>
        </Paper>
        <Paper variant="outlined" sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'minmax(220px, .8fr) minmax(300px, 1fr) minmax(520px, 1.8fr)' }, overflow: 'hidden' }}>
            <DocumentTabGroup label={t('documents.detail.tabGroups.information')} tabs={[['overview', t('documents.detail.overview')], ['metadata', t('documents.detail.metadata')]]} active={tab} onChange={selectTab} />
            <DocumentTabGroup label={t('documents.detail.tabGroups.document')} tabs={[['pages', t('documents.detail.pages')], ['figures', t('documents.figures.title')], ['chunks', t('documents.chunks.title')]]} active={tab} onChange={selectTab} />
            <DocumentTabGroup label={t('documents.detail.tabGroups.workflow')} tabs={[['workflow', t('documents.pipeline.title')], ['jobs', t('documents.detail.jobs')], ['indexing', t('documents.detail.indexing')], ['cleanup', t('documents.cleanup.shortTitle')]]} active={tab} onChange={selectTab} />
        </Paper>
        {tab === 'overview' && <Overview summary={summary} progress={detail.progress} jobs={detail.recentJobs.items} onRetried={refreshJobs} jobType={overviewJobType} onJobTypeChange={setOverviewJobType} />}
        {tab === 'pages' && <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            {pagesQuery.isPending && <LinearProgress />}
            <TableContainer><Table size="small"><TableHead><TableRow>
                <SortableTableCell active={pagesTable.sort === 'pageSequence'} direction={pagesTable.direction} onClick={() => changePageSort('pageSequence')}>{t('documents.detail.sequence')}</SortableTableCell>
                <SortableTableCell active={pagesTable.sort === 'printedPageSort'} direction={pagesTable.direction} onClick={() => changePageSort('printedPageSort')}>{t('documents.detail.printedPage')}</SortableTableCell>
                <TableCell>{t('documents.detail.pageKind')}</TableCell>
                <TableCell>{t('documents.detail.workflowProgress')}</TableCell>
                <SortableTableCell active={pagesTable.sort === 'processingState'} direction={pagesTable.direction} onClick={() => changePageSort('processingState')}>{t('documents.columns.processing')}</SortableTableCell>
                <SortableTableCell active={pagesTable.sort === 'reviewState'} direction={pagesTable.direction} onClick={() => changePageSort('reviewState')}>{t('documents.columns.review')}</SortableTableCell>
                <SortableTableCell active={pagesTable.sort === 'currentQualityScore'} direction={pagesTable.direction} onClick={() => changePageSort('currentQualityScore')}>{t('documents.detail.quality')}</SortableTableCell>
                <TableCell>{t('documents.columns.indexing')}</TableCell><TableCell />
            </TableRow></TableHead><TableBody>{pagesQuery.data?.content.length === 0
                ? <TableRow><TableCell colSpan={9} align="center" sx={{ py: 7 }}><Typography color="text.secondary">{t('documents.detail.noPages')}</Typography>{summary.processingState === 'PENDING' && <Typography variant="body2" color="text.secondary">{t('documents.detail.extractionWaiting')}</Typography>}</TableCell></TableRow>
                : pagesQuery.data?.content.map((item, index) => <TableRow hover key={item.id} tabIndex={0} aria-label={t('documents.detail.openPageNumber', { page: item.printedPageNumber ?? item.pageLabel ?? item.pageSequence })} onClick={() => setReviewPageId(item.id)} onKeyDown={event => { if (event.target === event.currentTarget && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); setReviewPageId(item.id) } }} sx={{ cursor: 'pointer' }}>
                    <TableCell>{item.pageSequence}</TableCell><TableCell>{item.printedPageNumber ?? item.pageLabel ?? '—'}</TableCell><TableCell>{apiEnumLabel(t, 'pageKind', item.pageKind)}</TableCell>
                    <TableCell><PageWorkflowProgress progress={pageWorkflowQueries[index]?.data} loading={pageWorkflowQueries[index]?.isPending} compact /></TableCell>
                    <TableCell><DocumentStatusChip kind="processing" value={item.processingState} /></TableCell><TableCell><DocumentStatusChip kind="review" value={item.reviewState} /></TableCell><TableCell>{item.quality ? <QualityResultSummary quality={item.quality} compact /> : '—'}</TableCell><TableCell><DocumentStatusChip kind="indexing" value={item.indexingState} /></TableCell>
                    <TableCell align="right"><Stack direction="row" spacing={.5} sx={{ justifyContent: 'flex-end' }}>
                        <Tooltip title={t('documents.detail.openPage')}><IconButton size="small" onClick={event => { event.stopPropagation(); setReviewPageId(item.id) }} aria-label={t('documents.detail.openPage')}><RateReviewOutlinedIcon fontSize="small" /></IconButton></Tooltip>
                        {canManageProcessing && <Tooltip title={t('documents.detail.deletePage')}><IconButton size="small" color="error" onClick={event => { event.stopPropagation(); setDeletePageTarget(item) }} aria-label={t('documents.detail.deletePage')}><DeleteOutlineOutlinedIcon fontSize="small" /></IconButton></Tooltip>}
                    </Stack></TableCell>
                </TableRow>)}</TableBody></Table></TableContainer>
            <TablePagination component="div" count={pagesQuery.data?.totalElements ?? 0} page={pagesTable.page} rowsPerPage={pagesTable.size} rowsPerPageOptions={[20, 50, 100]} onPageChange={(_, value) => setPagesTable(current => ({ ...current, page: value }))} onRowsPerPageChange={event => setPagesTable(current => ({ ...current, page: 0, size: Number(event.target.value) }))} />
        </Paper>}
        {tab === 'jobs' && <JobTable jobs={(jobsQuery.data?.content ?? []).map(processingSummaryRow)} loading={jobsQuery.isPending} onRetried={refreshJobs} jobType={jobsTable.jobType} onJobTypeChange={value => setJobsTable(current => ({ ...current, page: 0, jobType: value }))} sortProperty={jobsTable.sort} sortDirection={jobsTable.direction} onSortChange={changeJobSort} pagination={{ count: jobsQuery.data?.totalElements ?? 0, page: jobsTable.page, size: jobsTable.size, onPageChange: value => setJobsTable(current => ({ ...current, page: value })), onSizeChange: value => setJobsTable(current => ({ ...current, page: 0, size: value })) }} />}
        {tab === 'metadata' && <Paper variant="outlined" sx={{ p: 3 }}><MetadataLine label={t('documents.uploadDialog.titleField')} value={summary.title} /><MetadataLine label={t('documents.uploadDialog.author')} value={summary.author} /><MetadataLine label={t('documents.uploadDialog.publisher')} value={summary.publisher} /><MetadataLine label={t('documents.uploadDialog.year')} value={summary.publicationYear} /><MetadataLine label={t('documents.uploadDialog.language')} value={summary.language?.toUpperCase()} /><Divider sx={{ my: 2 }} /><Typography variant="h6" sx={{ fontWeight: 700 }}>{t('documents.detail.source')}</Typography>{detail.source ? <Stack sx={{ mt: 1 }}><Typography sx={{ fontWeight: 700 }}>{detail.source.title}</Typography><Typography color="text.secondary">{[detail.source.author, detail.source.publisher, detail.source.publicationYear].filter(Boolean).join(' · ')}</Typography></Stack> : <Typography color="text.secondary">{t('documents.detail.noSource')}</Typography>}<Divider sx={{ my: 2 }} /><DocumentDefaultSourceReferenceEditor summary={summary} canEdit={canManageProcessing} /></Paper>}
        {tab === 'workflow' && <WorkflowOverview summary={summary} />}
        {tab === 'figures' && <DocumentFiguresPanel documentId={documentId} defaultSourceReferenceId={summary.defaultSourceReferenceId} />}
        {tab === 'chunks' && <DocumentChunkManagement documentId={documentId} onOpenPage={setReviewPageId} />}
        {tab === 'indexing' && <Paper variant="outlined" sx={{ p: 3 }}><Stack spacing={2}><DocumentStatusChip kind="indexing" value={detail.indexingStatus.documentState} /><Typography variant="h6">{t('documents.detail.chunks', { count: detail.indexingStatus.totalChunks })}</Typography><Alert severity="info">{t('documents.detail.indexingLimited')}</Alert>{Object.entries(detail.indexingStatus.chunkCounts).map(([state, count]) => <MetadataLine key={state} label={apiEnumLabel(t, 'indexingState', state)} value={count} />)}</Stack></Paper>}
        {tab === 'cleanup' && <DocumentCleanupPanel documentId={documentId} canManage={canManageProcessing} />}
        {summary.originalMediaAssetId && <ProtectedDocumentPdfViewer open={pdfPreviewOpen} mediaAssetId={summary.originalMediaAssetId} title={summary.title} onClose={() => setPdfPreviewOpen(false)} />}
        <DocumentPageReviewDialog key={reviewPageId ?? 'closed'} open={reviewPageId !== null} documentId={documentId} pageId={reviewPageId} hasPrevious={hasPreviousReviewPage} hasNext={hasNextReviewPage} navigationPending={pageNavigationPending} onPrevious={() => void navigateReviewPage(-1)} onNext={() => void navigateReviewPage(1)} onClose={() => setReviewPageId(null)} onChanged={() => { void pagesQuery.refetch(); void detailQuery.refetch() }} />
        <ConfirmDialog open={deletePageTarget !== null} title={t('documents.detail.deletePageTitle')} confirmLabel={t('documents.detail.deletePageConfirm')} pending={deletingPage} confirmDisabled={!deletePageReason.trim()} onCancel={() => { setDeletePageTarget(null); setDeletePageReason('') }} onConfirm={() => void deletePage()}>
            <Stack spacing={2}><Typography>{t('documents.detail.deletePageDescription', { page: deletePageTarget?.printedPageNumber ?? deletePageTarget?.pageLabel ?? deletePageTarget?.pageSequence })}</Typography><TextField autoFocus required multiline minRows={2} label={t('documents.detail.deletePageReason')} value={deletePageReason} onChange={event => setDeletePageReason(event.target.value.slice(0, 500))} /></Stack>
        </ConfirmDialog>
        <ConfirmDialog open={deleteDocumentOpen} title={t('documents.deleteDocument.title')} confirmLabel={t('documents.deleteDocument.confirm')} pending={deletingDocument} confirmDisabled={deleteDocumentConfirmation !== summary.title} onCancel={() => { setDeleteDocumentOpen(false); setDeleteDocumentConfirmation(''); setDeleteDocumentError(null) }} onConfirm={() => void removeDocument()}>
            <Stack spacing={2}>
                <Alert severity="error">{t('documents.deleteDocument.description')}</Alert>
                {deleteDocumentError && <Alert severity="error" sx={{ whiteSpace: 'pre-line' }}>{deleteDocumentError}</Alert>}
                <Typography>{t('documents.deleteDocument.help', { title: summary.title })}</Typography>
                <TextField autoFocus required label={t('documents.deleteDocument.confirmation')} value={deleteDocumentConfirmation} onChange={event => setDeleteDocumentConfirmation(event.target.value)} />
            </Stack>
        </ConfirmDialog>
    </Stack>
}

function DocumentTabGroup({ label, tabs, active, onChange }: { label: string; tabs: [DetailTab, string][]; active: DetailTab; onChange: (value: DetailTab) => void }) {
    const selected = tabs.some(([value]) => value === active) ? active : false
    return <Box sx={{ minWidth: 0, px: 1, pt: 1, bgcolor: selected ? 'rgba(157, 15, 42, 0.045)' : 'background.paper', '&:not(:last-of-type)': { borderRight: { md: 1 }, borderBottom: { xs: 1, md: 0 }, borderColor: 'divider' } }}>
        <Typography variant="caption" color="text.secondary" sx={{ px: 1.5, display: 'block', fontWeight: 700, lineHeight: 1.5, textTransform: 'uppercase', letterSpacing: 0 }}>{label}</Typography>
        <Tabs value={selected} onChange={(_, value) => onChange(value)} aria-label={label} variant="scrollable" scrollButtons={false} sx={{ minHeight: 44, '& .MuiTabs-indicator': { height: 3 }, '& .MuiTab-root': { minHeight: 44, minWidth: 0, px: 1.5, py: .75, fontWeight: 700 } }}>
            {tabs.map(([value, tabLabel]) => <Tab key={value} value={value} label={tabLabel} />)}
        </Tabs>
    </Box>
}

function Overview({ summary, progress, jobs, onRetried, jobType, onJobTypeChange }: { summary: DocumentSummary; progress: DocumentProgress; jobs: DocumentProcessingJob[]; onRetried: () => void; jobType: DocumentJobType | ''; onJobTypeChange: (value: DocumentJobType | '') => void }) {
    const { t } = useTranslation()
    const cards = [{ label: t('documents.detail.pages'), value: progress.totalPages }, { label: t('documents.columns.processing'), value: summary.progress.completedProcessingPages }, { label: t('documents.columns.review'), value: summary.progress.reviewRequiredPages }, { label: t('documents.detail.transcription'), value: summary.progress.approvedTranscriptionPages }, { label: t('documents.columns.indexing'), value: summary.progress.indexedPages }]
    return <Stack spacing={3}><Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(5, 1fr)' }, gap: 2 }}>{cards.map(card => <Paper variant="outlined" key={card.label} sx={{ p: 2 }}><Typography variant="h4" sx={{ fontWeight: 800 }}>{card.value}</Typography><Typography color="text.secondary">{card.label}</Typography></Paper>)}</Box><Box><Typography variant="h6" sx={{ mb: 1, fontWeight: 700 }}>{t('documents.detail.recentActivity')}</Typography><JobTable jobs={jobs} onRetried={onRetried} jobType={jobType} onJobTypeChange={onJobTypeChange} /></Box></Stack>
}

function WorkflowOverview({ summary }: { summary: DocumentSummary }) {
    const { t } = useTranslation()
    const stages = [
        { label: t('documents.pipeline.processed'), value: summary.progress.completedProcessingPages },
        { label: t('documents.pipeline.review'), value: summary.progress.reviewRequiredPages },
        { label: t('documents.pipeline.approved'), value: summary.progress.approvedTranscriptionPages },
        { label: t('documents.pipeline.indexed'), value: summary.progress.indexedPages },
    ]
    return <Paper variant="outlined" sx={{ p: 3 }}><Stack spacing={3}>
        <Box><Typography variant="h6" sx={{ fontWeight: 700 }}>{t('documents.pipeline.title')}</Typography><Typography color="text.secondary">{t('documents.pipeline.description')}</Typography></Box>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' }, gap: 2 }}>{stages.map(stage => <Paper key={stage.label} variant="outlined" sx={{ p: 2 }}><Typography variant="h4" sx={{ fontWeight: 800 }}>{stage.value}/{summary.progress.totalPages}</Typography><Typography color="text.secondary">{stage.label}</Typography></Paper>)}</Box>
        {summary.progress.failedProcessingPages > 0 && <Alert severity="error">{t('documents.pipeline.failed', { count: summary.progress.failedProcessingPages })}</Alert>}
        <Alert severity="info">{t('documents.pipeline.independent')}</Alert>
    </Stack></Paper>
}

type JobPagination = { count: number; page: number; size: number; onPageChange: (page: number) => void; onSizeChange: (size: number) => void }
type JobTableRow = Pick<DocumentProcessingJob, 'id' | 'jobType' | 'status' | 'attemptCount' | 'maxAttempts' | 'createdAt' | 'errorCode' | 'capabilities'> & { page?: string | number | null; result?: ProcessingJobResult; details?: ProcessingJobSummary }

function JobTable({ jobs, loading = false, onRetried, pagination, jobType: controlledJobType, onJobTypeChange, sortProperty, sortDirection = 'asc', onSortChange }: { jobs: JobTableRow[]; loading?: boolean; onRetried: () => void; pagination?: JobPagination; jobType?: DocumentJobType | ''; onJobTypeChange?: (value: DocumentJobType | '') => void; sortProperty?: JobSort; sortDirection?: 'asc' | 'desc'; onSortChange?: (property: JobSort) => void }) {
    const { t, i18n } = useTranslation()
    const { admin } = useAdminAuth()
    const queryClient = useQueryClient()
    const canMutate = Boolean(admin && hasAnyRole(admin.roles, processingMutationRoles))
    const [retrying, setRetrying] = useState<number | null>(null)
    const [bulkRetrying, setBulkRetrying] = useState(false)
    const [selected, setSelected] = useState<Set<number>>(new Set())
    const [error, setError] = useState<string | null>(null)
    const [notice, setNotice] = useState<string | null>(null)
    const [retryTarget, setRetryTarget] = useState<JobTableRow | null>(null)
    const [detailTarget, setDetailTarget] = useState<ProcessingJobSummary | null>(null)
    const [localJobType, setLocalJobType] = useState<DocumentJobType | ''>('')
    const jobType = controlledJobType ?? localJobType
    const filteredJobs = jobs.filter(job => !jobType || job.jobType === jobType)
    const rerunnable = filteredJobs.filter(canManuallyRetryJob)
    const allSelected = rerunnable.length > 0 && rerunnable.every(job => selected.has(job.id))
    const someSelected = rerunnable.some(job => selected.has(job.id))

    useEffect(() => {
        setSelected(current => new Set([...current].filter(id => filteredJobs.some(job => job.id === id && canManuallyRetryJob(job)))))
    }, [jobs, jobType])

    async function retry(jobId: number) {
        setRetrying(jobId); setError(null); setNotice(null)
        try {
            await retryProcessingJob(jobId)
            setRetryTarget(null)
            setSelected(current => { const next = new Set(current); next.delete(jobId); return next })
            setNotice(t('documents.detail.retrySucceeded', { count: 1 }))
            await queryClient.invalidateQueries({ queryKey: processingQueryKeys.all })
            onRetried()
        }
        catch (caught) { setError(apiErrorMessage(caught, t('documents.detail.retryFailed'))) }
        finally { setRetrying(null) }
    }

    async function retrySelected() {
        if (!canMutate || selected.size === 0 || bulkRetrying) return
        setBulkRetrying(true); setError(null); setNotice(null)
        try {
            const result = await retryProcessingJobs([...selected])
            setSelected(new Set())
            setNotice(t('documents.detail.retrySucceeded', { count: result.jobs.length }))
            await queryClient.invalidateQueries({ queryKey: processingQueryKeys.all })
            onRetried()
        } catch (caught) {
            setError(apiErrorMessage(caught, t('documents.detail.retryFailed')))
        } finally {
            setBulkRetrying(false)
        }
    }

    function toggleAll() {
        setSelected(allSelected ? new Set() : new Set(rerunnable.slice(0, 100).map(job => job.id)))
    }

    function toggle(jobId: number) {
        setSelected(current => {
            const next = new Set(current)
            if (next.has(jobId)) next.delete(jobId)
            else if (next.size < 100) next.add(jobId)
            return next
        })
    }

    const disabled = retrying !== null || bulkRetrying
    return <><Paper variant="outlined" sx={{ overflow: 'hidden' }}>
        {loading && <LinearProgress />}
        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
        {notice && <Alert severity="success" onClose={() => setNotice(null)}>{notice}</Alert>}
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ p: 1.5, justifyContent: 'space-between' }}>
            <FormControl size="small" sx={{ minWidth: 240 }}><InputLabel>{t('documents.detail.jobTypeFilter')}</InputLabel><Select value={jobType} label={t('documents.detail.jobTypeFilter')} onChange={event => {
                const value = event.target.value as DocumentJobType | ''
                if (onJobTypeChange) onJobTypeChange(value); else setLocalJobType(value)
            }}><MenuItem value="">{t('documents.detail.allJobTypes')}</MenuItem>{documentJobTypes.map(value => <MenuItem key={value} value={value}>{apiEnumLabel(t, 'jobType', value)}</MenuItem>)}</Select></FormControl>
            {canMutate &&
            <Button variant="contained" size="small" startIcon={<AutorenewOutlinedIcon />} disabled={selected.size === 0 || disabled} onClick={() => void retrySelected()}>
                {bulkRetrying ? t('documents.detail.retrying') : t('documents.detail.retrySelected', { count: selected.size })}
            </Button>}
        </Stack>
        <TableContainer><Table size="small"><TableHead><TableRow>
            {canMutate && <TableCell padding="checkbox"><Checkbox checked={allSelected} indeterminate={!allSelected && someSelected} disabled={rerunnable.length === 0 || disabled} onChange={toggleAll} slotProps={{ input: { 'aria-label': t('documents.detail.selectRerunnable') } }} /></TableCell>}
            <TableCell>{t('documents.detail.jobColumns.id')}</TableCell><JobTableHeader property="jobType" active={sortProperty} direction={sortDirection} onSort={onSortChange}>{t('documents.detail.jobColumns.type')}</JobTableHeader><TableCell>{t('documents.detail.jobColumns.page')}</TableCell><JobTableHeader property="status" active={sortProperty} direction={sortDirection} onSort={onSortChange}>{t('documents.detail.jobColumns.status')}</JobTableHeader><JobTableHeader property="attemptCount" active={sortProperty} direction={sortDirection} onSort={onSortChange}>{t('documents.detail.jobColumns.attempts')}</JobTableHeader><TableCell>{t('documents.detail.jobColumns.result')}</TableCell><JobTableHeader property="createdAt" active={sortProperty} direction={sortDirection} onSort={onSortChange}>{t('documents.detail.jobColumns.created')}</JobTableHeader><TableCell>{t('documents.detail.jobColumns.error')}</TableCell><TableCell />
        </TableRow></TableHead><TableBody>{filteredJobs.length === 0
            ? <TableRow><TableCell colSpan={canMutate ? 10 : 9} align="center" sx={{ py: 5 }}>{t('documents.detail.noJobs')}</TableCell></TableRow>
            : filteredJobs.map(job => <TableRow key={job.id}>
                {canMutate && <TableCell padding="checkbox"><Checkbox checked={selected.has(job.id)} disabled={!canManuallyRetryJob(job) || disabled} onChange={() => toggle(job.id)} slotProps={{ input: { 'aria-label': t('documents.detail.selectJob', { id: job.id }) } }} /></TableCell>}
                <TableCell>{job.id}</TableCell><TableCell>{apiEnumLabel(t, 'jobType', job.jobType)}</TableCell><TableCell>{job.page ?? '—'}</TableCell><TableCell><DocumentStatusChip kind="job" value={job.status} /></TableCell><TableCell>{t('curator.processing.attemptCount', { current: job.attemptCount, total: job.maxAttempts })}</TableCell><TableCell><ProcessingJobResultSummary result={job.result} compact /></TableCell><TableCell>{new Intl.DateTimeFormat(i18n.resolvedLanguage ?? 'bg', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(job.createdAt))}</TableCell><TableCell>{isAutomaticRetryPending(job.status) ? t('curator.processing.automaticRetry.noAction') : job.errorCode ? <ProcessingJobErrorSummary errorCode={job.errorCode} compact /> : '—'}</TableCell>
                <TableCell align="right"><Stack direction="row" spacing={.5} sx={{ justifyContent: 'flex-end' }}>{job.details && <Tooltip title={t('curator.processing.details')}><IconButton size="small" onClick={() => job.details && setDetailTarget(job.details)}><VisibilityOutlinedIcon fontSize="small" /></IconButton></Tooltip>}{canMutate && canManuallyRetryJob(job) && <Button size="small" startIcon={<AutorenewOutlinedIcon />} disabled={disabled} onClick={() => setRetryTarget(job)}>{t('documents.detail.rerunJob')}</Button>}</Stack></TableCell>
            </TableRow>)}</TableBody></Table></TableContainer>
        {pagination && <TablePagination component="div" count={pagination.count} page={pagination.page} rowsPerPage={pagination.size} rowsPerPageOptions={[20, 50, 100]} onPageChange={(_, value) => pagination.onPageChange(value)} onRowsPerPageChange={event => pagination.onSizeChange(Number(event.target.value))} />}
    </Paper>
        <ConfirmDialog open={retryTarget !== null} title={t('curator.processing.confirm.retry.title')} confirmLabel={t('curator.processing.confirm.retry.confirm')} confirmColor="primary" pending={retrying !== null} onCancel={() => setRetryTarget(null)} onConfirm={() => retryTarget && void retry(retryTarget.id)}>{t('curator.processing.confirm.retry.description', { id: retryTarget?.id })}</ConfirmDialog>
        <ProcessingJobDetailsDialog job={detailTarget} language={i18n.resolvedLanguage ?? 'bg'} canMutate={canMutate} onClose={() => setDetailTarget(null)} onChanged={() => { setDetailTarget(null); onRetried() }} />
    </>
}

function JobTableHeader({ property, active, direction, onSort, children }: { property: JobSort; active?: JobSort; direction: 'asc' | 'desc'; onSort?: (property: JobSort) => void; children: React.ReactNode }) {
    return onSort
        ? <SortableTableCell active={active === property} direction={direction} onClick={() => onSort(property)}>{children}</SortableTableCell>
        : <TableCell>{children}</TableCell>
}

function ProtectedDocumentPdfViewer({ open, mediaAssetId, title, onClose }: { open: boolean; mediaAssetId: number; title: string; onClose: () => void }) {
    const { t } = useTranslation()
    const content = useAdminMediaContent(open ? mediaAssetId : null)

    if (!open) return null
    if (content.isError) return <Box sx={{ position: 'fixed', inset: 0, zIndex: theme => theme.zIndex.modal + 1, display: 'grid', placeItems: 'center', bgcolor: 'rgba(0, 0, 0, .28)', p: 2 }}><Alert severity="error" action={<Button onClick={onClose}>{t('imageViewer.close')}</Button>}>{apiErrorMessage(content.error, t('documents.loadFailed'))}</Alert></Box>
    if (!content.url) return <Box sx={{ position: 'fixed', inset: 0, zIndex: theme => theme.zIndex.modal + 1, display: 'grid', placeItems: 'center', bgcolor: 'rgba(0, 0, 0, .28)' }}><CircularProgress /></Box>

    return <PdfViewerDialog open source={content.url} title={title} onClose={onClose} />
}

function processingSummaryRow(job: ProcessingJobSummary): JobTableRow {
    return { id: job.id, jobType: job.jobType, status: job.status, attemptCount: job.progress.attemptCount, maxAttempts: job.progress.maxAttempts, createdAt: job.timestamps.createdAt, errorCode: job.error?.code ?? null, page: job.page ? job.page.printedPageNumber ?? job.page.pageLabel ?? job.page.pageSequence : null, result: job.result, capabilities: job.capabilities, details: job }
}

function MetadataLine({ label, value }: { label: string; value: string | number | null | undefined }) { return <Stack direction="row" spacing={2} sx={{ py: 1, justifyContent: 'space-between' }}><Typography color="text.secondary">{label}</Typography><Typography sx={{ fontWeight: 600 }}>{value ?? '—'}</Typography></Stack> }

import { useState } from 'react'
import { Alert, Box, Button, Divider, LinearProgress, Paper, Skeleton, Stack, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, Tabs, Typography } from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import OpenInNewOutlinedIcon from '@mui/icons-material/OpenInNewOutlined'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { documentQueryKeys, getDocument, listDocumentJobs, listDocumentPages } from '../../api/DocumentAdminApi'
import { apiErrorMessage, apiUrl } from '../../api/http'
import DocumentStatusChip from '../../components/admin/document/DocumentStatusChip'
import PdfViewerDialog from '../../components/admin/PdfViewerDialog'
import type { DocumentProcessingJob, DocumentProgress, DocumentSummary } from '../../types/document'

type DetailTab = 'overview' | 'pages' | 'jobs' | 'metadata' | 'indexing'

export default function DocumentDetailPage() {
    const { t } = useTranslation()
    const navigate = useNavigate()
    const documentId = Number(useParams().documentId)
    const [params, setParams] = useSearchParams()
    const [pdfPreviewOpen, setPdfPreviewOpen] = useState(false)
    const tab = (params.get('tab') ?? 'overview') as DetailTab
    const page = Math.max(0, Number(params.get('page') ?? 0) || 0)
    const detailQuery = useQuery({ queryKey: documentQueryKeys.detail(documentId), queryFn: ({ signal }) => getDocument(documentId, signal), enabled: Number.isInteger(documentId) && documentId > 0 })
    const pagesQuery = useQuery({ queryKey: documentQueryKeys.pages(documentId, { page, size: 20 }), queryFn: ({ signal }) => listDocumentPages(documentId, { page, size: 20 }, signal), enabled: tab === 'pages' && documentId > 0 })
    const jobsQuery = useQuery({ queryKey: documentQueryKeys.jobs(documentId, { page, size: 20, sort: 'createdAt,desc' }), queryFn: ({ signal }) => listDocumentJobs(documentId, { page, size: 20, sort: 'createdAt,desc' }, signal), enabled: tab === 'jobs' && documentId > 0 })

    if (detailQuery.isPending) return <Stack spacing={2}><Skeleton width={280} height={48} /><Skeleton variant="rounded" height={180} /><Skeleton variant="rounded" height={320} /></Stack>
    if (detailQuery.isError) return <Alert severity="error" action={<Button onClick={() => navigate('/admin/documents')}>{t('documents.detail.back')}</Button>}>{apiErrorMessage(detailQuery.error, t('documents.loadFailed'))}</Alert>
    const detail = detailQuery.data
    const summary = detail.summary

    function selectTab(value: DetailTab) { setParams(value === 'overview' ? {} : { tab: value }) }
    return <Stack spacing={3}>
        <Box><Button component={Link} to="/admin/documents" startIcon={<ArrowBackIcon />}>{t('documents.detail.back')}</Button></Box>
        <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2} sx={{ justifyContent: 'space-between' }}>
            <Box><Typography component="h1" variant="h4" sx={{ fontWeight: 800 }}>{summary.title}</Typography><Typography color="text.secondary">{[summary.author, summary.publisher, summary.publicationYear].filter(Boolean).join(' · ')}</Typography></Box>
            <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}><DocumentStatusChip kind="processing" value={summary.processingState} /><DocumentStatusChip kind="review" value={summary.reviewState} /><DocumentStatusChip kind="trust" value={summary.provenanceTrustState} /><DocumentStatusChip kind="indexing" value={summary.indexingState} /></Stack>
        </Stack>
        <Paper variant="outlined"><Tabs value={tab} onChange={(_, value) => selectTab(value)} variant="scrollable" scrollButtons="auto" aria-label={summary.title}><Tab value="overview" label={t('documents.detail.overview')} /><Tab value="pages" label={t('documents.detail.pages')} /><Tab value="jobs" label={t('documents.detail.jobs')} /><Tab value="metadata" label={t('documents.detail.metadata')} /><Tab value="indexing" label={t('documents.detail.indexing')} /></Tabs></Paper>
        {tab === 'overview' && <Overview summary={summary} progress={detail.progress} jobs={detail.recentJobs.items} />}
        {tab === 'pages' && <Paper variant="outlined" sx={{ overflow: 'hidden' }}>{pagesQuery.isFetching && <LinearProgress />}<TableContainer><Table size="small"><TableHead><TableRow><TableCell>{t('documents.detail.sequence')}</TableCell><TableCell>{t('documents.detail.printedPage')}</TableCell><TableCell>{t('documents.detail.pageKind')}</TableCell><TableCell>{t('documents.columns.processing')}</TableCell><TableCell>{t('documents.columns.review')}</TableCell><TableCell>{t('documents.detail.ocr')}</TableCell><TableCell>{t('documents.columns.indexing')}</TableCell><TableCell /></TableRow></TableHead><TableBody>{pagesQuery.data?.content.length === 0 ? <TableRow><TableCell colSpan={8} align="center" sx={{ py: 7 }}><Typography color="text.secondary">{t('documents.detail.noPages')}</Typography>{summary.processingState === 'PENDING' && <Typography variant="body2" color="text.secondary">{t('documents.detail.extractionWaiting')}</Typography>}</TableCell></TableRow> : pagesQuery.data?.content.map(item => <TableRow hover key={item.id}><TableCell>{item.pageSequence}</TableCell><TableCell>{item.printedPageNumber ?? item.pageLabel ?? '—'}</TableCell><TableCell>{item.pageKind}</TableCell><TableCell><DocumentStatusChip kind="processing" value={item.processingState} /></TableCell><TableCell><DocumentStatusChip kind="review" value={item.reviewState} /></TableCell><TableCell>{item.hasRawOcrText ? '✓' : '—'}</TableCell><TableCell><DocumentStatusChip kind="indexing" value={item.indexingState} /></TableCell><TableCell align="right"><Button size="small" component={Link} to={`/admin/documents/${documentId}/pages/${item.id}`} endIcon={<OpenInNewOutlinedIcon />}>{t('documents.detail.openPage')}</Button></TableCell></TableRow>)}</TableBody></Table></TableContainer><TablePagination component="div" count={pagesQuery.data?.totalElements ?? 0} page={page} rowsPerPage={20} rowsPerPageOptions={[20]} onPageChange={(_, value) => setParams({ tab: 'pages', page: String(value) })} /></Paper>}
        {tab === 'jobs' && <JobTable jobs={jobsQuery.data?.content ?? []} loading={jobsQuery.isFetching} />}
        {tab === 'metadata' && <Paper variant="outlined" sx={{ p: 3 }}><MetadataLine label={t('documents.uploadDialog.titleField')} value={summary.title} /><MetadataLine label={t('documents.uploadDialog.author')} value={summary.author} /><MetadataLine label={t('documents.uploadDialog.publisher')} value={summary.publisher} /><MetadataLine label={t('documents.uploadDialog.year')} value={summary.publicationYear} /><MetadataLine label={t('documents.uploadDialog.language')} value={summary.language?.toUpperCase()} /><Divider sx={{ my: 2 }} /><Typography variant="h6" sx={{ fontWeight: 700 }}>{t('documents.detail.source')}</Typography>{detail.source ? <Stack sx={{ mt: 1 }}><Typography sx={{ fontWeight: 700 }}>{detail.source.title}</Typography><Typography color="text.secondary">{[detail.source.author, detail.source.publisher, detail.source.publicationYear].filter(Boolean).join(' · ')}</Typography></Stack> : <Typography color="text.secondary">{t('documents.detail.noSource')}</Typography>}</Paper>}
        {tab === 'indexing' && <Paper variant="outlined" sx={{ p: 3 }}><Stack spacing={2}><DocumentStatusChip kind="indexing" value={detail.indexingStatus.documentState} /><Typography variant="h6">{t('documents.detail.chunks', { count: detail.indexingStatus.totalChunks })}</Typography><Alert severity="info">{t('documents.detail.indexingLimited')}</Alert>{Object.entries(detail.indexingStatus.chunkCounts).map(([state, count]) => <MetadataLine key={state} label={state} value={count} />)}</Stack></Paper>}
        {summary.originalMediaAssetId && <Button onClick={() => setPdfPreviewOpen(true)} startIcon={<VisibilityOutlinedIcon />} sx={{ alignSelf: 'flex-start' }}>{t('documents.detail.original')}</Button>}
        {summary.originalMediaAssetId && pdfPreviewOpen && <PdfViewerDialog open source={apiUrl(`/api/media/${summary.originalMediaAssetId}/content`)} title={summary.title} onClose={() => setPdfPreviewOpen(false)} />}
    </Stack>
}

function Overview({ summary, progress, jobs }: { summary: DocumentSummary; progress: DocumentProgress; jobs: DocumentProcessingJob[] }) {
    const { t } = useTranslation()
    const cards = [{ label: t('documents.detail.pages'), value: progress.totalPages }, { label: t('documents.columns.processing'), value: summary.progress.completedProcessingPages }, { label: t('documents.columns.review'), value: summary.progress.reviewRequiredPages }, { label: t('documents.detail.transcription'), value: summary.progress.approvedTranscriptionPages }, { label: t('documents.columns.indexing'), value: summary.progress.indexedPages }]
    return <Stack spacing={3}><Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(5, 1fr)' }, gap: 2 }}>{cards.map(card => <Paper variant="outlined" key={card.label} sx={{ p: 2 }}><Typography variant="h4" sx={{ fontWeight: 800 }}>{card.value}</Typography><Typography color="text.secondary">{card.label}</Typography></Paper>)}</Box><Box><Typography variant="h6" sx={{ mb: 1, fontWeight: 700 }}>{t('documents.detail.recentActivity')}</Typography><JobTable jobs={jobs} /></Box></Stack>
}

function JobTable({ jobs, loading = false }: { jobs: DocumentProcessingJob[]; loading?: boolean }) {
    const { t, i18n } = useTranslation()
    return <Paper variant="outlined" sx={{ overflow: 'hidden' }}>{loading && <LinearProgress />}<TableContainer><Table size="small"><TableHead><TableRow><TableCell>Type</TableCell><TableCell>Status</TableCell><TableCell>Attempts</TableCell><TableCell>Created</TableCell><TableCell>Error</TableCell></TableRow></TableHead><TableBody>{jobs.length === 0 ? <TableRow><TableCell colSpan={5} align="center" sx={{ py: 5 }}>{t('documents.detail.noJobs')}</TableCell></TableRow> : jobs.map(job => <TableRow key={job.id}><TableCell>{job.jobType}</TableCell><TableCell><DocumentStatusChip kind="job" value={job.status} /></TableCell><TableCell>{job.attemptCount}/{job.maxAttempts}</TableCell><TableCell>{new Intl.DateTimeFormat(i18n.resolvedLanguage ?? 'bg', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(job.createdAt))}</TableCell><TableCell>{job.safeErrorMessage ?? '—'}</TableCell></TableRow>)}</TableBody></Table></TableContainer></Paper>
}

function MetadataLine({ label, value }: { label: string; value: string | number | null | undefined }) { return <Stack direction="row" spacing={2} sx={{ py: 1, justifyContent: 'space-between' }}><Typography color="text.secondary">{label}</Typography><Typography sx={{ fontWeight: 600 }}>{value ?? '—'}</Typography></Stack> }

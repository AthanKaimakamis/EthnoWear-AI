import { Alert, Box, Button, Chip, CircularProgress, Divider, LinearProgress, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, Typography } from '@mui/material'
import AutorenewOutlinedIcon from '@mui/icons-material/AutorenewOutlined'
import OpenInNewOutlinedIcon from '@mui/icons-material/OpenInNewOutlined'
import PlaylistAddCheckOutlinedIcon from '@mui/icons-material/PlaylistAddCheckOutlined'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { documentQueryKeys, getChunkGenerationEligibility, listChunkGenerationJobs, listGeneratedChunks, queueDocumentChunkGeneration, retryProcessingJob } from '../../../api/DocumentAdminApi'
import { apiErrorMessage } from '../../../api/http'
import { useAdminAuth } from '../../../app/adminAuth'
import { hasAnyRole, processingMutationRoles } from '../../../app/permissions'
import type { ChunkGenerationBlocker, DocumentProcessingJob } from '../../../types/document'
import ConfirmDialog from '../ConfirmDialog'
import DocumentStatusChip from './DocumentStatusChip'
import ProcessingJobErrorSummary from './ProcessingJobErrorSummary'
import { canManuallyRetryJob, isAutomaticRetryPending } from './processingJobPresentation'

type Props = { documentId: number; onOpenPage: (pageId: number) => void }
const activeStatuses = new Set(['QUEUED', 'CLAIMED', 'RUNNING', 'RETRY_WAIT', 'CANCEL_REQUESTED'])

export default function DocumentChunkManagement({ documentId, onOpenPage }: Props) {
    const { t, i18n } = useTranslation()
    const { admin } = useAdminAuth()
    const queryClient = useQueryClient()
    const canGenerate = Boolean(admin && hasAnyRole(admin.roles, processingMutationRoles))
    const [jobPage, setJobPage] = useState(0)
    const [chunkPage, setChunkPage] = useState(0)
    const [confirmGenerate, setConfirmGenerate] = useState(false)
    const [retryTarget, setRetryTarget] = useState<DocumentProcessingJob | null>(null)
    const [pending, setPending] = useState<'generate' | 'retry' | null>(null)
    const [error, setError] = useState<string | null>(null)
    const jobsRequest = { page: jobPage, size: 10, sort: 'createdAt,desc' }
    const chunksRequest = { page: chunkPage, size: 10, sort: 'chunkOrdinal,asc' }
    const eligibility = useQuery({ queryKey: documentQueryKeys.chunkEligibility(documentId), queryFn: ({ signal }) => getChunkGenerationEligibility(documentId, signal) })
    const jobs = useQuery({
        queryKey: documentQueryKeys.chunkJobs(documentId, jobsRequest),
        queryFn: ({ signal }) => listChunkGenerationJobs(documentId, jobsRequest, signal),
        refetchInterval: query => query.state.data?.content.some(job => activeStatuses.has(job.status)) ? 3000 : false,
    })
    const chunks = useQuery({ queryKey: documentQueryKeys.generatedChunks(documentId, chunksRequest), queryFn: ({ signal }) => listGeneratedChunks(documentId, chunksRequest, signal) })
    const activeJob = jobs.data?.content.some(job => activeStatuses.has(job.status)) ?? false
    const blockers = useMemo(() => groupBlockers(eligibility.data?.blockers ?? []), [eligibility.data?.blockers])

    async function refresh() {
        await Promise.all([
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.chunkEligibility(documentId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.chunkJobsRoot(documentId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.generatedChunksRoot(documentId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.indexing(documentId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.progress(documentId) }),
        ])
    }

    async function generate() {
        if (!canGenerate || pending || !eligibility.data?.eligible || activeJob) return
        setPending('generate'); setError(null)
        try { await queueDocumentChunkGeneration(documentId); setConfirmGenerate(false); await refresh() }
        catch (caught) { setError(apiErrorMessage(caught, t('documents.chunks.actionFailed'))) }
        finally { setPending(null) }
    }

    async function retry() {
        if (!retryTarget || pending) return
        setPending('retry'); setError(null)
        try { await retryProcessingJob(retryTarget.id); setRetryTarget(null); await refresh() }
        catch (caught) { setError(apiErrorMessage(caught, t('documents.chunks.retryFailed'))) }
        finally { setPending(null) }
    }

    const date = (value: string) => new Intl.DateTimeFormat(i18n.resolvedLanguage ?? 'bg', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
    return <Stack spacing={3}>
        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
        <Paper variant="outlined" sx={{ p: 2.5 }}><Stack spacing={2}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
                <Box><Typography variant="h6" sx={{ fontWeight: 800 }}>{t('documents.chunks.eligibility')}</Typography><Typography color="text.secondary">{t('documents.chunks.eligiblePages', { count: eligibility.data?.eligiblePageCount ?? 0 })}</Typography></Box>
                {canGenerate && <Button variant="contained" startIcon={<PlaylistAddCheckOutlinedIcon />} disabled={pending !== null || eligibility.isPending || !eligibility.data?.eligible || activeJob} onClick={() => setConfirmGenerate(true)}>{activeJob ? t('documents.chunks.active') : t('documents.chunks.generate')}</Button>}
            </Stack>
            {eligibility.isPending && <LinearProgress />}
            {eligibility.isError && <Alert severity="error" action={<Button onClick={() => void eligibility.refetch()}>{t('common.retry')}</Button>}>{apiErrorMessage(eligibility.error, t('documents.chunks.loadFailed'))}</Alert>}
            {blockers.map(([pageId, pageBlockers]) => <Alert key={pageId} severity="warning" action={<Button startIcon={<OpenInNewOutlinedIcon />} onClick={() => onOpenPage(pageId)}>{t('documents.chunks.openPage')}</Button>}>
                <Typography sx={{ fontWeight: 700 }}>{t('documents.chunks.pageBlockers', { page: pageId })}</Typography>
                {pageBlockers.map(blocker => <Typography key={blocker.code} variant="body2">{t(`documents.chunks.blockers.${blocker.code}`, { defaultValue: t('documents.chunks.blockers.UNKNOWN') })}</Typography>)}
            </Alert>)}
        </Stack></Paper>

        <Paper variant="outlined" sx={{ overflow: 'hidden' }}><Box sx={{ p: 2 }}><Typography variant="h6" sx={{ fontWeight: 800 }}>{t('documents.chunks.jobs')}</Typography></Box>
            {jobs.isFetching && <LinearProgress />}
            {jobs.isError ? <Alert severity="error">{apiErrorMessage(jobs.error, t('documents.chunks.jobsFailed'))}</Alert> : <TableContainer><Table size="small"><TableHead><TableRow><TableCell>{t('documents.chunks.job')}</TableCell><TableCell>{t('documents.chunks.status')}</TableCell><TableCell>{t('documents.chunks.attempts')}</TableCell><TableCell>{t('documents.chunks.created')}</TableCell><TableCell>{t('documents.chunks.failure')}</TableCell><TableCell /></TableRow></TableHead><TableBody>
                {jobs.data?.content.length === 0 && <TableRow><TableCell colSpan={6} align="center" sx={{ py: 4 }}>{t('documents.chunks.noJobs')}</TableCell></TableRow>}
                {jobs.data?.content.map(job => <TableRow key={job.id}><TableCell>#{job.id}</TableCell><TableCell><DocumentStatusChip kind="job" value={job.status} /></TableCell><TableCell>{t('curator.processing.attemptCount', { current: job.attemptCount, total: job.maxAttempts })}</TableCell><TableCell>{date(job.createdAt)}</TableCell><TableCell>{isAutomaticRetryPending(job.status) ? t('curator.processing.automaticRetry.noAction') : job.errorCode ? <ProcessingJobErrorSummary errorCode={job.errorCode} compact /> : '—'}</TableCell><TableCell align="right">{canGenerate && canManuallyRetryJob(job) && <Button size="small" startIcon={<AutorenewOutlinedIcon />} disabled={pending !== null} onClick={() => setRetryTarget(job)}>{t('common.retry')}</Button>}</TableCell></TableRow>)}
            </TableBody></Table></TableContainer>}
            <TablePagination component="div" count={jobs.data?.totalElements ?? 0} page={jobPage} rowsPerPage={10} rowsPerPageOptions={[10]} onPageChange={(_, value) => setJobPage(value)} />
        </Paper>

        <Stack spacing={2}><Typography variant="h6" sx={{ fontWeight: 800 }}>{t('documents.chunks.generated')}</Typography>
            {chunks.isPending && <Box sx={{ py: 5, display: 'grid', placeItems: 'center' }}><CircularProgress size={28} /></Box>}
            {chunks.isError && <Alert severity="error" action={<Button onClick={() => void chunks.refetch()}>{t('common.retry')}</Button>}>{apiErrorMessage(chunks.error, t('documents.chunks.generatedFailed'))}</Alert>}
            {chunks.data?.content.length === 0 && <Typography color="text.secondary">{t('documents.chunks.empty')}</Typography>}
            {chunks.data?.content.map(chunk => <Paper key={chunk.id} variant="outlined" sx={{ p: 2.5 }}><Stack spacing={2}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}><Typography variant="subtitle1" sx={{ fontWeight: 800 }}>#{chunk.chunkOrdinal}</Typography><DocumentStatusChip kind="indexing" value={chunk.indexingState} />{!chunk.current && <Chip size="small" color="warning" label={chunk.supersededByKnowledgeChunkId ? t('documents.chunks.superseded') : t('documents.chunks.outdated')} />}</Stack>
                <Typography sx={{ whiteSpace: 'pre-wrap' }}>{chunk.content}</Typography><Divider />
                <Typography variant="body2" color="text.secondary">{t('documents.chunks.metadata', { strategy: chunk.chunkingStrategy, version: chunk.chunkingVersion, language: chunk.language ?? '—' })}</Typography>
                <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}><DocumentStatusChip kind="review" value={chunk.reviewState} /><DocumentStatusChip kind="transcription" value={chunk.transcriptionApprovalState} /><DocumentStatusChip kind="trust" value={chunk.provenanceTrustState} /></Stack>
                <TableContainer><Table size="small"><TableHead><TableRow><TableCell>{t('documents.chunks.citationPage')}</TableCell><TableCell>{t('documents.chunks.printedPage')}</TableCell><TableCell>{t('documents.chunks.pdfPage')}</TableCell><TableCell>{t('documents.chunks.offsets')}</TableCell></TableRow></TableHead><TableBody>{[...chunk.citations].sort((a, b) => a.pageOrder - b.pageOrder).map(citation => <TableRow key={`${chunk.id}-${citation.pageOrder}-${citation.documentPageId}`} hover onClick={() => onOpenPage(citation.documentPageId)} sx={{ cursor: 'pointer' }}><TableCell>{citation.label ?? citation.pageOrder}</TableCell><TableCell>{citation.printedPageNumber ?? '—'}</TableCell><TableCell>{citation.pdfPageIndex ?? '—'}</TableCell><TableCell>{citation.startCharOffset}–{citation.endCharOffset}</TableCell></TableRow>)}</TableBody></Table></TableContainer>
            </Stack></Paper>)}
            <TablePagination component="div" count={chunks.data?.totalElements ?? 0} page={chunkPage} rowsPerPage={10} rowsPerPageOptions={[10]} onPageChange={(_, value) => setChunkPage(value)} />
        </Stack>
        <ConfirmDialog open={confirmGenerate} title={t('documents.chunks.confirm.title')} confirmLabel={t('documents.chunks.confirm.confirm')} pending={pending === 'generate'} onCancel={() => setConfirmGenerate(false)} onConfirm={() => void generate()}>{t('documents.chunks.confirm.description', { count: eligibility.data?.eligiblePageCount ?? 0 })}</ConfirmDialog>
        <ConfirmDialog open={retryTarget !== null} title={t('documents.chunks.retryConfirm.title')} confirmLabel={t('documents.chunks.retryConfirm.confirm')} pending={pending === 'retry'} onCancel={() => setRetryTarget(null)} onConfirm={() => void retry()}>{t('documents.chunks.retryConfirm.description', { id: retryTarget?.id })}</ConfirmDialog>
    </Stack>
}

function groupBlockers(blockers: ChunkGenerationBlocker[]) {
    const grouped = new Map<number, ChunkGenerationBlocker[]>()
    blockers.forEach(blocker => grouped.set(blocker.documentPageId, [...(grouped.get(blocker.documentPageId) ?? []), blocker]))
    return [...grouped.entries()]
}

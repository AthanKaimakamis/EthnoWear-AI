import { useEffect, useMemo, useState } from 'react'
import {
    Alert, Button, Checkbox, FormControl, IconButton, InputAdornment, InputLabel, LinearProgress,
    MenuItem, Paper, Select, Skeleton, Stack, Table, TableBody, TableCell,
    TableContainer, TableHead, TablePagination, TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
import AutorenewOutlinedIcon from '@mui/icons-material/AutorenewOutlined'
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined'
import SearchIcon from '@mui/icons-material/Search'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import {
    cancelProcessingJob, getProcessingJobCounts, listProcessingJobs, processingQueryKeys,
    retryProcessingJob, retryProcessingJobs,
} from '../../api/DocumentAdminApi'
import { apiErrorMessage } from '../../api/http'
import { apiEnumLabel } from '../../app/apiEnumLabels'
import { useAdminAuth } from '../../app/adminAuth'
import { hasAnyRole, processingMutationRoles } from '../../app/permissions'
import AdminModal from '../../components/admin/AdminModal'
import AdminPageHeader from '../../components/admin/AdminPageHeader'
import ConfirmDialog from '../../components/admin/ConfirmDialog'
import DocumentStatusChip from '../../components/admin/document/DocumentStatusChip'
import ProcessingJobDetailsDialog from '../../components/admin/document/ProcessingJobDetailsDialog'
import ProcessingJobErrorSummary from '../../components/admin/document/ProcessingJobErrorSummary'
import ProcessingJobResultSummary from '../../components/admin/document/ProcessingJobResultSummary'
import { canManuallyRetryJob, isAutomaticRetryPending } from '../../components/admin/document/processingJobPresentation'
import { documentJobTypes } from '../../components/admin/document/documentOptions'
import SortableTableCell from '../../components/admin/table/SortableTableCell'
import useDebouncedValue from '../../hooks/useDebouncedValue'
import type { DocumentJobStatus, ProcessingJobQuery, ProcessingJobSummary } from '../../types/document'

const pageSizes = [20, 50, 100]
const jobStatuses: DocumentJobStatus[] = ['QUEUED', 'CLAIMED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'RETRY_WAIT', 'CANCEL_REQUESTED', 'CANCELLED', 'TIMED_OUT', 'DEAD']
const processingSorts = ['jobType', 'status', 'attemptCount', 'createdAt'] as const
type ProcessingSort = typeof processingSorts[number]

export default function ProcessingStatusPage() {
    const { t, i18n } = useTranslation()
    const { admin } = useAdminAuth()
    const queryClient = useQueryClient()
    const canMutate = Boolean(admin && hasAnyRole(admin.roles, processingMutationRoles))
    const [params, setParams] = useSearchParams()
    const [search, setSearch] = useState(params.get('search') ?? '')
    const [selected, setSelected] = useState<Set<number>>(new Set())
    const [retrying, setRetrying] = useState(false)
    const [actingJobId, setActingJobId] = useState<number | null>(null)
    const [cancelTarget, setCancelTarget] = useState<ProcessingJobSummary | null>(null)
    const [detailTarget, setDetailTarget] = useState<ProcessingJobSummary | null>(null)
    const [retryTarget, setRetryTarget] = useState<ProcessingJobSummary | null>(null)
    const [cancelReason, setCancelReason] = useState('')
    const [error, setError] = useState<string | null>(null)
    const [notice, setNotice] = useState<string | null>(null)
    const debouncedSearch = useDebouncedValue(search, 350)
    const page = Math.max(0, Number(params.get('page') ?? 0) || 0)
    const size = pageSizes.includes(Number(params.get('size'))) ? Number(params.get('size')) : 20
    const jobType = enumValue(params.get('type'), documentJobTypes)
    const status = enumValue(params.get('status'), jobStatuses)
    const [rawSort, rawDirection] = (params.get('sort') ?? 'createdAt,desc').split(',')
    const sortProperty = enumValue(rawSort, processingSorts) ?? 'createdAt'
    const sortDirection = rawDirection === 'asc' ? 'asc' : 'desc'
    const query = useMemo<ProcessingJobQuery>(() => ({
        page, size, sort: `${sortProperty},${sortDirection}`, searchText: debouncedSearch.trim() || undefined,
        jobType, status,
    }), [debouncedSearch, jobType, page, size, sortDirection, sortProperty, status])
    const countQuery = useMemo<ProcessingJobQuery>(() => ({
        searchText: debouncedSearch.trim() || undefined, jobType, status,
    }), [debouncedSearch, jobType, status])
    const jobsQuery = useQuery({
        queryKey: processingQueryKeys.jobs(query),
        queryFn: ({ signal }) => listProcessingJobs(query, signal),
        placeholderData: keepPreviousData,
    })
    const countsQuery = useQuery({
        queryKey: processingQueryKeys.counts(countQuery),
        queryFn: ({ signal }) => getProcessingJobCounts(countQuery, signal),
    })
    const jobs = jobsQuery.data?.content ?? []
    const visibleRetryable = jobs.filter(canManuallyRetryJob)
    const allVisibleSelected = visibleRetryable.length > 0 && visibleRetryable.every(job => selected.has(job.id))
    const someVisibleSelected = visibleRetryable.some(job => selected.has(job.id))

    useEffect(() => {
        setSelected(current => new Set([...current].filter(id => jobs.some(job => job.id === id && canManuallyRetryJob(job)))))
    }, [jobs])

    function updateParam(key: string, value?: string) {
        setParams(current => {
            const next = new URLSearchParams(current)
            if (value) next.set(key, value); else next.delete(key)
            if (key !== 'page') next.delete('page')
            return next
        })
    }

    function toggleVisible() {
        setSelected(current => {
            const next = new Set(current)
            if (allVisibleSelected) visibleRetryable.forEach(job => next.delete(job.id))
            else visibleRetryable.slice(0, Math.max(0, 100 - next.size)).forEach(job => next.add(job.id))
            return next
        })
    }

    function changeSort(property: ProcessingSort) {
        updateParam('sort', `${property},${property === sortProperty && sortDirection === 'asc' ? 'desc' : 'asc'}`)
    }

    async function retrySelected() {
        if (selected.size === 0 || retrying) return
        setRetrying(true)
        setError(null)
        setNotice(null)
        try {
            const result = await retryProcessingJobs([...selected])
            setSelected(new Set())
            setNotice(t('curator.processing.retrySucceeded', { count: result.jobs.length }))
            await queryClient.invalidateQueries({ queryKey: processingQueryKeys.all })
        } catch (caught) {
            setError(apiErrorMessage(caught, t('curator.processing.retryFailed')))
        } finally {
            setRetrying(false)
        }
    }

    async function retryOne(jobId: number) {
        if (!canMutate || actingJobId !== null) return
        setActingJobId(jobId)
        setError(null)
        setNotice(null)
        try {
            await retryProcessingJob(jobId)
            setRetryTarget(null)
            setSelected(current => { const next = new Set(current); next.delete(jobId); return next })
            setNotice(t('curator.processing.retryOneSucceeded'))
            await queryClient.invalidateQueries({ queryKey: processingQueryKeys.all })
        } catch (caught) {
            setError(apiErrorMessage(caught, t('curator.processing.retryFailed')))
        } finally {
            setActingJobId(null)
        }
    }

    async function cancelSelectedJob() {
        if (!canMutate || !cancelTarget || !cancelReason.trim() || actingJobId !== null) return
        setActingJobId(cancelTarget.id)
        setError(null)
        setNotice(null)
        try {
            await cancelProcessingJob(cancelTarget.id, cancelReason.trim())
            setCancelTarget(null)
            setCancelReason('')
            setNotice(t('curator.processing.cancelSucceeded'))
            await queryClient.invalidateQueries({ queryKey: processingQueryKeys.all })
        } catch (caught) {
            setError(apiErrorMessage(caught, t('curator.processing.cancelFailed')))
        } finally {
            setActingJobId(null)
        }
    }

    return <Stack spacing={3}>
        <AdminPageHeader
            title={t('curator.processing.title')}
            description={t('curator.processing.description')}
            actions={canMutate ? <Button variant="contained" startIcon={<AutorenewOutlinedIcon />} disabled={selected.size === 0 || retrying || actingJobId !== null} onClick={() => void retrySelected()}>{retrying ? t('curator.processing.retrying') : t('curator.processing.retrySelected', { count: selected.size })}</Button> : undefined}
        />
        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
        {notice && <Alert severity="success" onClose={() => setNotice(null)}>{notice}</Alert>}
        {jobsQuery.isError && <Alert severity="error" action={<Button color="inherit" onClick={() => void jobsQuery.refetch()}>{t('errorPage.retry')}</Button>}>{apiErrorMessage(jobsQuery.error, t('curator.processing.loadFailed'))}</Alert>}
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
            <Summary label={t('curator.processing.total')} value={countsQuery.data?.total} loading={countsQuery.isPending} />
            <Summary label={t('curator.processing.active')} value={countsQuery.data?.active} loading={countsQuery.isPending} />
            <Summary label={t('curator.processing.retryable')} value={countsQuery.data?.retryable} loading={countsQuery.isPending} />
        </Stack>
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ p: 2 }}>
                <TextField size="small" value={search} onChange={event => { setSearch(event.target.value); updateParam('search', event.target.value || undefined) }} placeholder={t('curator.processing.search')} sx={{ flex: 1 }} slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
                <FilterSelect group="jobType" label={t('curator.processing.type')} value={jobType ?? ''} options={documentJobTypes} onChange={value => updateParam('type', value)} />
                <FilterSelect group="jobStatus" label={t('curator.processing.status')} value={status ?? ''} options={jobStatuses} onChange={value => updateParam('status', value)} />
            </Stack>
            {jobsQuery.isFetching && !jobsQuery.isPending && <LinearProgress />}
            <TableContainer><Table size="small" aria-label={t('curator.processing.title')}>
                <TableHead><TableRow>
                    {canMutate && <TableCell padding="checkbox"><Checkbox checked={allVisibleSelected} indeterminate={!allVisibleSelected && someVisibleSelected} disabled={visibleRetryable.length === 0 || retrying || actingJobId !== null} onChange={toggleVisible} slotProps={{ input: { 'aria-label': t('curator.processing.selectRetryable') } }} /></TableCell>}
                    <SortableTableCell active={sortProperty === 'jobType'} direction={sortDirection} onClick={() => changeSort('jobType')}>{t('curator.processing.columns.job')}</SortableTableCell>
                    <TableCell>{t('curator.processing.columns.document')}</TableCell>
                    <TableCell>{t('curator.processing.columns.page')}</TableCell>
                    <SortableTableCell active={sortProperty === 'status'} direction={sortDirection} onClick={() => changeSort('status')}>{t('curator.processing.columns.status')}</SortableTableCell>
                    <SortableTableCell active={sortProperty === 'attemptCount'} direction={sortDirection} onClick={() => changeSort('attemptCount')}>{t('curator.processing.columns.attempts')}</SortableTableCell>
                    <TableCell>{t('curator.processing.columns.result')}</TableCell>
                    <SortableTableCell active={sortProperty === 'createdAt'} direction={sortDirection} onClick={() => changeSort('createdAt')}>{t('curator.processing.columns.created')}</SortableTableCell>
                    <TableCell>{t('curator.processing.columns.error')}</TableCell>
                    <TableCell align="right">{t('curator.processing.columns.actions')}</TableCell>
                </TableRow></TableHead>
                <TableBody>{jobsQuery.isPending
                    ? Array.from({ length: 8 }, (_, index) => <JobSkeleton key={index} columns={canMutate ? 10 : 9} />)
                    : jobs.length === 0
                        ? <TableRow><TableCell colSpan={canMutate ? 10 : 9} align="center" sx={{ py: 7, color: 'text.secondary' }}>{t('curator.processing.noJobs')}</TableCell></TableRow>
                        : jobs.map(job => <JobRow key={job.id} job={job} canMutate={canMutate} selected={selected.has(job.id)} disabled={retrying || actingJobId !== null || (!selected.has(job.id) && selected.size >= 100)} acting={actingJobId === job.id} language={i18n.resolvedLanguage ?? 'bg'} onToggle={() => setSelected(current => { const next = new Set(current); if (next.has(job.id)) next.delete(job.id); else next.add(job.id); return next })} onRetry={() => setRetryTarget(job)} onCancel={() => { setCancelTarget(job); setCancelReason('') }} onDetails={() => setDetailTarget(job)} />)}
                </TableBody></Table>
            </TableContainer>
            <TablePagination component="div" count={jobsQuery.data?.totalElements ?? 0} page={page} rowsPerPage={size} rowsPerPageOptions={pageSizes} onPageChange={(_, value) => updateParam('page', String(value))} onRowsPerPageChange={event => updateParam('size', event.target.value)} />
        </Paper>
        <AdminModal
            open={cancelTarget !== null}
            title={t('curator.processing.cancelTitle', { id: cancelTarget?.id })}
            description={t('curator.processing.cancelDescription')}
            onClose={() => { setCancelTarget(null); setCancelReason('') }}
            closeDisabled={actingJobId !== null}
            maxWidth="sm"
            actions={<><Button onClick={() => { setCancelTarget(null); setCancelReason('') }} disabled={actingJobId !== null}>{t('admin.cancel')}</Button><Button color="error" variant="contained" disabled={!cancelReason.trim() || actingJobId !== null} onClick={() => void cancelSelectedJob()}>{t('curator.processing.cancelConfirm')}</Button></>}
        >
            <TextField autoFocus fullWidth multiline minRows={3} label={t('curator.processing.cancelReason')} value={cancelReason} onChange={event => setCancelReason(event.target.value.slice(0, 500))} helperText={`${cancelReason.length}/500`} />
        </AdminModal>
        <ConfirmDialog open={retryTarget !== null} title={t('curator.processing.confirm.retry.title')} confirmLabel={t('curator.processing.confirm.retry.confirm')} confirmColor="primary" pending={actingJobId !== null} onCancel={() => setRetryTarget(null)} onConfirm={() => retryTarget && void retryOne(retryTarget.id)}>{t('curator.processing.confirm.retry.description', { id: retryTarget?.id })}</ConfirmDialog>
        <ProcessingJobDetailsDialog job={detailTarget} language={i18n.resolvedLanguage ?? 'bg'} canMutate={canMutate} onClose={() => setDetailTarget(null)} onChanged={() => { setDetailTarget(null); void jobsQuery.refetch(); void countsQuery.refetch() }} />
    </Stack>
}

function enumValue<T extends string>(value: string | null, options: readonly T[]) {
    return value && options.includes(value as T) ? value as T : undefined
}

function Summary({ label, value, loading }: { label: string; value?: number; loading: boolean }) {
    return <Paper variant="outlined" sx={{ px: 2.5, py: 1.75, minWidth: 150 }}><Typography variant="caption" color="text.secondary">{label}</Typography><Typography variant="h5">{loading ? <Skeleton width={36} /> : value ?? 0}</Typography></Paper>
}

function FilterSelect<T extends string>({ group, label, value, options, onChange }: { group: string; label: string; value: string; options: readonly T[]; onChange: (value?: string) => void }) {
    const { t } = useTranslation()
    return <FormControl size="small" sx={{ minWidth: 210 }}><InputLabel>{label}</InputLabel><Select label={label} value={value} onChange={event => onChange(event.target.value || undefined)}><MenuItem value=""><em>{t('documents.filter.all')}</em></MenuItem>{options.map(option => <MenuItem key={option} value={option}>{apiEnumLabel(t, group, option)}</MenuItem>)}</Select></FormControl>
}

function JobRow({ job, canMutate, selected, disabled, acting, language, onToggle, onRetry, onCancel, onDetails }: { job: ProcessingJobSummary; canMutate: boolean; selected: boolean; disabled: boolean; acting: boolean; language: string; onToggle: () => void; onRetry: () => void; onCancel: () => void; onDetails: () => void }) {
    const { t } = useTranslation()
    return <TableRow hover selected={selected}>
        {canMutate && <TableCell padding="checkbox"><Checkbox checked={selected} disabled={!canManuallyRetryJob(job) || disabled} onChange={onToggle} slotProps={{ input: { 'aria-label': t('curator.processing.selectJob', { id: job.id }) } }} /></TableCell>}
        <TableCell><Typography sx={{ fontWeight: 700 }}>{apiEnumLabel(t, 'jobType', job.jobType)}</Typography><Typography variant="caption" color="text.secondary">#{job.id}</Typography></TableCell>
        <TableCell><Button component={Link} to={`/management/documents/${job.document.id}`} size="small" sx={{ justifyContent: 'flex-start', textTransform: 'none' }}>{job.document.title}</Button></TableCell>
        <TableCell>{job.page ? job.page.printedPageNumber ?? job.page.pageLabel ?? job.page.pageSequence : '—'}</TableCell>
        <TableCell><DocumentStatusChip kind="job" value={job.status} /></TableCell>
        <TableCell>{t('curator.processing.attemptCount', { current: job.progress.attemptCount, total: job.progress.maxAttempts })}</TableCell>
        <TableCell><ProcessingJobResultSummary result={job.result} compact /></TableCell>
        <TableCell>{new Intl.DateTimeFormat(language, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(job.timestamps.createdAt))}</TableCell>
        <TableCell sx={{ maxWidth: 320 }}>{isAutomaticRetryPending(job.status)
            ? <Typography variant="body2" color="text.secondary">{t('curator.processing.automaticRetry.noAction')}</Typography>
            : job.error ? <ProcessingJobErrorSummary errorCode={job.error.code} compact /> : '—'}</TableCell>
        <TableCell align="right"><Stack direction="row" spacing={.5} sx={{ justifyContent: 'flex-end' }}>
            <Tooltip title={t('curator.processing.details')}><IconButton size="small" onClick={onDetails}><VisibilityOutlinedIcon fontSize="small" /></IconButton></Tooltip>
            {canMutate && canManuallyRetryJob(job) && <Tooltip title={t('curator.processing.retryJob')}><span><IconButton size="small" disabled={disabled || acting} onClick={onRetry}><AutorenewOutlinedIcon fontSize="small" /></IconButton></span></Tooltip>}
            {canMutate && job.capabilities.cancellable && <Tooltip title={t('curator.processing.cancelJob')}><span><IconButton size="small" color="error" disabled={disabled || acting} onClick={onCancel}><CancelOutlinedIcon fontSize="small" /></IconButton></span></Tooltip>}
        </Stack></TableCell>
    </TableRow>
}

function JobSkeleton({ columns }: { columns: number }) {
    return <TableRow>{Array.from({ length: columns }, (_, index) => <TableCell key={index}><Skeleton width={index < 3 ? 150 : 90} /></TableCell>)}</TableRow>
}

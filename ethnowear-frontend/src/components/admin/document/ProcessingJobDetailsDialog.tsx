import { useState } from 'react'
import { Alert, Box, Button, Divider, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from '@mui/material'
import AutorenewOutlinedIcon from '@mui/icons-material/AutorenewOutlined'
import AddTaskOutlinedIcon from '@mui/icons-material/AddTaskOutlined'
import DeleteOutlineOutlinedIcon from '@mui/icons-material/DeleteOutlineOutlined'
import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createReplacementProcessingJob, processingQueryKeys, retireProcessingJob, retryProcessingJob } from '../../../api/DocumentAdminApi'
import { apiErrorMessage } from '../../../api/http'
import { apiEnumLabel } from '../../../app/apiEnumLabels'
import type { ProcessingJobSummary } from '../../../types/document'
import AdminModal from '../AdminModal'
import ConfirmDialog from '../ConfirmDialog'
import DocumentStatusChip from './DocumentStatusChip'
import ProcessingJobErrorSummary from './ProcessingJobErrorSummary'
import ProcessingJobResultSummary from './ProcessingJobResultSummary'
import { canCreateReplacementJob, canManuallyRetryJob, isAutomaticRetryPending } from './processingJobPresentation'

type Action = 'retry' | 'replace' | 'delete'

type Props = {
    job: ProcessingJobSummary | null
    language: string
    canMutate: boolean
    onClose: () => void
    onChanged: () => void
}

export default function ProcessingJobDetailsDialog({ job, language, canMutate, onClose, onChanged }: Props) {
    const { t } = useTranslation()
    const queryClient = useQueryClient()
    const [action, setAction] = useState<Action | null>(null)
    const [reason, setReason] = useState('')
    const [pending, setPending] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [notice, setNotice] = useState<string | null>(null)
    const formatDate = (value: string | null) => value ? new Intl.DateTimeFormat(language, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'

    function close() {
        if (pending) return
        setAction(null); setReason(''); setError(null); setNotice(null); onClose()
    }

    async function confirm() {
        if (!job || !action) return
        if ((action === 'replace' || action === 'delete') && !job.versionToken) return
        const completedAction = action
        setPending(true); setError(null); setNotice(null)
        try {
            if (completedAction === 'retry') await retryProcessingJob(job.id)
            if (completedAction === 'replace' && job.versionToken) await createReplacementProcessingJob(job.id, job.versionToken)
            if (completedAction === 'delete' && job.versionToken) await retireProcessingJob(job.id, job.versionToken, reason.trim())
            setNotice(t(`curator.processing.actionSuccess.${completedAction}`))
            setAction(null); setReason('')
            await queryClient.invalidateQueries({ queryKey: processingQueryKeys.all })
            onChanged()
            if (completedAction === 'delete') onClose()
        } catch (caught) {
            setError(apiErrorMessage(caught, t(`curator.processing.actionFailed.${completedAction}`)))
            setAction(null)
        } finally {
            setPending(false)
        }
    }

    const canRetry = job ? canManuallyRetryJob(job) : false
    const canReplace = job ? canCreateReplacementJob(job) : false
    const actions = job && canMutate ? <>
        <Button onClick={close} disabled={pending}>{t('curator.actions.close')}</Button>
        <Box sx={{ flex: 1 }} />
        {canRetry && <Button startIcon={<AutorenewOutlinedIcon />} onClick={() => setAction('retry')} disabled={pending}>{t('curator.processing.retryJob')}</Button>}
        {canReplace && <Button startIcon={<AddTaskOutlinedIcon />} onClick={() => setAction('replace')} disabled={pending}>{t('curator.processing.createReplacement')}</Button>}
        {job.capabilities.deletable && <Button color="error" startIcon={<DeleteOutlineOutlinedIcon />} onClick={() => setAction('delete')} disabled={pending}>{t('curator.processing.deleteJob')}</Button>}
    </> : <Button onClick={close}>{t('curator.actions.close')}</Button>

    return <>
        <AdminModal open={job !== null} title={job ? t('curator.processing.detailsTitle', { id: job.id }) : ''} description={job ? apiEnumLabel(t, 'jobType', job.purpose.type) : undefined} onClose={close} closeDisabled={pending} maxWidth="md" actions={actions}>
            {job && <Stack spacing={3}>
                {error && <Alert severity="error" onClose={() => setError(null)} sx={{ whiteSpace: 'pre-line' }}>{error}</Alert>}
                {notice && <Alert severity="success" onClose={() => setNotice(null)}>{notice}</Alert>}
                {isAutomaticRetryPending(job.status) && <Alert severity="info">
                    <Typography sx={{ fontWeight: 700 }}>{t('curator.processing.automaticRetry.title')}</Typography>
                    <Typography variant="body2">{t('curator.processing.automaticRetry.description')}</Typography>
                </Alert>}
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} divider={<Divider orientation="vertical" flexItem />}>
                    <Box><Typography variant="caption" color="text.secondary">{t('curator.processing.columns.document')}</Typography><Typography sx={{ fontWeight: 700 }}>{job.document.title}</Typography></Box>
                    <Box><Typography variant="caption" color="text.secondary">{t('curator.processing.columns.page')}</Typography><Typography sx={{ fontWeight: 700 }}>{job.page ? job.page.printedPageNumber ?? job.page.pageLabel ?? job.page.pageSequence : '—'}</Typography></Box>
                    <Box><Typography variant="caption" color="text.secondary">{t('curator.processing.status')}</Typography><Box sx={{ mt: .5 }}><DocumentStatusChip kind="job" value={job.status} /></Box></Box>
                    <Box><Typography variant="caption" color="text.secondary">{t('curator.processing.columns.attempts')}</Typography><Typography sx={{ fontWeight: 700 }}>{t('curator.processing.attemptCount', { current: job.progress.attemptCount, total: job.progress.maxAttempts })}</Typography></Box>
                </Stack>
                {job.previousJobId && <Alert severity="info">{t('curator.processing.previousJob', { id: job.previousJobId })}</Alert>}
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 1.5 }}>
                    <Detail label={t('curator.processing.errorCode')} value={job.error?.code ?? '—'} />
                    <Detail label={t('curator.processing.errorRetryable')} value={t(job.progress.retryable ? 'common.yes' : 'common.no')} />
                    <Detail label={t('curator.processing.created')} value={formatDate(job.timestamps.createdAt)} />
                    <Detail label={t('curator.processing.available')} value={formatDate(job.timestamps.availableAt)} />
                    <Detail label={t('curator.processing.claimed')} value={formatDate(job.timestamps.claimedAt)} />
                    <Detail label={t('curator.processing.started')} value={formatDate(job.timestamps.startedAt)} />
                    <Detail label={t('curator.processing.finished')} value={formatDate(job.timestamps.finishedAt)} />
                </Box>
                {job.error && <ProcessingJobErrorSummary errorCode={job.error.code} />}
                <Box><Typography variant="h6" sx={{ mb: 1 }}>{t('curator.processing.result')}</Typography><ProcessingJobResultSummary result={job.result} /></Box>
                <Box><Typography variant="h6" sx={{ mb: 1 }}>{t('curator.processing.attemptHistory')}</Typography>
                    {job.attempts.length === 0 ? <Typography color="text.secondary">{t('curator.processing.noAttempts')}</Typography> : <TableContainer component={Paper} variant="outlined"><Table size="small"><TableHead><TableRow><TableCell>{t('curator.processing.execution')}</TableCell><TableCell>{t('curator.processing.columns.status')}</TableCell><TableCell>{t('curator.processing.worker')}</TableCell><TableCell>{t('curator.processing.started')}</TableCell><TableCell>{t('curator.processing.finished')}</TableCell><TableCell>{t('curator.processing.columns.error')}</TableCell></TableRow></TableHead><TableBody>{job.attempts.map(attempt => <TableRow key={attempt.id}><TableCell>{attempt.executionNumber}.{attempt.attemptNumber}</TableCell><TableCell><DocumentStatusChip kind="job" value={attempt.status} /></TableCell><TableCell>{attempt.worker ?? '—'}</TableCell><TableCell>{formatDate(attempt.startedAt)}</TableCell><TableCell>{formatDate(attempt.finishedAt)}</TableCell><TableCell>{attempt.error ? <ProcessingJobErrorSummary errorCode={attempt.error.code} compact /> : '—'}</TableCell></TableRow>)}</TableBody></Table></TableContainer>}
                </Box>
            </Stack>}
        </AdminModal>
        <ConfirmDialog open={action !== null} title={t(`curator.processing.confirm.${action}.title`)} confirmLabel={t(`curator.processing.confirm.${action}.confirm`)} confirmColor={action === 'delete' ? 'error' : 'primary'} pending={pending} confirmDisabled={action === 'delete' && !reason.trim()} onCancel={() => { setAction(null); setReason('') }} onConfirm={() => void confirm()}>
            <Stack spacing={2}>
                <Typography>{t(`curator.processing.confirm.${action}.description`, { id: job?.id })}</Typography>
                {action === 'delete' && <TextField autoFocus required multiline minRows={2} label={t('curator.processing.deleteReason')} value={reason} onChange={event => setReason(event.target.value.slice(0, 500))} />}
            </Stack>
        </ConfirmDialog>
    </>
}

function Detail({ label, value }: { label: string; value: string }) {
    return <Box><Typography variant="caption" color="text.secondary">{label}</Typography><Typography sx={{ fontWeight: 600 }}>{value}</Typography></Box>
}

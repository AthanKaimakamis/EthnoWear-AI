import { useState } from 'react'
import { Alert, Button, CircularProgress, List, ListItem, ListItemText, Paper, Stack, TextField, Typography } from '@mui/material'
import CleaningServicesOutlinedIcon from '@mui/icons-material/CleaningServicesOutlined'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { documentQueryKeys, getMediaCleanupEligibility, scheduleMediaCleanup } from '../../../api/DocumentAdminApi'
import { apiErrorMessage } from '../../../api/http'
import ConfirmDialog from '../ConfirmDialog'

export default function DocumentCleanupPanel({ documentId, canManage }: { documentId: number; canManage: boolean }) {
    const { t } = useTranslation()
    const queryClient = useQueryClient()
    const query = useQuery({ queryKey: documentQueryKeys.cleanupEligibility(documentId), queryFn: ({ signal }) => getMediaCleanupEligibility(documentId, signal) })
    const [days, setDays] = useState(30)
    const [confirm, setConfirm] = useState(false)
    const [pending, setPending] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [notice, setNotice] = useState<string | null>(null)

    async function schedule() {
        setPending(true); setError(null)
        try {
            const result = await scheduleMediaCleanup(documentId, days)
            setConfirm(false)
            setNotice(t('documents.cleanup.scheduled', { count: result.scheduledMediaCount }))
            await Promise.all([
                queryClient.invalidateQueries({ queryKey: documentQueryKeys.cleanupEligibility(documentId) }),
                queryClient.invalidateQueries({ queryKey: documentQueryKeys.jobsRoot(documentId) }),
            ])
        } catch (caught) { setError(apiErrorMessage(caught, t('documents.cleanup.failed'))) }
        finally { setPending(false) }
    }

    if (query.isPending) return <Paper variant="outlined" sx={{ p: 4, display: 'grid', placeItems: 'center' }}><CircularProgress /></Paper>
    if (query.isError) return <Alert severity="error">{apiErrorMessage(query.error, t('documents.cleanup.loadFailed'))}</Alert>
    const eligibility = query.data!
    return <Paper variant="outlined" sx={{ p: 3 }}><Stack spacing={2}>
        <Typography variant="h6" sx={{ fontWeight: 700 }}>{t('documents.cleanup.title')}</Typography>
        <Alert severity="info">{t('documents.cleanup.explanation')}</Alert>
        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
        {notice && <Alert severity="success" onClose={() => setNotice(null)}>{notice}</Alert>}
        <Typography>{t('documents.cleanup.generated', { count: eligibility.generatedMediaCount })}</Typography>
        {!eligibility.eligible && <Alert severity="warning"><Typography sx={{ fontWeight: 700 }}>{t('documents.cleanup.notReady')}</Typography><List dense disablePadding>{eligibility.blockers.map(blocker => <ListItem key={blocker} disableGutters><ListItemText primary={blocker} /></ListItem>)}</List></Alert>}
        {canManage && <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: { sm: 'center' } }}>
            <TextField type="number" size="small" label={t('documents.cleanup.retentionDays')} value={days} onChange={event => setDays(Math.min(3650, Math.max(1, Number(event.target.value) || 1)))} sx={{ width: 180 }} />
            <Button variant="contained" startIcon={<CleaningServicesOutlinedIcon />} disabled={!eligibility.eligible || pending || eligibility.generatedMediaCount === 0} onClick={() => setConfirm(true)}>{t('documents.cleanup.schedule')}</Button>
        </Stack>}
        <ConfirmDialog open={confirm} title={t('documents.cleanup.confirm.title')} confirmLabel={t('documents.cleanup.confirm.confirm')} confirmColor="error" pending={pending} onCancel={() => setConfirm(false)} onConfirm={() => void schedule()}>{t('documents.cleanup.confirm.description', { days })}</ConfirmDialog>
    </Stack></Paper>
}

import { useEffect, useState } from 'react'
import {
    Alert, Box, Button, Chip, CircularProgress, IconButton, Paper, Stack, Table, TableBody,
    TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined'
import DownloadOutlinedIcon from '@mui/icons-material/DownloadOutlined'
import DataObjectOutlinedIcon from '@mui/icons-material/DataObjectOutlined'
import RestoreOutlinedIcon from '@mui/icons-material/RestoreOutlined'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { apiErrorMessage } from '../../api/http'
import {
    getLatestOntologyVersion, getOntologyVersion, getOntologyVersionContent, listOntologyVersions,
    ontologyVersionQueryKeys, restoreOntologyVersion,
} from '../../api/OntologyAdminApi'
import { invalidatePublicQueries } from '../../app/queryClient'
import AdminModal from '../../components/admin/AdminModal'
import AdminPageHeader from '../../components/admin/AdminPageHeader'
import ConfirmDialog from '../../components/admin/ConfirmDialog'
import type { OntologyVersion, OntologyVersionStatus } from '../../types/ontologyAdmin'

const statusColor: Record<OntologyVersionStatus, 'success' | 'warning' | 'error' | 'default'> = {
    ACTIVE: 'success', STAGED: 'warning', FAILED: 'error', SUPERSEDED: 'default',
}

export default function OntologyVersionsPage() {
    const { t, i18n } = useTranslation()
    const queryClient = useQueryClient()
    const [page, setPage] = useState(0)
    const [size, setSize] = useState(20)
    const [selectedId, setSelectedId] = useState<number | null>(null)
    const [restoreTarget, setRestoreTarget] = useState<OntologyVersion | null>(null)
    const [reason, setReason] = useState('')
    const [notice, setNotice] = useState<string | null>(null)
    const [contentError, setContentError] = useState<string | null>(null)
    const [contentTarget, setContentTarget] = useState<OntologyVersion | null>(null)

    const versionsQuery = useQuery({
        queryKey: ontologyVersionQueryKeys.list(page, size),
        queryFn: ({ signal }) => listOntologyVersions({ page, size, sort: 'versionNumber,desc' }, signal),
    })
    const detailQuery = useQuery({
        queryKey: ontologyVersionQueryKeys.detail(selectedId ?? 0),
        queryFn: ({ signal }) => getOntologyVersion(selectedId!, signal),
        enabled: selectedId !== null,
    })
    const latestQuery = useQuery({
        queryKey: ontologyVersionQueryKeys.latest(),
        queryFn: ({ signal }) => getLatestOntologyVersion(signal),
    })
    const contentQuery = useQuery({
        queryKey: ontologyVersionQueryKeys.content(contentTarget?.id ?? 0),
        queryFn: ({ signal }) => getOntologyVersionContent(contentTarget!.id, signal),
        enabled: contentTarget !== null,
    })
    const restoreMutation = useMutation({
        mutationFn: () => restoreOntologyVersion(restoreTarget!.id, reason),
        onSuccess: async restored => {
            setRestoreTarget(null)
            setReason('')
            setSelectedId(restored.id)
            setNotice(t('ontologyVersions.restore.success', { version: restored.versionNumber }))
            await Promise.all([
                queryClient.invalidateQueries({ queryKey: ontologyVersionQueryKeys.all }),
                invalidatePublicQueries(),
            ])
        },
    })

    const dateFormatter = new Intl.DateTimeFormat(i18n.resolvedLanguage === 'en' ? 'en-GB' : 'bg-BG', {
        dateStyle: 'medium', timeStyle: 'short',
    })
    const rows = versionsQuery.data?.content ?? []
    const active = latestQuery.data

    function openRestore(version: OntologyVersion) {
        setReason('')
        restoreMutation.reset()
        setRestoreTarget(version)
    }

    async function downloadVersion(version: OntologyVersion | null, latest = false) {
        if (!version) return
        setContentError(null)
        try {
            const blob = await queryClient.fetchQuery({
                queryKey: ontologyVersionQueryKeys.content(latest ? 'latest' : version.id),
                queryFn: ({ signal }) => getOntologyVersionContent(latest ? 'latest' : version.id, signal),
            })
            const url = URL.createObjectURL(blob)
            const anchor = document.createElement('a')
            anchor.href = url
            anchor.download = version.fileName || `ontology-v${version.versionNumber}.owl`
            document.body.append(anchor)
            anchor.click()
            anchor.remove()
            window.setTimeout(() => URL.revokeObjectURL(url), 0)
        } catch (error) {
            setContentError(apiErrorMessage(error, t('ontologyVersions.errors.content')))
        }
    }

    return <Stack spacing={3}>
        <AdminPageHeader title={t('ontologyVersions.title')} description={t('ontologyVersions.description')} />
        <Alert severity="info">{t('ontologyVersions.contentAvailable')}</Alert>
        {active && <Paper variant="outlined" sx={{ p: 2 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between', gap: 1 }}>
                <Box><Typography variant="overline" color="text.secondary">{t('ontologyVersions.active')}</Typography>
                    <Typography variant="h6">{t('ontologyVersions.version', { version: active.versionNumber })}</Typography>
                    <Typography variant="body2" color="text.secondary">{active.changeReason}</Typography></Box>
                <Stack direction="row" spacing={1}>
                    <Button startIcon={<DataObjectOutlinedIcon />} onClick={() => setContentTarget(active)}>{t('ontologyVersions.preview')}</Button>
                    <Button variant="outlined" startIcon={<DownloadOutlinedIcon />} onClick={() => void downloadVersion(active, true)}>{t('ontologyVersions.downloadLatest')}</Button>
                </Stack>
            </Stack>
        </Paper>}
        {latestQuery.error && <Alert severity="error">{apiErrorMessage(latestQuery.error, t('ontologyVersions.errors.latest'))}</Alert>}
        {contentError && <Alert severity="error" onClose={() => setContentError(null)}>{contentError}</Alert>}
        {versionsQuery.error && <Alert severity="error">{apiErrorMessage(versionsQuery.error, t('ontologyVersions.errors.load'))}</Alert>}
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <TableContainer><Table size="small">
                <TableHead><TableRow>
                    <TableCell>{t('ontologyVersions.columns.version')}</TableCell>
                    <TableCell>{t('ontologyVersions.columns.status')}</TableCell>
                    <TableCell>{t('ontologyVersions.columns.reason')}</TableCell>
                    <TableCell>{t('ontologyVersions.columns.author')}</TableCell>
                    <TableCell>{t('ontologyVersions.columns.created')}</TableCell>
                    <TableCell>{t('ontologyVersions.columns.validation')}</TableCell>
                    <TableCell align="right">{t('admin.columns.actions')}</TableCell>
                </TableRow></TableHead>
                <TableBody>{versionsQuery.isPending
                    ? <TableRow><TableCell colSpan={7} align="center" sx={{ py: 8 }}><CircularProgress size={28} /></TableCell></TableRow>
                    : rows.length === 0
                        ? <TableRow><TableCell colSpan={7} align="center" sx={{ py: 8 }}>{t('ontologyVersions.empty')}</TableCell></TableRow>
                        : rows.map(version => <TableRow key={version.id} hover>
                            <TableCell><Typography sx={{ fontWeight: 800 }}>{t('ontologyVersions.version', { version: version.versionNumber })}</Typography>
                                <Typography variant="caption" color="text.secondary">ID {version.id}</Typography></TableCell>
                            <TableCell><Chip size="small" color={statusColor[version.status]} variant="outlined" label={t(`ontologyVersions.status.${version.status}`)} /></TableCell>
                            <TableCell sx={{ minWidth: 260, maxWidth: 440 }}><Typography variant="body2" sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{version.changeReason}</Typography></TableCell>
                            <TableCell>{version.createdByUsername ?? t('ontologyVersions.system')}</TableCell>
                            <TableCell sx={{ whiteSpace: 'nowrap' }}>{dateFormatter.format(new Date(version.createdAt))}</TableCell>
                            <TableCell><Chip size="small" color={version.valid ? 'success' : 'error'} label={t(version.valid ? 'ontologyVersions.valid' : 'ontologyVersions.invalid')} /></TableCell>
                            <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                                <Tooltip title={t('ontologyVersions.details')}><IconButton size="small" aria-label={t('ontologyVersions.details')} onClick={() => setSelectedId(version.id)}><VisibilityOutlinedIcon fontSize="small" /></IconButton></Tooltip>
                                <Tooltip title={t('ontologyVersions.preview')}><IconButton size="small" aria-label={t('ontologyVersions.preview')} onClick={() => setContentTarget(version)}><DataObjectOutlinedIcon fontSize="small" /></IconButton></Tooltip>
                                <Tooltip title={t('ontologyVersions.download')}><IconButton size="small" aria-label={t('ontologyVersions.download')} onClick={() => void downloadVersion(version)}><DownloadOutlinedIcon fontSize="small" /></IconButton></Tooltip>
                                <Tooltip title={version.status === 'ACTIVE' ? t('ontologyVersions.restore.activeDisabled') : t('ontologyVersions.restore.action')}><span><IconButton size="small" color="warning" aria-label={t('ontologyVersions.restore.action')} disabled={version.status === 'ACTIVE'} onClick={() => openRestore(version)}><RestoreOutlinedIcon fontSize="small" /></IconButton></span></Tooltip>
                            </TableCell>
                        </TableRow>)}</TableBody>
            </Table></TableContainer>
            <TablePagination component="div" count={versionsQuery.data?.totalElements ?? 0} page={page} rowsPerPage={size}
                rowsPerPageOptions={[20, 50, 100]} onPageChange={(_, value) => setPage(value)}
                onRowsPerPageChange={event => { setPage(0); setSize(Number(event.target.value)) }} />
        </Paper>
        <AdminModal open={selectedId !== null} title={detailQuery.data ? t('ontologyVersions.detailsTitle', { version: detailQuery.data.versionNumber }) : t('ontologyVersions.details')}
            description={t('ontologyVersions.detailsDescription')} onClose={() => setSelectedId(null)} maxWidth="md"
            actions={<><Button onClick={() => setSelectedId(null)}>{t('curator.actions.close')}</Button>{detailQuery.data?.status !== 'ACTIVE' && detailQuery.data && <Button color="warning" variant="contained" startIcon={<RestoreOutlinedIcon />} onClick={() => { setSelectedId(null); openRestore(detailQuery.data!) }}>{t('ontologyVersions.restore.action')}</Button>}</>}>
            {detailQuery.isPending ? <Box sx={{ py: 8, textAlign: 'center' }}><CircularProgress /></Box>
                : detailQuery.error ? <Alert severity="error">{apiErrorMessage(detailQuery.error, t('ontologyVersions.errors.details'))}</Alert>
                    : detailQuery.data && <VersionDetails version={detailQuery.data} dateFormatter={dateFormatter} />}
        </AdminModal>
        <AdminModal open={contentTarget !== null} title={contentTarget ? t('ontologyVersions.previewTitle', { version: contentTarget.versionNumber }) : t('ontologyVersions.preview')}
            description={contentTarget?.fileName} onClose={() => setContentTarget(null)} maxWidth="xl" workspace
            actions={<><Button onClick={() => setContentTarget(null)}>{t('curator.actions.close')}</Button><Button variant="contained" startIcon={<DownloadOutlinedIcon />} disabled={!contentTarget} onClick={() => void downloadVersion(contentTarget)}>{t('ontologyVersions.download')}</Button></>}>
            {contentQuery.isPending ? <Box sx={{ py: 8, textAlign: 'center' }}><CircularProgress /></Box>
                : contentQuery.error ? <Alert severity="error">{apiErrorMessage(contentQuery.error, t('ontologyVersions.errors.content'))}</Alert>
                    : contentQuery.data && <OntologyContentPreview blob={contentQuery.data} />}
        </AdminModal>
        <ConfirmDialog open={restoreTarget !== null} title={t('ontologyVersions.restore.title')} pending={restoreMutation.isPending}
            confirmColor="warning" confirmLabel={t('ontologyVersions.restore.confirm')} confirmDisabled={!reason.trim()}
            onCancel={() => { setRestoreTarget(null); setReason(''); restoreMutation.reset() }} onConfirm={() => restoreMutation.mutate()}>
            <Stack spacing={2}>
                <Alert severity="warning" icon={<HistoryOutlinedIcon />}>{t('ontologyVersions.restore.warning', { version: restoreTarget?.versionNumber })}</Alert>
                <Typography>{t('ontologyVersions.restore.explanation')}</Typography>
                <TextField required multiline minRows={3} value={reason} onChange={event => setReason(event.target.value)}
                    label={t('ontologyVersions.restore.reason')} helperText={t('ontologyVersions.restore.reasonHelp')}
                    slotProps={{ htmlInput: { maxLength: 500 } }} />
                {restoreMutation.error && <Alert severity="error">{apiErrorMessage(restoreMutation.error, t('ontologyVersions.errors.restore'))}</Alert>}
            </Stack>
        </ConfirmDialog>
        {notice && <Alert severity="success" onClose={() => setNotice(null)}>{notice}</Alert>}
    </Stack>
}

function VersionDetails({ version, dateFormatter }: { version: OntologyVersion, dateFormatter: Intl.DateTimeFormat }) {
    const { t } = useTranslation()
    const values = [
        ['status', <Chip key="status" size="small" color={statusColor[version.status]} label={t(`ontologyVersions.status.${version.status}`)} />],
        ['created', dateFormatter.format(new Date(version.createdAt))],
        ['author', version.createdByUsername ?? t('ontologyVersions.system')],
        ['reason', version.changeReason],
        ['file', version.fileName],
        ['namespace', version.ontologyNamespace],
        ['hash', version.contentHash],
        ['validation', version.valid ? t('ontologyVersions.valid') : version.validationMessage ?? t('ontologyVersions.invalid')],
        ['previous', version.previousVersionNumber ? t('ontologyVersions.version', { version: version.previousVersionNumber }) : '—'],
        ['restoredFrom', version.restoredFromVersionNumber ? t('ontologyVersions.version', { version: version.restoredFromVersionNumber }) : '—'],
    ] as const
    return <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'minmax(150px, .35fr) 1fr' }, gap: 0, border: 1, borderColor: 'divider' }}>
        {values.map(([key, value]) => <Box key={key} sx={{ display: 'contents' }}>
            <Typography variant="body2" sx={{ p: 1.5, fontWeight: 700, bgcolor: 'action.hover', borderBottom: 1, borderColor: 'divider' }}>{t(`ontologyVersions.fields.${key}`)}</Typography>
            <Box sx={{ p: 1.5, borderBottom: 1, borderColor: 'divider', minWidth: 0 }}><Typography component="div" variant="body2" sx={key === 'hash' || key === 'namespace' ? { fontFamily: 'monospace', overflowWrap: 'anywhere' } : undefined}>{value}</Typography></Box>
        </Box>)}
    </Box>
}

function OntologyContentPreview({ blob }: { blob: Blob }) {
    const { t } = useTranslation()
    const [content, setContent] = useState('')
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        let active = true
        blob.text().then(text => { if (active) setContent(text) }).catch(() => { if (active) setError(t('ontologyVersions.errors.preview')) })
        return () => { active = false }
    }, [blob, t])

    if (error) return <Alert severity="error">{error}</Alert>
    if (!content) return <Box sx={{ py: 8, textAlign: 'center' }}><CircularProgress /></Box>
    return <Box component="pre" sx={{ m: 0, p: 2, width: '100%', height: '100%', minHeight: 0, boxSizing: 'border-box', overflow: 'auto', bgcolor: '#171917', color: '#F4F5F4', borderRadius: 1,
        fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace', fontSize: '.78rem', lineHeight: 1.55, whiteSpace: 'pre', tabSize: 2 }}>
        {content}
    </Box>
}

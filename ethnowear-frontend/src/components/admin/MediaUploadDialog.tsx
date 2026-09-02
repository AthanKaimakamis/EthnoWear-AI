import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Box, Button, Chip, FormControl, FormControlLabel, FormHelperText, IconButton, InputLabel, LinearProgress, MenuItem, Paper, Select, Stack, Switch, TextField, Tooltip, Typography } from '@mui/material'
import AddPhotoAlternateOutlinedIcon from '@mui/icons-material/AddPhotoAlternateOutlined'
import DeleteOutlineOutlinedIcon from '@mui/icons-material/DeleteOutlineOutlined'
import InsertDriveFileOutlinedIcon from '@mui/icons-material/InsertDriveFileOutlined'
import RefreshOutlinedIcon from '@mui/icons-material/RefreshOutlined'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import { mediaAssetsApi, uploadMediaAsset } from '../../api/ArchiveAdminApi'
import type { MediaAssetDetails, MediaAssetWriteDto, MediaType, RightsStatus, SourceDetails, SourceReferenceDetails } from '../../types/archive'
import { apiErrorMessage } from '../../api/http'
import { useTranslation } from 'react-i18next'
import { apiEnumLabel } from '../../app/apiEnumLabels'
import AdminModal from './AdminModal'
import PdfViewerDialog from './PdfViewerDialog'
import RightsGuidance from './media/RightsGuidance'

type Props = { open: boolean; category: 'archive' | 'documents' | 'entities'; sourceReferences: SourceReferenceDetails[]; sources?: SourceDetails[]; sourceReferenceLabel: (reference: SourceReferenceDetails) => string; onClose: () => void; onUploaded: (asset: MediaAssetDetails) => void }
type RowStatus = 'READY' | 'UPLOADING' | 'SAVING_RIGHTS' | 'COMPLETE' | 'FAILED'
type UploadRow = { key: string; file: File; previewUrl: string | null; sourceReferenceId: string; description: string; rightsStatus: RightsStatus; license: string; publicDisplayAllowed: boolean; status: RowStatus; error: string | null; uploadedAsset: MediaAssetDetails | null }
type Defaults = Pick<UploadRow, 'sourceReferenceId' | 'rightsStatus' | 'license' | 'publicDisplayAllowed'>

const rightsStatuses: RightsStatus[] = ['UNKNOWN', 'PUBLIC_DOMAIN', 'LICENSED', 'RESTRICTED']
const activeStatuses: RowStatus[] = ['UPLOADING', 'SAVING_RIGHTS']
const acceptedTypes = 'image/jpeg,image/png,image/gif,image/webp,application/pdf'
const columns = ['preview', 'file', 'description', 'source', 'rights', 'public', 'status', 'actions'] as const

function mediaType(file: File): MediaType { return file.type === 'application/pdf' ? 'PDF' : file.type.startsWith('image/') ? 'IMAGE' : 'OTHER' }
function valid(row: UploadRow) { return row.rightsStatus !== 'LICENSED' || Boolean(row.license.trim()) }

export default function MediaUploadDialog({ open, category, sourceReferences, sources = [], sourceReferenceLabel, onClose, onUploaded }: Props) {
    const { t } = useTranslation()
    const [rows, setRows] = useState<UploadRow[]>([])
    const rowsRef = useRef(rows)
    const [defaults, setDefaults] = useState<Defaults>({ sourceReferenceId: '', rightsStatus: 'UNKNOWN', license: '', publicDisplayAllowed: false })
    const [dragging, setDragging] = useState(false)
    const [running, setRunning] = useState(false)
    const [pdfPreview, setPdfPreview] = useState<UploadRow | null>(null)

    useEffect(() => { rowsRef.current = rows }, [rows])
    useEffect(() => () => { rowsRef.current.forEach(row => { if (row.previewUrl) URL.revokeObjectURL(row.previewUrl) }) }, [])

    const sourceById = useMemo(() => new Map(sources.map(source => [source.id, source])), [sources])
    const referenceById = useMemo(() => new Map(sourceReferences.map(reference => [reference.id, reference])), [sourceReferences])
    const completeCount = rows.filter(row => row.status === 'COMPLETE').length
    const failedCount = rows.filter(row => row.status === 'FAILED').length
    const readyCount = rows.filter(row => (row.status === 'READY' || row.status === 'FAILED') && valid(row)).length
    const invalidCount = rows.filter(row => row.status !== 'COMPLETE' && !valid(row)).length

    function updateRow(key: string, update: Partial<UploadRow>) { setRows(current => current.map(row => row.key === key ? { ...row, ...update } : row)) }

    function addFiles(files: FileList | File[]) {
        const candidates = Array.from(files).filter(file => file.type.startsWith('image/') || file.type === 'application/pdf')
        const existing = new Set(rowsRef.current.map(row => `${row.file.name}:${row.file.size}:${row.file.lastModified}`))
        const added = candidates.filter(file => !existing.has(`${file.name}:${file.size}:${file.lastModified}`)).map((file, index): UploadRow => ({
            key: `${file.name}:${file.size}:${file.lastModified}:${Date.now()}:${index}`, file,
            previewUrl: file.type.startsWith('image/') ? URL.createObjectURL(file) : null,
            sourceReferenceId: defaults.sourceReferenceId, description: '', rightsStatus: defaults.rightsStatus,
            license: defaults.rightsStatus === 'LICENSED' ? defaults.license : '',
            publicDisplayAllowed: defaults.publicDisplayAllowed && (defaults.rightsStatus === 'PUBLIC_DOMAIN' || defaults.rightsStatus === 'LICENSED'),
            status: 'READY', error: null, uploadedAsset: null,
        }))
        if (added.length) setRows(current => [...current, ...added])
    }

    function removeRow(row: UploadRow) {
        if (activeStatuses.includes(row.status)) return
        if (row.previewUrl) URL.revokeObjectURL(row.previewUrl)
        setRows(current => current.filter(candidate => candidate.key !== row.key))
    }

    function changeRights(row: UploadRow, rightsStatus: RightsStatus) {
        updateRow(row.key, { rightsStatus, license: rightsStatus === 'LICENSED' ? row.license : '', publicDisplayAllowed: rightsStatus === 'PUBLIC_DOMAIN' || rightsStatus === 'LICENSED' ? row.publicDisplayAllowed : false, status: row.status === 'FAILED' ? 'READY' : row.status, error: null })
    }

    function applyDefaults() {
        setRows(current => current.map(row => row.status === 'COMPLETE' || activeStatuses.includes(row.status) ? row : { ...row, sourceReferenceId: defaults.sourceReferenceId, rightsStatus: defaults.rightsStatus, license: defaults.rightsStatus === 'LICENSED' ? defaults.license : '', publicDisplayAllowed: defaults.publicDisplayAllowed && (defaults.rightsStatus === 'PUBLIC_DOMAIN' || defaults.rightsStatus === 'LICENSED'), status: 'READY', error: null }))
    }

    function sourceWarning(row: UploadRow) {
        if (!row.publicDisplayAllowed || !row.sourceReferenceId) return false
        const reference = referenceById.get(Number(row.sourceReferenceId))
        const source = reference ? sourceById.get(reference.sourceId) : null
        return !(source?.publicDisplayAllowed && (source.rightsStatus === 'PUBLIC_DOMAIN' || source.rightsStatus === 'LICENSED'))
    }

    async function processRow(row: UploadRow) {
        let asset = row.uploadedAsset
        try {
            if (!asset) {
                updateRow(row.key, { status: 'UPLOADING', error: null })
                asset = await uploadMediaAsset(row.file, { sourceReferenceId: row.sourceReferenceId ? Number(row.sourceReferenceId) : null, mediaType: mediaType(row.file), category, description: row.description.trim() || null })
                updateRow(row.key, { uploadedAsset: asset, status: 'SAVING_RIGHTS' })
            } else updateRow(row.key, { status: 'SAVING_RIGHTS', error: null })
            const updated = await mediaAssetsApi.update(asset.id, { sourceReferenceId: row.sourceReferenceId ? Number(row.sourceReferenceId) : null, description: row.description.trim() || null, rightsStatus: row.rightsStatus, license: row.rightsStatus === 'LICENSED' ? row.license.trim() : null, publicDisplayAllowed: row.publicDisplayAllowed } as MediaAssetWriteDto)
            updateRow(row.key, { uploadedAsset: updated, status: 'COMPLETE', error: null })
            onUploaded(updated)
        } catch (caught) {
            updateRow(row.key, { uploadedAsset: asset, status: 'FAILED', error: apiErrorMessage(caught, asset ? t('curator.uploadDialog.rightsUpdateFailed') : t('curator.uploadDialog.failed')) })
        }
    }

    async function uploadReady() {
        if (running) return
        const queue = rowsRef.current.filter(row => (row.status === 'READY' || row.status === 'FAILED') && valid(row))
        if (!queue.length) return
        setRunning(true)
        let cursor = 0
        const worker = async () => { while (cursor < queue.length) await processRow(queue[cursor++]) }
        await Promise.all(Array.from({ length: Math.min(3, queue.length) }, worker))
        setRunning(false)
    }

    function close() {
        if (running) return
        rowsRef.current.forEach(row => { if (row.previewUrl) URL.revokeObjectURL(row.previewUrl) })
        setRows([])
        setDefaults({ sourceReferenceId: '', rightsStatus: 'UNKNOWN', license: '', publicDisplayAllowed: false })
        setPdfPreview(null)
        onClose()
    }

    return <AdminModal open={open} onClose={close} closeDisabled={running} maxWidth="xl" blurBackdrop title={t('curator.uploadDialog.batchTitle')} description={t('curator.uploadDialog.batchDescription')} actions={<>
        <Typography variant="body2" color="text.secondary" sx={{ mr: 'auto' }}>{t('curator.uploadDialog.summary', { ready: readyCount, complete: completeCount, failed: failedCount })}</Typography>
        <Button onClick={close} disabled={running}>{completeCount > 0 ? t('curator.uploadDialog.done') : t('admin.cancel')}</Button>
        <Button variant="contained" startIcon={failedCount ? <RefreshOutlinedIcon /> : <AddPhotoAlternateOutlinedIcon />} disabled={!readyCount || running} onClick={() => void uploadReady()}>{failedCount ? t('curator.uploadDialog.retryReady') : t('curator.uploadDialog.uploadReady', { count: readyCount })}</Button>
    </>}>
        <Stack spacing={2.5}>
            {running && <LinearProgress />}
            <Box onDragEnter={event => { event.preventDefault(); setDragging(true) }} onDragOver={event => event.preventDefault()} onDragLeave={() => setDragging(false)} onDrop={event => { event.preventDefault(); setDragging(false); addFiles(event.dataTransfer.files) }} sx={{ border: '1px dashed', borderColor: dragging ? 'primary.main' : 'divider', bgcolor: dragging ? '#F8EDEF' : 'background.paper', p: 2, minHeight: 88, display: 'grid', placeItems: 'center' }}>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: 'center', textAlign: 'center' }}><InsertDriveFileOutlinedIcon color="action" /><Typography>{t('curator.uploadDialog.batchDrop')}</Typography><Button component="label" variant="outlined" startIcon={<AddPhotoAlternateOutlinedIcon />}>{t('curator.uploadDialog.addFiles')}<input hidden multiple type="file" accept={acceptedTypes} onChange={event => { if (event.target.files) addFiles(event.target.files); event.target.value = '' }} /></Button></Stack>
            </Box>
            <Paper variant="outlined" sx={{ p: 2 }}><Stack spacing={2}>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: { md: 'flex-start' } }}>
                    <SourceSelect id="batch-source" value={defaults.sourceReferenceId} references={sourceReferences} referenceLabel={sourceReferenceLabel} label={t('curator.uploadDialog.source')} emptyLabel={t('curator.uploadDialog.noSource')} onChange={value => setDefaults(current => ({ ...current, sourceReferenceId: value }))} />
                    <RightsSelect id="batch-rights" value={defaults.rightsStatus} label={t('admin.archive.fields.rightsStatus')} onChange={rightsStatus => setDefaults(current => ({ ...current, rightsStatus, license: rightsStatus === 'LICENSED' ? current.license : '', publicDisplayAllowed: rightsStatus === 'PUBLIC_DOMAIN' || rightsStatus === 'LICENSED' ? current.publicDisplayAllowed : false }))} t={t} />
                    {defaults.rightsStatus === 'LICENSED' && <TextField size="small" label={t('admin.archive.fields.license')} value={defaults.license} onChange={event => setDefaults(current => ({ ...current, license: event.target.value }))} />}
                    <FormControlLabel control={<Switch checked={defaults.publicDisplayAllowed} disabled={defaults.rightsStatus === 'UNKNOWN' || defaults.rightsStatus === 'RESTRICTED'} onChange={event => setDefaults(current => ({ ...current, publicDisplayAllowed: event.target.checked }))} />} label={t('admin.archive.fields.publicDisplayAllowed')} />
                    <Button variant="outlined" onClick={applyDefaults} disabled={!rows.some(row => row.status !== 'COMPLETE' && !activeStatuses.includes(row.status))}>{t('curator.uploadDialog.applyToAll')}</Button>
                </Stack>
                <RightsGuidance />
            </Stack></Paper>
            {invalidCount > 0 && <Alert severity="warning">{t('curator.uploadDialog.invalidRows', { count: invalidCount })}</Alert>}
            {!rows.length ? <Box sx={{ minHeight: 96, display: 'grid', placeItems: 'center' }}><Typography color="text.secondary">{t('curator.uploadDialog.emptyBatch')}</Typography></Box> : <Box sx={{ overflowX: 'auto' }}><Stack spacing={1.25} sx={{ minWidth: 1180 }}>
                <Box sx={{ display: 'grid', gridTemplateColumns: '86px minmax(180px, 1.1fr) minmax(220px, 1.3fr) minmax(220px, 1.2fr) minmax(210px, 1fr) 120px 150px 48px', gap: 1.5, px: 1.5 }}>{columns.map(column => <Typography key={column} variant="caption" sx={{ fontWeight: 800, color: 'text.secondary', textTransform: 'uppercase' }}>{t(`curator.uploadDialog.columns.${column}`)}</Typography>)}</Box>
                {rows.map(row => <UploadRowEditor key={row.key} row={row} disabled={running || row.status === 'COMPLETE'} references={sourceReferences} referenceLabel={sourceReferenceLabel} sourceWarning={sourceWarning(row)} onChange={update => updateRow(row.key, { ...update, status: row.status === 'FAILED' ? 'READY' : row.status, error: null })} onRightsChange={status => changeRights(row, status)} onPreview={() => setPdfPreview(row)} onRemove={() => removeRow(row)} t={t} />)}
            </Stack></Box>}
        </Stack>
        {pdfPreview && <PdfViewerDialog open source={pdfPreview.file} title={pdfPreview.file.name} downloadName={pdfPreview.file.name} onClose={() => setPdfPreview(null)} />}
    </AdminModal>
}

type CommonSelectProps = { id: string; value: string; label: string; disabled?: boolean; onChange: (value: string) => void }
function SourceSelect({ id, value, label, disabled, references, referenceLabel, emptyLabel, onChange }: CommonSelectProps & { references: SourceReferenceDetails[]; referenceLabel: (reference: SourceReferenceDetails) => string; emptyLabel: string }) {
    return <FormControl size="small" disabled={disabled} sx={{ minWidth: 220 }}><InputLabel id={`${id}-label`}>{label}</InputLabel><Select labelId={`${id}-label`} label={label} value={value} onChange={event => onChange(event.target.value)}><MenuItem value=""><em>{emptyLabel}</em></MenuItem>{references.map(reference => <MenuItem key={reference.id} value={String(reference.id)}>{referenceLabel(reference)}</MenuItem>)}</Select></FormControl>
}
function RightsSelect({ id, value, label, disabled, onChange, t }: Omit<CommonSelectProps, 'onChange'> & { value: RightsStatus; onChange: (value: RightsStatus) => void; t: ReturnType<typeof useTranslation>['t'] }) {
    return <FormControl size="small" disabled={disabled} sx={{ minWidth: 190 }}><InputLabel id={`${id}-label`}>{label}</InputLabel><Select labelId={`${id}-label`} label={label} value={value} onChange={event => onChange(event.target.value as RightsStatus)}>{rightsStatuses.map(status => <MenuItem key={status} value={status}>{apiEnumLabel(t, 'rightsStatus', status)}</MenuItem>)}</Select></FormControl>
}

type RowEditorProps = { row: UploadRow; disabled: boolean; references: SourceReferenceDetails[]; referenceLabel: (reference: SourceReferenceDetails) => string; sourceWarning: boolean; onChange: (update: Partial<UploadRow>) => void; onRightsChange: (status: RightsStatus) => void; onPreview: () => void; onRemove: () => void; t: ReturnType<typeof useTranslation>['t'] }
function UploadRowEditor({ row, disabled, references, referenceLabel, sourceWarning, onChange, onRightsChange, onPreview, onRemove, t }: RowEditorProps) {
    const statusColor = row.status === 'COMPLETE' ? 'success' : row.status === 'FAILED' ? 'error' : activeStatuses.includes(row.status) ? 'info' : 'default'
    return <Paper variant="outlined" sx={{ p: 1.5, display: 'grid', gridTemplateColumns: '86px minmax(180px, 1.1fr) minmax(220px, 1.3fr) minmax(220px, 1.2fr) minmax(210px, 1fr) 120px 150px 48px', gap: 1.5, alignItems: 'start' }}>
        <Box sx={{ width: 78, height: 68, bgcolor: 'grey.100', display: 'grid', placeItems: 'center', overflow: 'hidden' }}>{row.previewUrl ? <Box component="img" src={row.previewUrl} alt="" sx={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : <InsertDriveFileOutlinedIcon color="action" />}</Box>
        <Stack spacing={.5} sx={{ minWidth: 0 }}><Typography variant="body2" sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>{row.file.name}</Typography><Typography variant="caption" color="text.secondary">{(row.file.size / 1024 / 1024).toFixed(2)} MB</Typography>{row.file.type === 'application/pdf' && <Button size="small" startIcon={<VisibilityOutlinedIcon />} onClick={onPreview}>{t('pdfViewer.preview')}</Button>}</Stack>
        <TextField size="small" label={t('curator.uploadDialog.description')} multiline minRows={2} disabled={disabled} value={row.description} onChange={event => onChange({ description: event.target.value })} />
        <Stack spacing={.5}><SourceSelect id={`source-${row.key}`} value={row.sourceReferenceId} disabled={disabled} references={references} referenceLabel={referenceLabel} label={t('curator.uploadDialog.source')} emptyLabel={t('curator.uploadDialog.noSource')} onChange={sourceReferenceId => onChange({ sourceReferenceId })} />{sourceWarning && <FormHelperText error>{t('curator.uploadDialog.sourceNotClearedShort')}</FormHelperText>}</Stack>
        <Stack spacing={1}><RightsSelect id={`rights-${row.key}`} value={row.rightsStatus} disabled={disabled} label={t('admin.archive.fields.rightsStatus')} onChange={onRightsChange} t={t} />{row.rightsStatus === 'LICENSED' && <TextField size="small" required error={!row.license.trim()} label={t('admin.archive.fields.license')} disabled={disabled} value={row.license} onChange={event => onChange({ license: event.target.value })} />}</Stack>
        <Switch slotProps={{ input: { 'aria-label': t('admin.archive.fields.publicDisplayAllowed') } }} checked={row.publicDisplayAllowed} disabled={disabled || row.rightsStatus === 'UNKNOWN' || row.rightsStatus === 'RESTRICTED'} onChange={event => onChange({ publicDisplayAllowed: event.target.checked })} />
        <Stack spacing={.75}><Chip size="small" color={statusColor} label={t(`curator.uploadDialog.rowStatus.${row.status}`)} />{row.error && <Typography variant="caption" color="error" sx={{ overflowWrap: 'anywhere' }}>{row.error}</Typography>}{row.uploadedAsset && row.status === 'FAILED' && <Typography variant="caption" color="warning.main">{t('curator.uploadDialog.uploadedPrivateShort')}</Typography>}</Stack>
        <Tooltip title={t('admin.delete')}><span><IconButton size="small" color="error" disabled={activeStatuses.includes(row.status)} onClick={onRemove} aria-label={t('admin.delete')}><DeleteOutlineOutlinedIcon /></IconButton></span></Tooltip>
    </Paper>
}

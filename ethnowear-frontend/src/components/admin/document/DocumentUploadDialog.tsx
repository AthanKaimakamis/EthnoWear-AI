import { useMemo, useState } from 'react'
import {
    Alert, Box, Button, Checkbox, FormControl, FormControlLabel, InputLabel,
    LinearProgress, MenuItem, Paper, Select, Stack, TextField, ToggleButton, ToggleButtonGroup,
    Typography,
} from '@mui/material'
import CloudUploadOutlinedIcon from '@mui/icons-material/CloudUploadOutlined'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined'
import InsertDriveFileOutlinedIcon from '@mui/icons-material/InsertDriveFileOutlined'
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined'
import AddIcon from '@mui/icons-material/Add'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import { useTranslation } from 'react-i18next'
import { uploadPdfDocument, uploadStandaloneCapture } from '../../../api/DocumentAdminApi'
import { sourceReferencesApi } from '../../../api/ArchiveAdminApi'
import { ApiError, apiErrorMessage } from '../../../api/http'
import { getAdminUsername } from '../../../app/adminAuthStore'
import type { SourceDetails, SourceReferenceDetails } from '../../../types/archive'
import type { DocumentUploadDetails, ProvenanceStatus, ProvenanceTrustState } from '../../../types/document'
import AdminModal from '../AdminModal'
import { provenanceStatuses, provenanceTrustStates } from './documentOptions'
import SourceCreateDialog from './SourceCreateDialog'
import PdfViewerDialog from '../PdfViewerDialog'
import { emptySourceReference, findGeneralSourceReference } from './documentSourceReference'

type UploadKind = 'pdf' | 'capture'

type Props = {
    open: boolean
    sources: SourceDetails[]
    references: SourceReferenceDetails[]
    onClose: () => void
    onUploaded: (result: DocumentUploadDetails, kind: UploadKind, queued: boolean) => void
    onSourceCreated?: (source: SourceDetails) => void
}

const emptyMetadata = { title: '', author: '', publisher: '', publicationYear: '', language: 'bg', sourceId: '', notes: '' }
const MAX_UPLOAD_SIZE_MB = 250
const MAX_UPLOAD_SIZE_BYTES = MAX_UPLOAD_SIZE_MB * 1024 * 1024

function fileSizeInMb(file: File) {
    return (file.size / 1024 / 1024).toFixed(2)
}

export default function DocumentUploadDialog({ open, sources, references, onClose, onUploaded, onSourceCreated }: Props) {
    const { t } = useTranslation()
    const [kind, setKind] = useState<UploadKind>('pdf')
    const [file, setFile] = useState<File | null>(null)
    const [thumbnail, setThumbnail] = useState<File | null>(null)
    const [metadata, setMetadata] = useState(emptyMetadata)
    const [provenanceStatus, setProvenanceStatus] = useState<ProvenanceStatus>('UNKNOWN_SOURCE')
    const [provenanceTrust, setProvenanceTrust] = useState<ProvenanceTrustState>('UNKNOWN')
    const [sourceReferenceId, setSourceReferenceId] = useState('')
    const [reason, setReason] = useState('')
    const [pageNumber, setPageNumber] = useState('')
    const [pageLabel, setPageLabel] = useState('')
    const [description, setDescription] = useState('')
    const [queueOcr, setQueueOcr] = useState(true)
    const [dragging, setDragging] = useState(false)
    const [progress, setProgress] = useState(0)
    const [uploading, setUploading] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [sourceCreateOpen, setSourceCreateOpen] = useState(false)
    const [createdSources, setCreatedSources] = useState<SourceDetails[]>([])
    const [overrideSourceMetadata, setOverrideSourceMetadata] = useState(false)
    const [pdfPreviewOpen, setPdfPreviewOpen] = useState(false)
    const formId = 'document-upload-form'

    const availableSources = useMemo(() => {
        const byId = new Map([...sources, ...createdSources].map(source => [source.id, source]))
        return [...byId.values()].sort((left, right) => left.title.localeCompare(right.title))
    }, [createdSources, sources])

    const sourceReferences = useMemo(() => {
        if (!metadata.sourceId) return references
        return references.filter(reference => reference.sourceId === Number(metadata.sourceId))
    }, [metadata.sourceId, references])

    function changeKind(next: UploadKind | null) {
        if (!next || uploading) return
        setKind(next)
        setFile(null)
        setThumbnail(null)
        setPdfPreviewOpen(false)
        setError(null)
    }

    function selectSource(source?: SourceDetails) {
        setMetadata(current => ({
            ...current,
            sourceId: source ? String(source.id) : '',
            title: source?.title ?? current.title,
            author: source ? source.author ?? '' : current.author,
            publisher: source ? source.publisher ?? '' : current.publisher,
            publicationYear: source?.year ? String(source.year) : source ? '' : current.publicationYear,
            language: source?.language ?? (source ? '' : current.language),
        }))
        setSourceReferenceId(source ? String(findGeneralSourceReference(references, source.id)?.id ?? '') : '')
        setOverrideSourceMetadata(false)
        setProvenanceStatus(source ? 'KNOWN_SOURCE' : 'UNKNOWN_SOURCE')
    }

    function sourceCreated(source: SourceDetails) {
        setCreatedSources(current => [...current.filter(candidate => candidate.id !== source.id), source])
        selectSource(source)
        setSourceCreateOpen(false)
        onSourceCreated?.(source)
    }

    function choose(candidate?: File) {
        if (!candidate) return
        if (candidate.size > MAX_UPLOAD_SIZE_BYTES) {
            setFile(null)
            setError(t('documents.uploadDialog.fileTooLarge', {
                actual: fileSizeInMb(candidate),
                limit: MAX_UPLOAD_SIZE_MB,
            }))
            return
        }
        const accepted = kind === 'pdf'
            ? candidate.type === 'application/pdf'
            : ['image/jpeg', 'image/png', 'image/webp'].includes(candidate.type)
        if (!accepted) {
            setError(t(kind === 'pdf' ? 'documents.uploadDialog.acceptedPdf' : 'documents.uploadDialog.acceptedImage'))
            return
        }
        setFile(candidate)
        setError(null)
    }

    function chooseThumbnail(candidate?: File) {
        if (!candidate) return
        if (!['image/jpeg', 'image/png', 'image/webp'].includes(candidate.type)) {
            setError(t('documents.uploadDialog.thumbnailAccepted'))
            return
        }
        setThumbnail(candidate)
        setError(null)
    }

    function reset() {
        setFile(null)
        setThumbnail(null)
        setPdfPreviewOpen(false)
        setMetadata(emptyMetadata)
        setProvenanceStatus('UNKNOWN_SOURCE')
        setProvenanceTrust('UNKNOWN')
        setSourceReferenceId('')
        setReason('')
        setPageNumber('')
        setPageLabel('')
        setDescription('')
        setQueueOcr(true)
        setProgress(0)
        setError(null)
        setSourceCreateOpen(false)
        setOverrideSourceMetadata(false)
    }

    function close() {
        if (uploading) return
        reset()
        onClose()
    }

    async function submit(event: React.SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        if (!file) { setError(t('documents.uploadDialog.selectFile')); return }
        if (!metadata.title.trim()) { setError(t('documents.uploadDialog.titleRequired')); return }
        if (kind === 'capture' && !reason.trim()) { setError(t('documents.uploadDialog.reasonRequired')); return }

        const bibliographic = {
            sourceId: metadata.sourceId ? Number(metadata.sourceId) : null,
            defaultSourceReferenceId: sourceReferenceId
                ? Number(sourceReferenceId)
                : null,
            title: metadata.title.trim(),
            author: metadata.author.trim() || null,
            publisher: metadata.publisher.trim() || null,
            publicationYear: metadata.publicationYear ? Number(metadata.publicationYear) : null,
            language: metadata.language.trim() || null,
            notes: metadata.notes.trim() || null,
        }

        setUploading(true)
        setProgress(0)
        setError(null)
        try {
            let effectiveSourceReferenceId = sourceReferenceId ? Number(sourceReferenceId) : null
            if (metadata.sourceId && effectiveSourceReferenceId === null) {
                const sourceId = Number(metadata.sourceId)
                const existing = findGeneralSourceReference(references, sourceId)
                effectiveSourceReferenceId = existing?.id ?? (await sourceReferencesApi.create(emptySourceReference(sourceId))).id
                setSourceReferenceId(String(effectiveSourceReferenceId))
            }
            bibliographic.defaultSourceReferenceId = effectiveSourceReferenceId
            const result = kind === 'pdf'
                ? await uploadPdfDocument({
                    metadata: bibliographic,
                    documentType: 'PDF_DOCUMENT',
                    provenanceStatus,
                    provenanceTrustState: provenanceTrust,
                    mediaDescription: description.trim() || null,
                }, file, thumbnail, setProgress)
                : await uploadStandaloneCapture({
                    metadata: bibliographic,
                    provenance: {
                        sourceReferenceId: effectiveSourceReferenceId,
                        provenanceStatus,
                        provenanceTrustState: provenanceTrust,
                        note: description.trim() || null,
                        recordedBy: getAdminUsername() ?? 'admin',
                        reason: reason.trim(),
                    },
                    printedPageNumber: pageNumber.trim() || null,
                    printedPageSort: null,
                    pageLabel: pageLabel.trim() || null,
                    queueOcr,
                    mediaDescription: description.trim() || null,
                }, file, thumbnail, setProgress)
            onUploaded(result, kind, kind === 'pdf' || queueOcr)
            reset()
        } catch (caught) {
            setError(caught instanceof ApiError && caught.status === 413
                ? t('documents.uploadDialog.fileTooLarge', {
                    actual: fileSizeInMb(file),
                    limit: MAX_UPLOAD_SIZE_MB,
                })
                : apiErrorMessage(caught, t('documents.uploadDialog.failed')))
        } finally {
            setUploading(false)
        }
    }

    return (
        <AdminModal
            open={open}
            onClose={close}
            closeDisabled={uploading}
            maxWidth="md"
            title={t('documents.uploadDialog.title')}
            description={t('documents.uploadDialog.description')}
            actions={<>
                <Button onClick={close} disabled={uploading}>{t('admin.cancel')}</Button>
                <Button type="submit" form={formId} variant="contained" disabled={uploading || !file}>{t('documents.uploadDialog.submit')}</Button>
            </>}
        >
            <Box component="form" id={formId} onSubmit={submit}>
                <Stack spacing={2.5}>
                    {error && <Alert severity="error" sx={{ whiteSpace: 'pre-line' }}>{error}</Alert>}
                    {uploading && <Box><LinearProgress variant="determinate" value={progress} /><Typography variant="caption">{t('documents.uploadDialog.uploading', { progress })}</Typography></Box>}
                    <ToggleButtonGroup exclusive fullWidth value={kind} onChange={(_, value) => changeKind(value)} aria-label={t('documents.uploadDialog.title')}>
                        <ToggleButton value="pdf"><Stack><Typography sx={{ fontWeight: 700 }}>{t('documents.uploadDialog.pdf')}</Typography><Typography variant="caption">{t('documents.uploadDialog.pdfHelp')}</Typography></Stack></ToggleButton>
                        <ToggleButton value="capture"><Stack><Typography sx={{ fontWeight: 700 }}>{t('documents.uploadDialog.capture')}</Typography><Typography variant="caption">{t('documents.uploadDialog.captureHelp')}</Typography></Stack></ToggleButton>
                    </ToggleButtonGroup>

                    <Box
                        onDragEnter={event => { event.preventDefault(); setDragging(true) }}
                        onDragOver={event => event.preventDefault()}
                        onDragLeave={() => setDragging(false)}
                        onDrop={event => { event.preventDefault(); setDragging(false); choose(event.dataTransfer.files[0]) }}
                        sx={{ minHeight: 170, border: '1px dashed', borderColor: dragging ? 'primary.main' : 'divider', bgcolor: dragging ? '#F8EDEF' : 'background.paper', display: 'grid', placeItems: 'center', p: 3, textAlign: 'center' }}
                    >
                        <Stack spacing={1.25} sx={{ alignItems: 'center' }}>
                            {kind === 'pdf' ? <InsertDriveFileOutlinedIcon color="action" sx={{ fontSize: 42 }} /> : <ImageOutlinedIcon color="action" sx={{ fontSize: 42 }} />}
                            <Typography sx={{ fontWeight: 700 }}>{file?.name ?? t('documents.uploadDialog.drop')}</Typography>
                            <Typography variant="body2" color="text.secondary">{file ? `${(file.size / 1024 / 1024).toFixed(2)} MB · ${file.type}` : t(kind === 'pdf' ? 'documents.uploadDialog.acceptedPdf' : 'documents.uploadDialog.acceptedImage')}</Typography>
                            <Stack direction="row" spacing={1}>
                                <Button component="label" variant="outlined" startIcon={<CloudUploadOutlinedIcon />} disabled={uploading}>
                                    {file ? t('documents.uploadDialog.change') : t('documents.uploadDialog.choose')}
                                    <input hidden type="file" accept={kind === 'pdf' ? 'application/pdf' : 'image/jpeg,image/png,image/webp'} onChange={event => choose(event.target.files?.[0])} />
                                </Button>
                                {file && kind === 'pdf' && <Button startIcon={<VisibilityOutlinedIcon />} onClick={() => setPdfPreviewOpen(true)}>{t('pdfViewer.preview')}</Button>}
                                {file && <Button color="error" startIcon={<DeleteOutlineIcon />} onClick={() => { setFile(null); setPdfPreviewOpen(false) }}>{t('documents.uploadDialog.remove')}</Button>}
                            </Stack>
                        </Stack>
                    </Box>

                    <Paper variant="outlined" sx={{ p: 2 }}>
                        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}>
                            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                                <ImageOutlinedIcon color="action" />
                                <Box>
                                    <Typography sx={{ fontWeight: 700 }}>{t('documents.uploadDialog.thumbnail')}</Typography>
                                    <Typography variant="body2" color="text.secondary">{thumbnail?.name ?? t('documents.uploadDialog.thumbnailHelp')}</Typography>
                                </Box>
                            </Stack>
                            <Stack direction="row" spacing={1}>
                                <Button component="label" variant="outlined" disabled={uploading}>
                                    {thumbnail ? t('documents.uploadDialog.changeThumbnail') : t('documents.uploadDialog.chooseThumbnail')}
                                    <input hidden type="file" accept="image/jpeg,image/png,image/webp" onChange={event => chooseThumbnail(event.target.files?.[0])} />
                                </Button>
                                {thumbnail && <Button color="error" onClick={() => setThumbnail(null)}>{t('documents.uploadDialog.remove')}</Button>}
                            </Stack>
                        </Stack>
                    </Paper>

                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: { sm: 'center' } }}>
                        <FormControl fullWidth><InputLabel id="document-source-label">{t('documents.uploadDialog.source')}</InputLabel><Select labelId="document-source-label" label={t('documents.uploadDialog.source')} value={metadata.sourceId} onChange={event => selectSource(availableSources.find(source => source.id === Number(event.target.value)))}><MenuItem value=""><em>{t('documents.uploadDialog.noSource')}</em></MenuItem>{availableSources.map(source => <MenuItem key={source.id} value={String(source.id)}>{source.title}</MenuItem>)}</Select></FormControl>
                        <Button variant="outlined" startIcon={<AddIcon />} onClick={() => setSourceCreateOpen(true)} sx={{ flexShrink: 0 }}>
                            {t('documents.uploadDialog.createSource')}
                        </Button>
                    </Stack>
                    {metadata.sourceId && <Alert severity="info">{t(sourceReferenceId ? 'documents.sourceReference.selectedForUpload' : 'documents.sourceReference.willCreateOnUpload')}</Alert>}
                    <TextField required fullWidth label={t('documents.uploadDialog.titleField')} value={metadata.title} onChange={event => setMetadata(value => ({ ...value, title: event.target.value }))} />

                    <Box>
                        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between', mb: 1.5 }}>
                            <Box>
                                <Typography variant="subtitle2">{t('documents.uploadDialog.bibliographicDetails')}</Typography>
                                {metadata.sourceId && !overrideSourceMetadata && <Typography variant="body2" color="text.secondary">{t('documents.uploadDialog.inheritedMetadata')}</Typography>}
                            </Box>
                            {metadata.sourceId && <FormControlLabel
                                control={<Checkbox checked={overrideSourceMetadata} onChange={event => setOverrideSourceMetadata(event.target.checked)} />}
                                label={t('documents.uploadDialog.overrideSourceMetadata')}
                                sx={{ mr: 0 }}
                            />}
                        </Stack>
                        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', md: 'minmax(0, 1fr) minmax(0, 1fr) 140px 140px' }, gap: 2 }}>
                            <TextField fullWidth label={t('documents.uploadDialog.author')} value={metadata.author} onChange={event => setMetadata(value => ({ ...value, author: event.target.value }))} slotProps={{ input: { readOnly: Boolean(metadata.sourceId) && !overrideSourceMetadata } }} />
                            <TextField fullWidth label={t('documents.uploadDialog.publisher')} value={metadata.publisher} onChange={event => setMetadata(value => ({ ...value, publisher: event.target.value }))} slotProps={{ input: { readOnly: Boolean(metadata.sourceId) && !overrideSourceMetadata } }} />
                            <TextField fullWidth type="number" label={t('documents.uploadDialog.year')} value={metadata.publicationYear} onChange={event => setMetadata(value => ({ ...value, publicationYear: event.target.value }))} slotProps={{ input: { readOnly: Boolean(metadata.sourceId) && !overrideSourceMetadata } }} />
                            <TextField fullWidth label={t('documents.uploadDialog.language')} value={metadata.language} onChange={event => setMetadata(value => ({ ...value, language: event.target.value }))} slotProps={{ input: { readOnly: Boolean(metadata.sourceId) && !overrideSourceMetadata } }} />
                        </Box>
                    </Box>
                    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                        <FormControl fullWidth><InputLabel>{t('documents.uploadDialog.provenanceStatus')}</InputLabel><Select label={t('documents.uploadDialog.provenanceStatus')} value={provenanceStatus} onChange={event => setProvenanceStatus(event.target.value as ProvenanceStatus)}>{provenanceStatuses.map(value => <MenuItem key={value} value={value}>{t(`documents.status.provenance.${value}`)}</MenuItem>)}</Select></FormControl>
                        <FormControl fullWidth><InputLabel>{t('documents.uploadDialog.provenanceTrust')}</InputLabel><Select label={t('documents.uploadDialog.provenanceTrust')} value={provenanceTrust} onChange={event => setProvenanceTrust(event.target.value as ProvenanceTrustState)}>{provenanceTrustStates.map(value => <MenuItem key={value} value={value}>{t(`documents.status.trust.${value}`)}</MenuItem>)}</Select></FormControl>
                    </Stack>

                    {kind === 'capture' && <>
                        <FormControl fullWidth><InputLabel>{t('documents.uploadDialog.sourceReference')}</InputLabel><Select label={t('documents.uploadDialog.sourceReference')} value={sourceReferenceId} onChange={event => setSourceReferenceId(event.target.value)}><MenuItem value=""><em>{t('documents.uploadDialog.noReference')}</em></MenuItem>{sourceReferences.map(reference => <MenuItem key={reference.id} value={String(reference.id)}>{[availableSources.find(source => source.id === reference.sourceId)?.title, reference.pageFrom, reference.locator].filter(Boolean).join(' · ')}</MenuItem>)}</Select></FormControl>
                        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}><TextField fullWidth label={t('documents.uploadDialog.pageNumber')} value={pageNumber} onChange={event => setPageNumber(event.target.value)} /><TextField fullWidth label={t('documents.uploadDialog.pageLabel')} value={pageLabel} onChange={event => setPageLabel(event.target.value)} /></Stack>
                        <TextField required multiline minRows={2} label={t('documents.uploadDialog.reason')} value={reason} onChange={event => setReason(event.target.value)} />
                        <FormControlLabel control={<Checkbox checked={queueOcr} onChange={event => setQueueOcr(event.target.checked)} />} label={t('documents.uploadDialog.queueOcr')} />
                    </>}
                    <TextField multiline minRows={2} label={t('documents.uploadDialog.notes')} value={metadata.notes} onChange={event => setMetadata(value => ({ ...value, notes: event.target.value }))} />
                    <TextField multiline minRows={2} label={t('documents.uploadDialog.descriptionField')} value={description} onChange={event => setDescription(event.target.value)} />
                </Stack>
            </Box>
            {sourceCreateOpen && <SourceCreateDialog
                initialValues={{
                    title: metadata.title,
                    author: nullable(metadata.author),
                    publisher: nullable(metadata.publisher),
                    year: metadata.publicationYear ? Number(metadata.publicationYear) : null,
                    language: nullable(metadata.language),
                }}
                onClose={() => setSourceCreateOpen(false)}
                onCreated={sourceCreated}
            />}
            {pdfPreviewOpen && file && kind === 'pdf' && <PdfViewerDialog
                open
                source={file}
                title={file.name}
                downloadName={file.name}
                onClose={() => setPdfPreviewOpen(false)}
            />}
        </AdminModal>
    )
}

function nullable(value: string) {
    return value.trim() || null
}

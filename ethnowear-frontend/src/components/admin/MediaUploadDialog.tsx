import { useEffect, useMemo, useState } from 'react'
import {
    Alert, Box, Button,
    FormControl, InputLabel, LinearProgress, MenuItem, Select, Stack, TextField,
    Typography,
} from '@mui/material'
import CloudUploadOutlinedIcon from '@mui/icons-material/CloudUploadOutlined'
import InsertDriveFileOutlinedIcon from '@mui/icons-material/InsertDriveFileOutlined'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import { uploadMediaAsset } from '../../api/ArchiveAdminApi'
import type { MediaAssetDetails, MediaType, SourceReferenceDetails } from '../../types/archive'
import { apiErrorMessage } from '../../api/http'
import { useTranslation } from 'react-i18next'
import AdminModal from './AdminModal'
import PdfViewerDialog from './PdfViewerDialog'
import { PreviewableImage } from '../common/ImageViewerDialog'

type Props = {
    open: boolean
    category: 'archive' | 'documents' | 'entities'
    sourceReferences: SourceReferenceDetails[]
    sourceReferenceLabel: (reference: SourceReferenceDetails) => string
    onClose: () => void
    onUploaded: (asset: MediaAssetDetails) => void
}

export default function MediaUploadDialog({ open, category, sourceReferences, sourceReferenceLabel, onClose, onUploaded }: Props) {
    const { t } = useTranslation()
    const [file, setFile] = useState<File | null>(null)
    const [sourceReferenceId, setSourceReferenceId] = useState('')
    const [description, setDescription] = useState('')
    const [dragging, setDragging] = useState(false)
    const [uploading, setUploading] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [pdfPreviewOpen, setPdfPreviewOpen] = useState(false)
    const formId = 'media-upload-form'
    const preview = useMemo(() => file?.type.startsWith('image/') ? URL.createObjectURL(file) : null, [file])

    useEffect(() => () => { if (preview) URL.revokeObjectURL(preview) }, [preview])

    function choose(candidate: File | undefined) {
        if (!candidate) return
        setFile(candidate)
        setPdfPreviewOpen(false)
        setError(null)
    }

    function close() {
        if (uploading) return
        setPdfPreviewOpen(false)
        onClose()
    }

    async function submit(event: React.SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        if (!file) { setError(t('curator.uploadDialog.selectFile')); return }
        const mediaType: MediaType = file.type === 'application/pdf' ? 'PDF' : file.type.startsWith('image/') ? 'IMAGE' : 'OTHER'
        setUploading(true)
        setError(null)
        try {
            const asset = await uploadMediaAsset(file, {
                sourceReferenceId: sourceReferenceId ? Number(sourceReferenceId) : null,
                mediaType,
                category,
                description: description.trim() || null,
            })
            onUploaded(asset)
            setFile(null)
            setDescription('')
            setSourceReferenceId('')
            setPdfPreviewOpen(false)
        } catch (caught) {
            setError(apiErrorMessage(caught, t('curator.uploadDialog.failed')))
        } finally {
            setUploading(false)
        }
    }

    return (
        <AdminModal
            open={open}
            onClose={close}
            closeDisabled={uploading}
            maxWidth="sm"
            title={t('curator.uploadDialog.title')}
            actions={
                <>
                    <Button onClick={close} disabled={uploading}>{t('admin.cancel')}</Button>
                    <Button type="submit" form={formId} variant="contained" disabled={!file || uploading}>{t('curator.media.upload')}</Button>
                </>
            }
        >
            <Box component="form" id={formId} onSubmit={submit}>
                {uploading && <LinearProgress />}
                    <Stack spacing={2.5}>
                        {error && <Alert severity="error">{error}</Alert>}
                        <Box
                            onDragEnter={event => { event.preventDefault(); setDragging(true) }}
                            onDragOver={event => event.preventDefault()}
                            onDragLeave={() => setDragging(false)}
                            onDrop={event => { event.preventDefault(); setDragging(false); choose(event.dataTransfer.files[0]) }}
                            sx={{ minHeight: 190, border: '1px dashed', borderColor: dragging ? 'primary.main' : 'divider', bgcolor: dragging ? '#F8EDEF' : 'background.default', display: 'grid', placeItems: 'center', p: 3, textAlign: 'center' }}
                        >
                            <Stack spacing={1.5} sx={{ alignItems: 'center' }}>
                                {preview ? <PreviewableImage src={preview} alt={file?.name ?? t('curator.uploadDialog.preview')} buttonSx={{ maxWidth: '100%' }} imageSx={{ maxWidth: '100%', maxHeight: 150, objectFit: 'contain' }} /> : <InsertDriveFileOutlinedIcon color="action" sx={{ fontSize: 46 }} />}
                                <Typography sx={{ fontWeight: 600 }}>{file?.name ?? t('curator.uploadDialog.drop')}</Typography>
                                {file && <Typography variant="body2" color="text.secondary">{(file.size / 1024 / 1024).toFixed(2)} MB</Typography>}
                                <Stack direction="row" spacing={1}>
                                    <Button component="label" variant="outlined" startIcon={<CloudUploadOutlinedIcon />}>
                                        {t('curator.uploadDialog.choose')}
                                        <input hidden type="file" accept="image/jpeg,image/png,image/gif,image/webp,application/pdf" onChange={event => choose(event.target.files?.[0])} />
                                    </Button>
                                    {file?.type === 'application/pdf' && <Button startIcon={<VisibilityOutlinedIcon />} onClick={() => setPdfPreviewOpen(true)}>{t('pdfViewer.preview')}</Button>}
                                </Stack>
                            </Stack>
                        </Box>
                        <FormControl fullWidth>
                            <InputLabel id="upload-source-label">{t('curator.uploadDialog.source')}</InputLabel>
                            <Select labelId="upload-source-label" label={t('curator.uploadDialog.source')} value={sourceReferenceId} onChange={event => setSourceReferenceId(event.target.value)}>
                                <MenuItem value=""><em>{t('curator.uploadDialog.noSource')}</em></MenuItem>
                                {sourceReferences.map(reference => <MenuItem key={reference.id} value={String(reference.id)}>{sourceReferenceLabel(reference)}</MenuItem>)}
                            </Select>
                        </FormControl>
                        <TextField label={t('curator.uploadDialog.description')} value={description} onChange={event => setDescription(event.target.value)} multiline minRows={2} />
                        <Alert severity="info">{t('curator.uploadDialog.automaticMetadata')}</Alert>
                    </Stack>
            </Box>
            {pdfPreviewOpen && file?.type === 'application/pdf' && <PdfViewerDialog
                open
                source={file}
                title={file.name}
                downloadName={file.name}
                onClose={() => setPdfPreviewOpen(false)}
            />}
        </AdminModal>
    )
}

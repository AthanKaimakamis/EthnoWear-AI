import { useEffect, useState } from 'react'
import { Alert, Box, Button, Chip, LinearProgress, Paper, Stack, Typography } from '@mui/material'
import UploadFileOutlinedIcon from '@mui/icons-material/UploadFileOutlined'
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined'
import AdminPageHeader from '../../components/admin/AdminPageHeader'
import MediaUploadDialog from '../../components/admin/MediaUploadDialog'
import { mediaAssetsApi, sourceReferencesApi, sourcesApi } from '../../api/ArchiveAdminApi'
import type { MediaAssetDetails, SourceDetails, SourceReferenceDetails } from '../../types/archive'
import { useTranslation } from 'react-i18next'

export default function DocumentsPage() {
    const { t } = useTranslation()
    const [documents, setDocuments] = useState<MediaAssetDetails[]>([])
    const [sources, setSources] = useState<SourceDetails[]>([])
    const [references, setReferences] = useState<SourceReferenceDetails[]>([])
    const [uploadOpen, setUploadOpen] = useState(false)
    const [loading, setLoading] = useState(true)
    useEffect(() => { Promise.all([mediaAssetsApi.findAll({ size: 1000, sort: 'updatedAt,desc' }), sourcesApi.findAll({ size: 1000 }), sourceReferencesApi.findAll({ size: 1000 })]).then(([media, sourcePage, refs]) => { setDocuments(media.content.filter(asset => asset.mediaType === 'PDF')); setSources(sourcePage.content); setReferences(refs.content) }).finally(() => setLoading(false)) }, [])
    const sourceFor = (asset: MediaAssetDetails) => { const reference = references.find(value => value.id === asset.sourceReferenceId); return reference ? sources.find(value => value.id === reference.sourceId) : undefined }
    const referenceLabel = (reference: SourceReferenceDetails) => { const source = sources.find(value => value.id === reference.sourceId); return [source?.title, reference.pageFrom ? `${t('curator.fields.page')} ${reference.pageFrom}` : null].filter(Boolean).join(' · ') }
    return <Stack spacing={3}><AdminPageHeader title={t('curator.documents.title')} description={t('curator.documents.description')} actions={<Button variant="contained" startIcon={<UploadFileOutlinedIcon />} onClick={() => setUploadOpen(true)}>{t('curator.documents.upload')}</Button>} /><Alert severity="warning">{t('curator.documents.apiGap')}</Alert>{loading && <LinearProgress />}
        <Stack spacing={1}>{documents.map(document => <Paper key={document.id} variant="outlined" sx={{ p: 2 }}><Stack direction="row" sx={{ gap: 2, alignItems: 'center' }}><PictureAsPdfOutlinedIcon color="error" sx={{ fontSize: 38 }} /><Box sx={{ flex: 1, minWidth: 0 }}><Typography sx={{ fontWeight: 700 }}>{sourceFor(document)?.title ?? document.fileName}</Typography><Typography variant="body2" color="text.secondary">{[sourceFor(document)?.author, sourceFor(document)?.year].filter(Boolean).join(' · ')}</Typography></Box><Chip label={t('curator.documents.uploaded')} /><Chip variant="outlined" label={t('curator.documents.processingUnavailable')} /></Stack></Paper>)}</Stack>
        {!loading && documents.length === 0 && <Paper variant="outlined" sx={{ p: 5, textAlign: 'center' }}><Typography color="text.secondary">{t('curator.documents.empty')}</Typography></Paper>}
        <MediaUploadDialog open={uploadOpen} category="documents" sourceReferences={references} sourceReferenceLabel={referenceLabel} onClose={() => setUploadOpen(false)} onUploaded={asset => { setDocuments(current => [asset, ...current]); setUploadOpen(false) }} />
    </Stack>
}

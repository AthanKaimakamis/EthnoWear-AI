import { useEffect, useMemo, useState } from 'react'
import {
    Alert, Box, Button, Chip,
    InputAdornment, LinearProgress, Paper,
    Stack, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography,
} from '@mui/material'
import AppsOutlinedIcon from '@mui/icons-material/AppsOutlined'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined'
import ListOutlinedIcon from '@mui/icons-material/ListOutlined'
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined'
import SearchIcon from '@mui/icons-material/Search'
import UploadOutlinedIcon from '@mui/icons-material/UploadOutlined'
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined'
import ImageNotSupportedOutlinedIcon from '@mui/icons-material/ImageNotSupportedOutlined'
import AdminPageHeader from '../../components/admin/AdminPageHeader'
import MediaUploadDialog from '../../components/admin/MediaUploadDialog'
import AdminModal from '../../components/admin/AdminModal'
import ConfirmDialog from '../../components/admin/ConfirmDialog'
import FormSelectField from '../../components/forms/FormSelectField'
import { archiveItemMediaApi, mediaAssetsApi, mediaEntityLinksApi, sourceReferencesApi, sourcesApi } from '../../api/ArchiveAdminApi'
import type { ArchiveItemMediaDetails, MediaAssetDetails, MediaEntityLinkDetails, SourceDetails, SourceReferenceDetails } from '../../types/archive'
import { useTranslation } from 'react-i18next'

export default function MediaLibraryPage() {
    const { t } = useTranslation()
    const [assets, setAssets] = useState<MediaAssetDetails[]>([])
    const [itemLinks, setItemLinks] = useState<ArchiveItemMediaDetails[]>([])
    const [entityLinks, setEntityLinks] = useState<MediaEntityLinkDetails[]>([])
    const [sources, setSources] = useState<SourceDetails[]>([])
    const [references, setReferences] = useState<SourceReferenceDetails[]>([])
    const [query, setQuery] = useState('')
    const [type, setType] = useState('')
    const [usage, setUsage] = useState('')
    const [source, setSource] = useState('')
    const [view, setView] = useState<'grid' | 'list'>('grid')
    const [selected, setSelected] = useState<MediaAssetDetails | null>(null)
    const [deleting, setDeleting] = useState<MediaAssetDetails | null>(null)
    const [uploadOpen, setUploadOpen] = useState(false)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    async function load() {
        setLoading(true); setError(null)
        try {
            const [assetPage, itemPage, entityPage, sourcePage, referencePage] = await Promise.all([
                mediaAssetsApi.findAll({ size: 1000, sort: 'createdAt,desc' }), archiveItemMediaApi.findAll({ size: 1000 }),
                mediaEntityLinksApi.findAll({ size: 1000 }), sourcesApi.findAll({ size: 1000 }), sourceReferencesApi.findAll({ size: 1000 }),
            ])
            setAssets(assetPage.content); setItemLinks(itemPage.content); setEntityLinks(entityPage.content); setSources(sourcePage.content); setReferences(referencePage.content)
        } catch (caught) { setError(caught instanceof Error ? caught.message : String(caught)) } finally { setLoading(false) }
    }
    useEffect(() => {
        Promise.all([
            mediaAssetsApi.findAll({ size: 1000, sort: 'createdAt,desc' }), archiveItemMediaApi.findAll({ size: 1000 }),
            mediaEntityLinksApi.findAll({ size: 1000 }), sourcesApi.findAll({ size: 1000 }), sourceReferencesApi.findAll({ size: 1000 }),
        ]).then(([assetPage, itemPage, entityPage, sourcePage, referencePage]) => {
            setAssets(assetPage.content); setItemLinks(itemPage.content); setEntityLinks(entityPage.content); setSources(sourcePage.content); setReferences(referencePage.content)
        }).catch(caught => setError(caught instanceof Error ? caught.message : String(caught)))
            .finally(() => setLoading(false))
    }, [])

    const usageByAsset = useMemo(() => {
        const counts = new Map<number, number>()
        ;[...itemLinks, ...entityLinks].forEach(link => counts.set(link.mediaAssetId, (counts.get(link.mediaAssetId) ?? 0) + 1))
        return counts
    }, [entityLinks, itemLinks])
    const sourceByReference = useMemo(() => new Map(references.map(reference => [reference.id, sources.find(sourceValue => sourceValue.id === reference.sourceId)])), [references, sources])
    const usageCount = (assetId: number) => usageByAsset.get(assetId) ?? 0
    const sourceFor = (asset: MediaAssetDetails) => asset.sourceReferenceId ? sourceByReference.get(asset.sourceReferenceId) : null
    const filtered = useMemo(() => assets.filter(asset => {
        const count = usageByAsset.get(asset.id) ?? 0
        const sourceValue = asset.sourceReferenceId ? sourceByReference.get(asset.sourceReferenceId) : null
        const text = [asset.fileName, asset.description, sourceValue?.title].join(' ').toLocaleLowerCase()
        return (!query.trim() || text.includes(query.trim().toLocaleLowerCase())) && (!type || asset.mediaType === type)
            && (!usage || (usage === 'used' ? count > 0 : count === 0)) && (!source || sourceValue?.id === Number(source))
    }), [assets, query, source, sourceByReference, type, usage, usageByAsset])
    const referenceLabel = (reference: SourceReferenceDetails) => { const sourceValue = sources.find(value => value.id === reference.sourceId); return [sourceValue?.title, reference.pageFrom ? `${t('curator.fields.page')} ${reference.pageFrom}` : null].filter(Boolean).join(' · ') }

    async function remove() {
        if (!deleting) return
        try { await mediaAssetsApi.remove(deleting.id); setDeleting(null); await load() } catch (caught) { setError(caught instanceof Error ? caught.message : String(caught)); setDeleting(null) }
    }

    return <Stack spacing={3}>
        <AdminPageHeader title={t('curator.mediaLibrary.title')} description={t('curator.mediaLibrary.description')} actions={<Button variant="contained" startIcon={<UploadOutlinedIcon />} onClick={() => setUploadOpen(true)}>{t('curator.media.upload')}</Button>} />
        <Paper variant="outlined" sx={{ p: 2 }}><Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', md: 'minmax(220px, 1.6fr) repeat(3, minmax(130px, 1fr)) auto' }, gap: 1.5, alignItems: 'center' }}>
            <TextField size="small" fullWidth value={query} onChange={event => setQuery(event.target.value)} placeholder={t('curator.mediaLibrary.search')} slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
            <FormSelectField name="media-type-filter" size="small" label={t('curator.mediaLibrary.type')} value={type} onChange={event => setType(event.target.value)} options={[{ value: '', label: t('admin.allCategories') }, ...['IMAGE','PDF','SCAN','THUMBNAIL','OTHER'].map(value => ({ value, label: value }))]} />
            <FormSelectField name="media-usage-filter" size="small" label={t('curator.mediaLibrary.usage')} value={usage} onChange={event => setUsage(event.target.value)} options={[{ value: '', label: t('admin.allCategories') }, ...['used','unused'].map(value => ({ value, label: t(`curator.mediaLibrary.${value}`) }))]} />
            <FormSelectField name="media-source-filter" size="small" label={t('curator.fields.source')} value={source} onChange={event => setSource(event.target.value)} options={[{ value: '', label: t('admin.allCategories') }, ...sources.map(value => ({ value: String(value.id), label: value.title }))]} />
            <ToggleButtonGroup exclusive size="small" value={view} onChange={(_, next) => next && setView(next)}><ToggleButton value="grid"><AppsOutlinedIcon /></ToggleButton><ToggleButton value="list"><ListOutlinedIcon /></ToggleButton></ToggleButtonGroup>
        </Box></Paper>
        {loading && <LinearProgress />}{error && <Alert severity="error">{error}</Alert>}
        {!loading && filtered.length === 0 && <Paper variant="outlined" sx={{ p: 5, textAlign: 'center' }}><Typography color="text.secondary">{t('curator.mediaLibrary.empty')}</Typography></Paper>}
        <Box sx={{ display: 'grid', gridTemplateColumns: view === 'grid' ? { xs: '1fr 1fr', sm: 'repeat(3, 1fr)', xl: 'repeat(5, 1fr)' } : '1fr', gap: 1.5 }}>
            {filtered.map(asset => { const count = usageCount(asset.id); const sourceValue = sourceFor(asset); return <Paper key={asset.id} variant="outlined" sx={{ overflow: 'hidden', display: view === 'list' ? 'flex' : 'block', minWidth: 0 }}>
                <Box onClick={() => setSelected(asset)} sx={{ width: view === 'list' ? 160 : '100%', aspectRatio: view === 'list' ? '16/9' : '4/3', bgcolor: 'background.default', display: 'grid', placeItems: 'center', cursor: 'pointer', overflow: 'hidden' }}>
                    {asset.mediaType === 'IMAGE' || asset.mediaType === 'THUMBNAIL' || asset.mediaType === 'SCAN' ? <MediaThumbnail asset={asset} /> : <PictureAsPdfOutlinedIcon color="action" sx={{ fontSize: 46 }} />}
                </Box>
                <Stack sx={{ p: 1.5, minWidth: 0, flex: 1 }} spacing={.75}><Typography sx={{ fontWeight: 700 }} noWrap>{asset.fileName ?? t('curator.media.unnamed')}</Typography><Typography variant="body2" color="text.secondary" noWrap>{asset.description ?? sourceValue?.title ?? t('curator.mediaLibrary.noDescription')}</Typography><Stack direction="row" sx={{ gap: .75, alignItems: 'center' }}><Chip size="small" label={asset.mediaType} /><Chip size="small" variant="outlined" label={t('curator.mediaLibrary.usageCount', { count })} /></Stack><Stack direction="row" sx={{ justifyContent: 'flex-end' }}><Button size="small" onClick={() => setSelected(asset)}>{t('curator.actions.details')}</Button><Tooltip title={count ? t('curator.mediaLibrary.inUseWarning') : t('admin.delete')}><span><Button size="small" color="error" disabled={count > 0} onClick={() => setDeleting(asset)}><DeleteOutlineIcon fontSize="small" /></Button></span></Tooltip></Stack></Stack>
            </Paper> })}
        </Box>
        <MediaUploadDialog open={uploadOpen} category="archive" sourceReferences={references} sourceReferenceLabel={referenceLabel} onClose={() => setUploadOpen(false)} onUploaded={asset => { setAssets(current => [asset, ...current]); setUploadOpen(false) }} />
        <AdminModal open={Boolean(selected)} onClose={() => setSelected(null)} maxWidth="md" title={selected?.fileName ?? t('curator.media.unnamed')}>
            {selected && <Stack spacing={2}>{selected.mediaType === 'PDF' ? <Box component="iframe" title={selected.fileName ?? 'PDF'} src={`/api/media/${selected.id}/content`} sx={{ width: '100%', height: 520, border: 0 }} /> : <Box component="img" src={`/api/media/${selected.id}/content`} alt={selected.fileName ?? ''} sx={{ maxWidth: '100%', maxHeight: 560, objectFit: 'contain', alignSelf: 'center' }} />}<Typography>{selected.description}</Typography><Alert severity={usageCount(selected.id) ? 'info' : 'warning'}>{usageCount(selected.id) ? t('curator.mediaLibrary.usageCount', { count: usageCount(selected.id) }) : t('curator.mediaLibrary.unusedWarning')}</Alert>{itemLinks.filter(link => link.mediaAssetId === selected.id).map(link => <Typography key={link.id} variant="body2">{t('curator.mediaLibrary.archiveUsage', { id: link.archiveItemId })}</Typography>)}{entityLinks.filter(link => link.mediaAssetId === selected.id).map(link => <Typography key={link.id} variant="body2">{link.description ?? link.entityType}</Typography>)}<Alert icon={<WarningAmberOutlinedIcon />} severity="info">{t('curator.mediaLibrary.replaceUnavailable')}</Alert></Stack>}
        </AdminModal>
        <ConfirmDialog open={Boolean(deleting)} title={t('admin.confirmDelete')} onCancel={() => setDeleting(null)} onConfirm={remove}>
            {t('admin.confirmDeleteText', { name: deleting?.fileName })}
        </ConfirmDialog>
    </Stack>
}
function MediaThumbnail({ asset }: { asset: MediaAssetDetails }) {
    const [failed, setFailed] = useState(false)
    return failed
        ? <ImageNotSupportedOutlinedIcon color="action" sx={{ fontSize: 46 }} />
        : <Box component="img" src={`/api/media/${asset.id}/content`} alt="" onError={() => setFailed(true)} sx={{ width: '100%', height: '100%', objectFit: 'cover' }} />
}

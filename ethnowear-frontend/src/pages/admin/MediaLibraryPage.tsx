import { useCallback, useEffect, useMemo, useState } from 'react'
import {
    Alert, Box, Button, Checkbox, Chip, CircularProgress, InputAdornment, Paper, Skeleton, Stack, TextField,
    ToggleButton, ToggleButtonGroup, Tooltip, Typography,
} from '@mui/material'
import AppsOutlinedIcon from '@mui/icons-material/AppsOutlined'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import ListOutlinedIcon from '@mui/icons-material/ListOutlined'
import LinkOutlinedIcon from '@mui/icons-material/LinkOutlined'
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined'
import SearchIcon from '@mui/icons-material/Search'
import UploadOutlinedIcon from '@mui/icons-material/UploadOutlined'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined'
import { useInfiniteQuery, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { archiveItemMediaApi, findDocumentMediaLinks, getAdminMediaContent, mediaAssetsApi, mediaEntityLinksApi, sourceReferencesApi, sourcesApi } from '../../api/ArchiveAdminApi'
import { deletePageFigure, documentQueryKeys, listDocuments, listPageFigures } from '../../api/DocumentAdminApi'
import { apiErrorMessage } from '../../api/http'
import { useAdminAuth } from '../../app/adminAuth'
import { apiEnumLabel } from '../../app/apiEnumLabels'
import { hasAnyRole, processingMutationRoles, rightsManagementRoles } from '../../app/permissions'
import AdminModal from '../../components/admin/AdminModal'
import AdminPageHeader from '../../components/admin/AdminPageHeader'
import ArchiveRecordDialog, { type ArchiveField, type ArchiveFormValues } from '../../components/admin/ArchiveRecordDialog'
import ConfirmDialog from '../../components/admin/ConfirmDialog'
import MediaOntologyLinksEditor from '../../components/admin/media/MediaOntologyLinksEditor'
import RightsGuidance from '../../components/admin/media/RightsGuidance'
import AdminMediaThumbnail from '../../components/admin/media/AdminMediaThumbnail'
import { useAdminMediaContent } from '../../components/admin/media/useAdminMediaContent'
import MediaUploadDialog from '../../components/admin/MediaUploadDialog'
import PdfViewerDialog from '../../components/admin/PdfViewerDialog'
import InfiniteScrollTrigger from '../../components/common/InfiniteScrollTrigger'
import { PreviewableImage } from '../../components/common/ImageViewerDialog'
import FormSelectField from '../../components/forms/FormSelectField'
import type { MediaAssetDetails, MediaAssetWriteDto, SourceReferenceDetails } from '../../types/archive'
import type { ArchiveAdminWriteDto } from '../../types/archiveAdmin'
import { approvedLibraryAssets, canDeleteMediaAsset } from './mediaLibraryModel'
import ArchiveEditorPage from './ArchiveEditorPage'

const PAGE_SIZE = 24
const DOCUMENT_PAGE_SIZE = 100
const mediaQueryKey = ['admin', 'media'] as const

async function listAllDocuments(signal?: AbortSignal) {
    const documents = []
    let page = 0
    let last = false

    while (!last) {
        const result = await listDocuments({ page, size: DOCUMENT_PAGE_SIZE }, signal)
        documents.push(...result.content)
        last = result.last
        page += 1
    }

    return documents
}

export default function MediaLibraryPage() {
    const { t, i18n } = useTranslation()
    const queryClient = useQueryClient()
    const { admin } = useAdminAuth()
    const canEditOntologyLinks = Boolean(admin && hasAnyRole(admin.roles, processingMutationRoles))
    const canEditRights = Boolean(admin && hasAnyRole(admin.roles, rightsManagementRoles))
    const [query, setQuery] = useState('')
    const [type, setType] = useState('')
    const [usage, setUsage] = useState('')
    const [source, setSource] = useState('')
    const [view, setView] = useState<'grid' | 'list'>('grid')
    const [selected, setSelected] = useState<MediaAssetDetails | null>(null)
    const [archiveSelection, setArchiveSelection] = useState<MediaAssetDetails[]>([])
    const [archiveImages, setArchiveImages] = useState<MediaAssetDetails[] | null>(null)
    const [pdfPreview, setPdfPreview] = useState<MediaAssetDetails | null>(null)
    const [deleting, setDeleting] = useState<MediaAssetDetails | null>(null)
    const [deletePending, setDeletePending] = useState(false)
    const [uploadOpen, setUploadOpen] = useState(false)
    const [rightsEditing, setRightsEditing] = useState<MediaAssetDetails | null>(null)
    const [rightsSaving, setRightsSaving] = useState(false)
    const [rightsError, setRightsError] = useState<string | null>(null)
    const [error, setError] = useState<string | null>(null)

    const assetsQuery = useInfiniteQuery({
        queryKey: [...mediaQueryKey, 'assets'],
        initialPageParam: 0,
        queryFn: ({ pageParam, signal }) => mediaAssetsApi.findAll({ page: pageParam, size: PAGE_SIZE, sort: 'createdAt,desc' }, signal),
        getNextPageParam: page => page.last ? undefined : page.number + 1,
    })
    const metadataQuery = useQuery({
        queryKey: [...mediaQueryKey, 'metadata'],
        queryFn: async ({ signal }) => {
            const [itemPage, entityPage, sourcePage, referencePage, documents] = await Promise.all([
                archiveItemMediaApi.findAll({ size: 1000 }, signal),
                mediaEntityLinksApi.findAll({ size: 1000 }, signal),
                sourcesApi.findAll({ size: 1000 }, signal),
                sourceReferencesApi.findAll({ size: 1000 }, signal),
                listAllDocuments(signal),
            ])
            return {
                itemLinks: itemPage.content,
                entityLinks: entityPage.content,
                sources: sourcePage.content,
                references: referencePage.content,
                documents,
            }
        },
    })

    const assets = useMemo(() => approvedLibraryAssets(assetsQuery.data?.pages.flatMap(page => page.content) ?? []), [assetsQuery.data])
    const documentLinkQueries = useQueries({
        queries: (assetsQuery.data?.pages ?? []).map(pageResult => {
            const mediaAssetIds = pageResult.content.map(asset => asset.id)
            return {
                queryKey: [...mediaQueryKey, 'document-links', mediaAssetIds],
                queryFn: ({ signal }: { signal: AbortSignal }) => findDocumentMediaLinks(mediaAssetIds, signal),
                enabled: mediaAssetIds.length > 0,
            }
        }),
    })
    const documentLinks = useMemo(() => documentLinkQueries.flatMap(result => result.data ?? []), [documentLinkQueries])
    const metadata = metadataQuery.data
    const itemLinks = useMemo(() => metadata?.itemLinks ?? [], [metadata?.itemLinks])
    const entityLinks = useMemo(() => metadata?.entityLinks ?? [], [metadata?.entityLinks])
    const sources = useMemo(() => metadata?.sources ?? [], [metadata?.sources])
    const references = useMemo(() => metadata?.references ?? [], [metadata?.references])
    const documents = useMemo(() => metadata?.documents ?? [], [metadata?.documents])
    const filterActive = Boolean(query.trim() || type || usage || source)

    useEffect(() => {
        if (filterActive && assetsQuery.hasNextPage && !assetsQuery.isFetchingNextPage) void assetsQuery.fetchNextPage()
    }, [assetsQuery, filterActive])

    const referenceById = useMemo(() => new Map(references.map(reference => [reference.id, reference])), [references])
    const sourceById = useMemo(() => new Map(sources.map(sourceValue => [sourceValue.id, sourceValue])), [sources])
    const documentSourceByAsset = useMemo(() => new Map(documents.flatMap(document =>
        document.sourceId ? [document.originalMediaAssetId, document.thumbnailMediaAssetId]
            .filter((id): id is number => id !== null)
            .map(id => [id, document.sourceId] as const) : [],
    )), [documents])
    const documentById = useMemo(() => new Map(documents.map(document => [document.id, document])), [documents])
    const pageSourceByAsset = useMemo(() => new Map(documentLinks.map(link => {
        const referenceSourceId = link.sourceReferenceId ? referenceById.get(link.sourceReferenceId)?.sourceId : null
        return [link.mediaAssetId, referenceSourceId ?? link.documentSourceId] as const
    })), [documentLinks, referenceById])
    const sourceIdFor = useCallback((asset: MediaAssetDetails) => {
        const figureReferenceId = asset.documentFigure?.sourceReferenceId
        const referenceId = figureReferenceId ?? asset.sourceReferenceId
        const referenceSourceId = referenceId ? referenceById.get(referenceId)?.sourceId : null
        return referenceSourceId ?? pageSourceByAsset.get(asset.id) ?? documentSourceByAsset.get(asset.id) ?? null
    }, [documentSourceByAsset, pageSourceByAsset, referenceById])
    const sourceFor = useCallback((asset: MediaAssetDetails) => {
        const sourceId = sourceIdFor(asset)
        return sourceId ? sourceById.get(sourceId) ?? null : null
    }, [sourceById, sourceIdFor])

    const usageByAsset = useMemo(() => {
        const counts = new Map<number, number>()
        ;[...itemLinks, ...entityLinks].forEach(link => counts.set(link.mediaAssetId, (counts.get(link.mediaAssetId) ?? 0) + 1))
        documents.forEach(document => {
            if (document.originalMediaAssetId) counts.set(document.originalMediaAssetId, (counts.get(document.originalMediaAssetId) ?? 0) + 1)
            if (document.thumbnailMediaAssetId) counts.set(document.thumbnailMediaAssetId, (counts.get(document.thumbnailMediaAssetId) ?? 0) + 1)
        })
        documentLinks.forEach(link => counts.set(link.mediaAssetId, (counts.get(link.mediaAssetId) ?? 0) + 1))
        assets.forEach(asset => { if (asset.documentFigure) counts.set(asset.id, Math.max(1, counts.get(asset.id) ?? 0)) })
        return counts
    }, [assets, documentLinks, documents, entityLinks, itemLinks])
    const usageCount = useCallback((assetId: number) => usageByAsset.get(assetId) ?? 0, [usageByAsset])
    const filtered = useMemo(() => assets.filter(asset => {
        const count = usageCount(asset.id)
        const sourceValue = sourceFor(asset)
        const text = [asset.fileName, asset.description, sourceValue?.title].join(' ').toLocaleLowerCase()
        return (!query.trim() || text.includes(query.trim().toLocaleLowerCase()))
            && (!type || asset.mediaType === type)
            && (!usage || (usage === 'used' ? count > 0 : count === 0))
            && (!source || sourceIdFor(asset) === Number(source))
    }), [assets, query, source, sourceFor, sourceIdFor, type, usage, usageCount])

    const loadMore = useCallback(() => {
        if (assetsQuery.hasNextPage && !assetsQuery.isFetchingNextPage) void assetsQuery.fetchNextPage()
    }, [assetsQuery])
    const referenceLabel = useCallback((reference: SourceReferenceDetails) => {
        const sourceValue = sourceById.get(reference.sourceId)
        return [sourceValue?.title, reference.pageFrom ? `${t('curator.fields.page')} ${reference.pageFrom}` : null].filter(Boolean).join(' · ')
    }, [sourceById, t])
    const rightsFields = useMemo<ArchiveField[]>(() => [
        {
            name: 'sourceReferenceId', label: t('admin.archive.fields.sourceReferenceId'), section: t('admin.archive.sections.links'), kind: 'select', nullable: true,
            options: references.map(reference => ({ value: String(reference.id), label: `#${reference.id} - ${referenceLabel(reference)}` })),
        },
        { name: 'description', label: t('admin.archive.fields.description'), section: t('admin.archive.sections.details'), kind: 'textarea', nullable: true },
        {
            name: 'rightsStatus', label: t('admin.archive.fields.rightsStatus'), section: t('admin.archive.sections.rights'), kind: 'select', required: true, nullable: false, defaultValue: 'UNKNOWN',
            options: ['UNKNOWN', 'PUBLIC_DOMAIN', 'LICENSED', 'RESTRICTED'].map(value => ({ value, label: apiEnumLabel(t, 'rightsStatus', value) })),
            helperText: t('admin.archive.rights.helpTooltip'),
        },
        { name: 'license', label: t('admin.archive.fields.license'), section: t('admin.archive.sections.rights'), nullable: true, visibleWhen: values => values.rightsStatus === 'LICENSED', requiredWhen: values => values.rightsStatus === 'LICENSED', helperText: t('admin.archive.rights.licenseHelp') },
        { name: 'publicDisplayAllowed', label: t('admin.archive.fields.publicDisplayAllowed'), section: t('admin.archive.sections.rights'), kind: 'boolean', nullable: false, defaultValue: false, disabledWhen: values => values.rightsStatus === 'UNKNOWN' || values.rightsStatus === 'RESTRICTED', helperText: t('admin.archive.rights.publicDisplayHelp') },
    ], [referenceLabel, references, t])
    const rightsWarnings = useCallback((values: ArchiveFormValues) => {
        if (!values.publicDisplayAllowed || !values.sourceReferenceId) return []
        const reference = referenceById.get(Number(values.sourceReferenceId))
        const linkedSource = reference ? sourceById.get(reference.sourceId) : null
        const publiclyCleared = linkedSource?.publicDisplayAllowed
            && (linkedSource.rightsStatus === 'PUBLIC_DOMAIN' || linkedSource.rightsStatus === 'LICENSED')
        return publiclyCleared ? [] : [t('admin.archive.rights.linkedSourceWarning')]
    }, [referenceById, sourceById, t])

    async function saveRights(input: ArchiveAdminWriteDto) {
        if (!rightsEditing || rightsSaving) return
        const values = input as unknown as Record<string, string | boolean | null | undefined>
        setRightsSaving(true)
        setRightsError(null)
        try {
            const updated = await mediaAssetsApi.update(rightsEditing.id, {
                sourceReferenceId: values.sourceReferenceId ? Number(values.sourceReferenceId) : null,
                description: values.description ? String(values.description) : null,
                rightsStatus: String(values.rightsStatus) as MediaAssetDetails['rightsStatus'],
                license: values.license ? String(values.license) : null,
                publicDisplayAllowed: Boolean(values.publicDisplayAllowed),
            } as MediaAssetWriteDto)
            setSelected(current => current?.id === updated.id ? updated : current)
            setRightsEditing(null)
            await queryClient.invalidateQueries({ queryKey: mediaQueryKey })
        } catch (caught) {
            setRightsError(apiErrorMessage(caught))
        } finally {
            setRightsSaving(false)
        }
    }

    async function remove() {
        if (!deleting || deletePending) return
        setDeletePending(true)
        try {
            if (deleting.documentFigure) {
                const figure = (await listPageFigures(deleting.documentFigure.documentPageId))
                    .find(item => item.id === deleting.documentFigure?.figureId)
                if (!figure) {
                    setError(t('curator.mediaLibrary.figureNotFound'))
                    setDeleting(null)
                    return
                }
                await deletePageFigure(figure.documentPageId, figure.id, figure.version)
                await queryClient.invalidateQueries({ queryKey: documentQueryKeys.detail(deleting.documentFigure.documentId) })
            } else {
                await mediaAssetsApi.remove(deleting.id)
            }
            if (selected?.id === deleting.id) setSelected(null)
            setDeleting(null)
            await queryClient.invalidateQueries({ queryKey: mediaQueryKey })
        } catch (caught) {
            setError(apiErrorMessage(caught)); setDeleting(null)
        } finally { setDeletePending(false) }
    }

    const initialLoading = assetsQuery.isPending || metadataQuery.isPending
    const loadError = assetsQuery.error ?? metadataQuery.error ?? documentLinkQueries.find(result => result.error)?.error
    return <Stack spacing={3}>
        <AdminPageHeader title={t('curator.mediaLibrary.title')} description={t('curator.mediaLibrary.description')} actions={<Button variant="contained" startIcon={<UploadOutlinedIcon />} onClick={() => setUploadOpen(true)}>{t('curator.media.upload')}</Button>} />
        <Paper variant="outlined" sx={{ p: 2 }}><Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', md: 'minmax(220px, 1.6fr) repeat(3, minmax(130px, 1fr)) auto' }, gap: 1.5, alignItems: 'center' }}>
            <TextField size="small" fullWidth value={query} onChange={event => setQuery(event.target.value)} placeholder={t('curator.mediaLibrary.search')} slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
            <FormSelectField name="media-type-filter" size="small" label={t('curator.mediaLibrary.type')} value={type} onChange={event => setType(event.target.value)} options={[{ value: '', label: t('admin.allCategories') }, ...['IMAGE', 'PDF', 'SCAN', 'THUMBNAIL', 'OTHER'].map(value => ({ value, label: apiEnumLabel(t, 'mediaType', value) }))]} />
            <FormSelectField name="media-usage-filter" size="small" label={t('curator.mediaLibrary.usage')} value={usage} onChange={event => setUsage(event.target.value)} options={[{ value: '', label: t('admin.allCategories') }, ...['used', 'unused'].map(value => ({ value, label: t(`curator.mediaLibrary.${value}`) }))]} />
            <FormSelectField name="media-source-filter" size="small" label={t('curator.fields.source')} value={source} onChange={event => setSource(event.target.value)} options={[{ value: '', label: t('admin.allCategories') }, ...sources.map(value => ({ value: String(value.id), label: value.title }))]} />
            <ToggleButtonGroup exclusive size="small" value={view} onChange={(_, next) => next && setView(next)}><ToggleButton value="grid" aria-label={t('curator.mediaLibrary.gridView')}><AppsOutlinedIcon /></ToggleButton><ToggleButton value="list" aria-label={t('curator.mediaLibrary.listView')}><ListOutlinedIcon /></ToggleButton></ToggleButtonGroup>
        </Box></Paper>
        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
        {archiveSelection.length > 0 && <Stack direction="row" spacing={1}>
            <Button variant="contained" onClick={() => setArchiveImages([...archiveSelection])}>{i18n.resolvedLanguage === 'en' ? `Create archive entry (${archiveSelection.length})` : `Създай архивен запис (${archiveSelection.length})`}</Button>
            <Button onClick={() => setArchiveSelection([])}>{t('admin.cancel')}</Button>
        </Stack>}
        {loadError && <Alert severity="error">{apiErrorMessage(loadError)}</Alert>}
        {!initialLoading && !assetsQuery.isFetchingNextPage && filtered.length === 0 && <Paper variant="outlined" sx={{ p: 5, textAlign: 'center' }}><Typography color="text.secondary">{t('curator.mediaLibrary.empty')}</Typography></Paper>}
        <Box sx={{ display: 'grid', gridTemplateColumns: view === 'grid' ? { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(3, minmax(0, 1fr))', xl: 'repeat(4, minmax(0, 1fr))' } : '1fr', gap: 2 }}>
            {initialLoading && Array.from({ length: 8 }, (_, index) => <MediaCardSkeleton key={index} view={view} />)}
            {filtered.map(asset => <Box key={asset.id} sx={{ position: 'relative', minWidth: 0 }}>
                {canEditRights && asset.mediaType !== 'PDF' && <Checkbox checked={archiveSelection.some(value => value.id === asset.id)} onChange={(_, checked) => setArchiveSelection(current => checked ? [...current, asset] : current.filter(value => value.id !== asset.id))} slotProps={{ input: { 'aria-label': `${i18n.resolvedLanguage === 'en' ? 'Select image' : 'Избери изображение'} ${asset.fileName}` } }} sx={{ position: 'absolute', top: 4, left: 4, zIndex: 1, bgcolor: 'background.paper' }} />}
                <MediaCard asset={asset} view={view} sourceTitle={sourceFor(asset)?.title ?? null} documentTitle={asset.documentFigure ? documentById.get(asset.documentFigure.documentId)?.title ?? null : null} usageCount={usageCount(asset.id)} language={i18n.resolvedLanguage ?? 'bg'} onOpen={() => setSelected(asset)} onDelete={() => setDeleting(asset)} />
            </Box>)}
            {assetsQuery.isFetchingNextPage && Array.from({ length: filterActive ? 4 : 8 }, (_, index) => <MediaCardSkeleton key={`next-${index}`} view={view} />)}
        </Box>
        <InfiniteScrollTrigger enabled={!filterActive && Boolean(assetsQuery.hasNextPage)} loading={false} onLoadMore={loadMore} />
        {archiveImages && <ArchiveEditorPage itemId={null} embedded initialMedia={archiveImages} onClose={() => setArchiveImages(null)} onSaved={() => { setArchiveImages(null); setArchiveSelection([]); void queryClient.invalidateQueries({ queryKey: mediaQueryKey }) }} />}
        <MediaUploadDialog open={uploadOpen} category="archive" sourceReferences={references} sources={sources} sourceReferenceLabel={referenceLabel} onClose={() => setUploadOpen(false)} onUploaded={() => { void queryClient.invalidateQueries({ queryKey: mediaQueryKey }) }} />
        <AdminModal open={Boolean(selected)} onClose={() => setSelected(null)} maxWidth="md" title={selected?.fileName ?? t('curator.media.unnamed')} actions={selected && <><Button startIcon={<EditOutlinedIcon />} disabled={!canEditRights} onClick={() => { setRightsError(null); setRightsEditing(selected) }}>{t('curator.mediaLibrary.editRights')}</Button><Button color="error" startIcon={<DeleteOutlineIcon />} disabled={!canDeleteMediaAsset(selected, usageCount(selected.id))} onClick={() => setDeleting(selected)}>{selected.documentFigure ? t('curator.mediaLibrary.deleteFigure') : t('admin.delete')}</Button><Button onClick={() => setSelected(null)}>{t('curator.actions.close')}</Button></>}>
            {selected && <Stack spacing={2}>{selected.mediaType === 'PDF' ? <Box sx={{ minHeight: 220, bgcolor: 'background.default', border: '1px solid', borderColor: 'divider', display: 'grid', placeItems: 'center' }}><Stack spacing={1.5} sx={{ alignItems: 'center' }}><PictureAsPdfOutlinedIcon color="action" sx={{ fontSize: 58 }} /><Button variant="contained" startIcon={<VisibilityOutlinedIcon />} onClick={() => setPdfPreview(selected)}>{t('pdfViewer.preview')}</Button></Stack></Box> : <AdminMediaImage asset={selected} />}
                {selected.documentFigure && <Paper variant="outlined" sx={{ p: 2 }}><Stack spacing={1}><Chip size="small" color="success" label={t('curator.mediaFigures.badge')} sx={{ alignSelf: 'flex-start' }} /><Typography variant="h6" sx={{ fontWeight: 800 }}>{selected.documentFigure.caption || t('documents.figures.captionMissing')}</Typography><Typography color="text.secondary">{t('curator.mediaFigures.documentContext', { document: documentById.get(selected.documentFigure.documentId)?.title ?? `#${selected.documentFigure.documentId}`, page: selected.documentFigure.pageSequence })}</Typography>{selected.documentFigure.printedFigureNumber && <Typography variant="body2">{selected.documentFigure.printedFigureNumber}</Typography>}{selected.documentFigure.sourceReferenceId && <Typography variant="body2">{t('curator.mediaFigures.citation')}: {referenceById.has(selected.documentFigure.sourceReferenceId) ? referenceLabel(referenceById.get(selected.documentFigure.sourceReferenceId)!) : `#${selected.documentFigure.sourceReferenceId}`}</Typography>}<Button component={Link} to={`/management/documents/${selected.documentFigure.documentId}?tab=figures&pageId=${selected.documentFigure.documentPageId}&figureId=${selected.documentFigure.figureId}`} startIcon={<LinkOutlinedIcon />} sx={{ alignSelf: 'flex-start' }}>{t('curator.mediaFigures.openSource')}</Button></Stack></Paper>}
                <MediaOntologyLinksEditor mediaAssetId={selected.id} canEdit={canEditOntologyLinks} />
                {!selected.documentFigure && <Typography>{selected.description}</Typography>}<Alert severity={usageCount(selected.id) ? 'info' : 'warning'}>{usageCount(selected.id) ? t('curator.mediaLibrary.usageCount', { count: usageCount(selected.id) }) : t('curator.mediaLibrary.unusedWarning')}</Alert>{itemLinks.filter(link => link.mediaAssetId === selected.id).map(link => <Typography key={link.id} variant="body2">{t('curator.mediaLibrary.archiveUsage', { id: link.archiveItemId })}</Typography>)}{documentLinks.filter(link => link.mediaAssetId === selected.id).map(link => <Button key={link.documentPageMediaId} component={Link} to={`/management/documents/${link.documentId}?tab=pages`} variant="text" sx={{ alignSelf: 'flex-start' }}>{t('curator.mediaLibrary.documentPageUsage', { documentId: link.documentId, pageId: link.documentPageId })}</Button>)}{entityLinks.filter(link => link.mediaAssetId === selected.id).map(link => <Typography key={link.id} variant="body2">{link.description ?? link.entityType}</Typography>)}{!selected.documentFigure && <Alert icon={<WarningAmberOutlinedIcon />} severity="info">{t('curator.mediaLibrary.replaceUnavailable')}</Alert>}</Stack>}
        </AdminModal>
        <ArchiveRecordDialog
            key={`media-rights:${rightsEditing?.id ?? 'closed'}`}
            open={Boolean(rightsEditing)}
            title={t('curator.mediaLibrary.editRightsTitle')}
            fields={rightsFields}
            record={rightsEditing}
            saving={rightsSaving}
            error={rightsError}
            warnings={rightsWarnings}
            intro={<RightsGuidance />}
            onClose={() => setRightsEditing(null)}
            onSubmit={saveRights}
        />
        {pdfPreview && <AdminMediaPdfDialog asset={pdfPreview} onClose={() => setPdfPreview(null)} />}
        <ConfirmDialog open={Boolean(deleting)} title={deleting?.documentFigure ? t('curator.mediaLibrary.deleteFigureTitle') : t('admin.confirmDelete')} confirmLabel={t('admin.delete')} pending={deletePending} onCancel={() => setDeleting(null)} onConfirm={remove}>{deleting?.documentFigure ? t('curator.mediaLibrary.deleteFigureText', { name: deleting.documentFigure.caption ?? deleting.fileName }) : t('admin.confirmDeleteText', { name: deleting?.fileName })}</ConfirmDialog>
    </Stack>
}

type MediaCardProps = {
    asset: MediaAssetDetails
    view: 'grid' | 'list'
    sourceTitle: string | null
    documentTitle: string | null
    usageCount: number
    language: string
    onOpen: () => void
    onDelete: () => void
}

function MediaCard({ asset, view, sourceTitle, documentTitle, usageCount, language, onOpen, onDelete }: MediaCardProps) {
    const { t } = useTranslation()
    return <Paper variant="outlined" sx={{ overflow: 'hidden', display: view === 'list' ? 'flex' : 'block', minWidth: 0, transition: 'box-shadow 150ms', '&:hover': { boxShadow: 2 } }}>
        <Box onClick={onOpen} sx={{ width: view === 'list' ? 190 : '100%', aspectRatio: '16/10', bgcolor: 'grey.100', display: 'grid', placeItems: 'center', cursor: 'pointer', overflow: 'hidden', flexShrink: 0 }}>
            {asset.mediaType === 'IMAGE' || asset.mediaType === 'THUMBNAIL' || asset.mediaType === 'SCAN' ? <MediaThumbnail asset={asset} /> : <PictureAsPdfOutlinedIcon color="action" sx={{ fontSize: 50 }} />}
        </Box>
        <Stack sx={{ p: 2, minWidth: 0, flex: 1 }} spacing={1}>
            <Typography sx={{ fontWeight: 700 }} noWrap title={asset.documentFigure?.caption ?? asset.fileName ?? undefined}>{asset.documentFigure?.caption ?? asset.fileName ?? t('curator.media.unnamed')}</Typography>
            <Typography variant="body2" color="text.secondary" noWrap>{asset.documentFigure ? t('curator.mediaFigures.documentContext', { document: documentTitle ?? `#${asset.documentFigure.documentId}`, page: asset.documentFigure.pageSequence }) : sourceTitle ?? asset.description ?? t('curator.mediaLibrary.noDescription')}</Typography>
            <Typography variant="caption" color="text.secondary">{formatBytes(asset.sizeBytes, language)}{asset.width && asset.height ? ` · ${asset.width} × ${asset.height}` : ''}</Typography>
            <Stack direction="row" sx={{ gap: .75, alignItems: 'center', flexWrap: 'wrap' }}>{asset.documentFigure && <Chip size="small" color="success" label={t('curator.mediaFigures.badge')} />}<Chip size="small" label={apiEnumLabel(t, 'mediaType', asset.mediaType)} /><Chip size="small" variant="outlined" label={t('curator.mediaLibrary.usageCount', { count: usageCount })} /></Stack>
            <Stack direction="row" sx={{ justifyContent: 'flex-end', mt: 'auto' }}><Button size="small" onClick={onOpen}>{t('curator.actions.details')}</Button><Tooltip title={asset.documentFigure ? t('curator.mediaLibrary.deleteFigure') : usageCount ? t('curator.mediaLibrary.inUseWarning') : t('admin.delete')}><span><Button size="small" color="error" disabled={!canDeleteMediaAsset(asset, usageCount)} onClick={onDelete} aria-label={asset.documentFigure ? t('curator.mediaLibrary.deleteFigure') : t('admin.delete')}><DeleteOutlineIcon fontSize="small" /></Button></span></Tooltip></Stack>
        </Stack>
    </Paper>
}

function MediaCardSkeleton({ view }: { view: 'grid' | 'list' }) {
    return <Paper variant="outlined" sx={{ overflow: 'hidden', display: view === 'list' ? 'flex' : 'block' }}>
        <Skeleton variant="rectangular" animation="wave" sx={{ width: view === 'list' ? 190 : '100%', aspectRatio: '16/10', flexShrink: 0 }} />
        <Stack spacing={1} sx={{ p: 2, flex: 1 }}><Skeleton width="72%" /><Skeleton width="48%" /><Skeleton width="35%" /><Stack direction="row" spacing={1}><Skeleton variant="rounded" width={78} height={24} /><Skeleton variant="rounded" width={70} height={24} /></Stack></Stack>
    </Paper>
}

function MediaThumbnail({ asset }: { asset: MediaAssetDetails }) {
    return <AdminMediaThumbnail mediaAssetId={asset.id} sx={{ width: '100%', height: '100%' }} />
}

function AdminMediaImage({ asset }: { asset: MediaAssetDetails }) {
    const { t } = useTranslation()
    const content = useAdminMediaContent(asset.id)
    if (content.isPending) return <Box sx={{ minHeight: 220, display: 'grid', placeItems: 'center' }}><CircularProgress /></Box>
    if (content.isError || !content.url) return <Alert severity="error">{apiErrorMessage(content.error)}</Alert>
    return <PreviewableImage src={content.url} alt={asset.documentFigure?.caption ?? asset.fileName ?? t('curator.media.unnamed')} caption={asset.documentFigure?.caption ?? asset.description} buttonSx={{ maxWidth: '100%', alignSelf: 'center' }} imageSx={{ maxWidth: '100%', maxHeight: 560, objectFit: 'contain' }} />
}

function AdminMediaPdfDialog({ asset, onClose }: { asset: MediaAssetDetails, onClose: () => void }) {
    const { t } = useTranslation()
    const content = useQuery({
        queryKey: [...mediaQueryKey, 'content', asset.id],
        queryFn: ({ signal }) => getAdminMediaContent(asset.id, signal),
        staleTime: 60_000,
    })
    if (content.isPending) return <AdminModal open title={asset.fileName ?? t('curator.media.unnamed')} onClose={onClose}><Box sx={{ minHeight: 240, display: 'grid', placeItems: 'center' }}><CircularProgress /></Box></AdminModal>
    if (content.isError) return <AdminModal open title={asset.fileName ?? t('curator.media.unnamed')} onClose={onClose}><Alert severity="error">{apiErrorMessage(content.error)}</Alert></AdminModal>

    const file = new File([content.data], asset.fileName ?? 'media.pdf', { type: content.data.type || 'application/pdf' })
    return <PdfViewerDialog open source={file} title={asset.fileName ?? t('curator.media.unnamed')} downloadName={asset.fileName ?? undefined} onClose={onClose} />
}

function formatBytes(value: number | null, language: string) {
    if (value == null) return '—'
    return new Intl.NumberFormat(language, { style: 'unit', unit: value >= 1_000_000 ? 'megabyte' : 'kilobyte', unitDisplay: 'short', maximumFractionDigits: 1 }).format(value / (value >= 1_000_000 ? 1_000_000 : 1_000))
}

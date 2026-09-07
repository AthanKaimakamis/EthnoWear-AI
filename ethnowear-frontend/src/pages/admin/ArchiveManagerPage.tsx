import { useEffect, useMemo, useState } from 'react'
import {
    Alert, Box, Button, Checkbox, FormControlLabel, InputAdornment,
    LinearProgress, Pagination, Paper, Stack, TextField,
    Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import ContentCopyOutlinedIcon from '@mui/icons-material/ContentCopyOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import OpenInNewOutlinedIcon from '@mui/icons-material/OpenInNewOutlined'
import SearchIcon from '@mui/icons-material/Search'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import {
    archiveItemMediaApi, archiveItemsApi, getPublicationReadiness, mediaAssetsApi,
    runPublicationCommand, sourceReferencesApi, sourcesApi, type PublicationCommand,
} from '../../api/ArchiveAdminApi'
import { getFullReference } from '../../api/ReferenceApi'
import type {
    ArchiveItemDetails, ArchiveItemMediaDetails, ArchiveType, MediaAssetDetails,
    PublicationStatus, SourceDetails, SourceReferenceDetails, TrustedLevel,
} from '../../types/archive'
import { ARCHIVE_TYPES } from '../../types/archive'
import type { ReferenceResource } from '../../types/reference'
import AdminPageHeader from '../../components/admin/AdminPageHeader'
import ArchiveStatusChip from '../../components/admin/ArchiveStatusChip'
import ArchiveWorkflowActions from '../../components/admin/ArchiveWorkflowActions'
import TrustedLevelChip from '../../components/admin/TrustedLevelChip'
import { publicationErrorMessages, workflowPermissionsForRoles } from '../../components/admin/archiveWorkflow'
import ArchiveEditorPage from './ArchiveEditorPage'
import ArchiveItemPreviewDialog from '../../components/admin/ArchiveItemPreviewDialog'
import FormSelectField from '../../components/forms/FormSelectField'
import { invalidatePublicQueries } from '../../app/queryClient'
import { useAdminAuth } from '../../app/adminAuth'
import { apiErrorMessage } from '../../api/http'
import AdminMediaThumbnail from '../../components/admin/media/AdminMediaThumbnail'

type Filters = {
    archiveType: string
    region: string
    embroidery: string
    trusted: string
    publication: string
    source: string
    missingImage: boolean
    missingSource: boolean
}

const initialFilters: Filters = { archiveType: '', region: '', embroidery: '', trusted: '', publication: '', source: '', missingImage: false, missingSource: false }

function title(item: ArchiveItemDetails) { return item.titleBg ?? item.titleEn ?? `Архивен запис #${item.id}` }

export default function ArchiveManagerPage() {
    const { t, i18n } = useTranslation()
    const { admin } = useAdminAuth()
    const workflowPermissions = workflowPermissionsForRoles(admin?.roles ?? [])
    const [searchParams, setSearchParams] = useSearchParams()
    const [items, setItems] = useState<ArchiveItemDetails[]>([])
    const [sources, setSources] = useState<SourceDetails[]>([])
    const [references, setReferences] = useState<SourceReferenceDetails[]>([])
    const [media, setMedia] = useState<ArchiveItemMediaDetails[]>([])
    const [assets, setAssets] = useState<MediaAssetDetails[]>([])
    const [regions, setRegions] = useState<ReferenceResource[]>([])
    const [embroideries, setEmbroideries] = useState<ReferenceResource[]>([])
    const [regionalMotifs, setRegionalMotifs] = useState<ReferenceResource[]>([])
    const [query, setQuery] = useState('')
    const [filters, setFilters] = useState(initialFilters)
    const [page, setPage] = useState(1)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [workflowErrors, setWorkflowErrors] = useState<string[]>([])
    const [blockedItemId, setBlockedItemId] = useState<number | null>(null)
    const [pendingAction, setPendingAction] = useState<{ id: number, command: PublicationCommand } | null>(null)
    const [reloadKey, setReloadKey] = useState(0)
    const [previewItemId, setPreviewItemId] = useState<number | null>(null)
    const [editorTarget, setEditorTarget] = useState<{ itemId: number | null, duplicateFromId?: number } | null>(() => {
        if (searchParams.get('create') === '1') return { itemId: null }
        const editId = Number(searchParams.get('edit'))
        return Number.isInteger(editId) && editId > 0 ? { itemId: editId } : null
    })
    const pageSize = 10

    useEffect(() => {
        const controller = new AbortController()
        Promise.all([
            archiveItemsApi.findAll({ size: 1000, sort: 'updatedAt,desc' }, controller.signal),
            sourcesApi.findAll({ size: 1000 }, controller.signal), sourceReferencesApi.findAll({ size: 1000 }, controller.signal),
            archiveItemMediaApi.findAll({ size: 1000 }, controller.signal), mediaAssetsApi.findAll({ size: 1000 }, controller.signal),
            getFullReference(i18n.resolvedLanguage === 'en' ? 'en' : 'bg'),
        ]).then(([itemPage, sourcePage, referencePage, mediaPage, assetPage, reference]) => {
            setItems(itemPage.content); setSources(sourcePage.content); setReferences(referencePage.content)
            setMedia(mediaPage.content); setAssets(assetPage.content); setRegions(reference.regions); setEmbroideries(reference.regionalEmbroideryTypes); setRegionalMotifs(reference.regionalMotifTypes)
        }).catch(caught => { if (!(caught instanceof DOMException && caught.name === 'AbortError')) setError(apiErrorMessage(caught)) })
            .finally(() => setLoading(false))
        return () => controller.abort()
    }, [i18n.resolvedLanguage, reloadKey])

    const labels = useMemo(() => new Map([...regions, ...embroideries, ...regionalMotifs].map(resource => [resource.localName, resource.label])), [regions, embroideries, regionalMotifs])
    const sourceByReference = useMemo(() => new Map(references.map(reference => [reference.id, sources.find(source => source.id === reference.sourceId)])), [references, sources])
    const mediaByItem = useMemo(() => {
        const result = new Map<number, ArchiveItemMediaDetails[]>()
        media.forEach(link => result.set(link.archiveItemId, [...(result.get(link.archiveItemId) ?? []), link]))
        return result
    }, [media])
    const assetById = useMemo(() => new Map(assets.map(asset => [asset.id, asset])), [assets])

    const filtered = useMemo(() => items.filter(item => {
        const itemMedia = mediaByItem.get(item.id) ?? []
        const source = sourceByReference.get(item.sourceReferenceId ?? 0)
        const text = [title(item), item.titleEn, item.inventoryNumber, item.originText, item.currentLocation, source?.title].join(' ').toLocaleLowerCase()
        return (!query.trim() || text.includes(query.trim().toLocaleLowerCase()))
            && (!filters.archiveType || item.archiveType === filters.archiveType)
            && (!filters.region || item.ontologyRegionLocalName === filters.region)
            && (!filters.embroidery || item.ontologyRegionalEmbroideryLocalName === filters.embroidery)
            && (!filters.trusted || item.trustedLevel === filters.trusted)
            && (!filters.publication || item.publicationStatus === filters.publication)
            && (!filters.source || source?.id === Number(filters.source))
            && (!filters.missingImage || itemMedia.length === 0)
            && (!filters.missingSource || !source)
    }), [filters, items, mediaByItem, query, sourceByReference])

    const visible = filtered.slice((page - 1) * pageSize, page * pageSize)
    const select = (key: keyof Filters, value: string | boolean) => { setFilters(current => ({ ...current, [key]: value })); setPage(1) }
    const closeEditor = () => { setEditorTarget(null); if (searchParams.size) setSearchParams({}, { replace: true }) }

    async function runWorkflow(item: ArchiveItemDetails, command: PublicationCommand) {
        setPendingAction({ id: item.id, command })
        setWorkflowErrors([])
        setBlockedItemId(null)
        try {
            if (command === 'submit' || command === 'publish') {
                const readiness = await getPublicationReadiness(item.id)
                if (!readiness.ready) {
                    setWorkflowErrors(readiness.requirements
                        .filter(requirement => !requirement.satisfied)
                        .map(requirement => requirement.message ?? t(`publication.requirementMessages.${requirement.key}`)))
                    setBlockedItemId(item.id)
                    return
                }
            }
            const updated = await runPublicationCommand(item.id, command)
            setItems(current => current.map(candidate => candidate.id === updated.id ? updated : candidate))
            void invalidatePublicQueries()
        } catch (caught) {
            setWorkflowErrors(publicationErrorMessages(caught, t('publication.errors.command'), t))
            setBlockedItemId(item.id)
        } finally {
            setPendingAction(null)
        }
    }

    return (
        <Stack spacing={3}>
            <AdminPageHeader title={t('curator.archive.title')} description={t('curator.archive.description')}
                actions={<Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditorTarget({ itemId: null })}>{t('curator.archive.new')}</Button>} />
            <Paper variant="outlined" sx={{ p: 2 }}>
                <Stack spacing={2}>
                    <TextField fullWidth size="small" value={query} onChange={event => { setQuery(event.target.value); setPage(1) }} placeholder={t('curator.archive.search')}
                        slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
                    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(3, minmax(140px, 1fr))' }, gap: 1.5 }}>
                        <FilterSelect label={t('curator.fields.archiveType')} value={filters.archiveType} onChange={value => select('archiveType', value)} options={ARCHIVE_TYPES.map(value => ({ value, label: t(`archiveDetails.types.${value}`) }))} />
                        <FilterSelect label={t('curator.fields.region')} value={filters.region} onChange={value => select('region', value)} options={regions.map(resource => ({ value: resource.localName, label: resource.label }))} />
                        <FilterSelect label={t('curator.fields.embroidery')} value={filters.embroidery} onChange={value => select('embroidery', value)} options={embroideries.map(resource => ({ value: resource.localName, label: resource.label }))} />
                        <FilterSelect label={t('curator.fields.publicationStatus')} value={filters.publication} onChange={value => select('publication', value)} options={(['DRAFT', 'IN_REVIEW', 'PUBLISHED', 'ARCHIVED'] as PublicationStatus[]).map(value => ({ value, label: t(`publication.status.${value}`) }))} />
                        <FilterSelect label={t('curator.fields.trustedLevel')} value={filters.trusted} onChange={value => select('trusted', value)} options={(['VERIFIED', 'LIKELY', 'UNVERIFIED'] as TrustedLevel[]).map(value => ({ value, label: t(`archiveDetails.trust.${value}`) }))} />
                        <FilterSelect label={t('curator.fields.source')} value={filters.source} onChange={value => select('source', value)} options={sources.map(source => ({ value: String(source.id), label: source.title }))} />
                    </Box>
                    <Stack direction="row" sx={{ gap: 2, flexWrap: 'wrap' }}>
                        <FormControlLabel control={<Checkbox checked={filters.missingImage} onChange={event => select('missingImage', event.target.checked)} />} label={t('curator.filters.missingImage')} />
                        <FormControlLabel control={<Checkbox checked={filters.missingSource} onChange={event => select('missingSource', event.target.checked)} />} label={t('curator.filters.missingSource')} />
                        {Object.values(filters).some(Boolean) && <Button size="small" onClick={() => setFilters(initialFilters)}>{t('filters.clearShort')}</Button>}
                    </Stack>
                </Stack>
            </Paper>
            {loading && <LinearProgress />}
            {error && <Alert severity="error" action={<Button color="inherit" onClick={() => window.location.reload()}>{t('errorPage.retry')}</Button>}>{error}</Alert>}
            {workflowErrors.length > 0 && (
                <Alert severity="error" action={blockedItemId && <Button color="inherit" onClick={() => setEditorTarget({ itemId: blockedItemId })}>{t('publication.actions.open-review')}</Button>}>
                    {workflowErrors.map(message => <Typography key={message} variant="body2">{message}</Typography>)}
                </Alert>
            )}
            {!loading && !error && visible.length === 0 && <Paper variant="outlined" sx={{ p: 5, textAlign: 'center' }}><Typography color="text.secondary">{t('curator.archive.empty')}</Typography></Paper>}
            <Stack spacing={1}>
                {visible.map(item => {
                    const links = mediaByItem.get(item.id) ?? []
                    const primary = links.find(link => link.role === 'PRIMARY') ?? links[0]
                    const asset = primary ? assetById.get(primary.mediaAssetId) : undefined
                    const source = sourceByReference.get(item.sourceReferenceId ?? 0)
                    return (
                        <Paper key={item.id} variant="outlined" role="button" tabIndex={0} aria-label={`${t('curator.archive.preview')}: ${title(item)}`}
                            onClick={() => setPreviewItemId(item.id)}
                            onKeyDown={event => { if (event.target === event.currentTarget && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); setPreviewItemId(item.id) } }}
                            sx={{ p: 1.5, cursor: 'pointer', transition: 'border-color 120ms, background-color 120ms', '&:hover': { borderColor: 'primary.main', bgcolor: '#FCF8F8' }, '&:focus-visible': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 2 } }}>
                            <Stack direction="row" sx={{ gap: 2, alignItems: 'center' }}>
                                <AdminMediaThumbnail mediaAssetId={asset?.id} alt={asset?.fileName ?? ''} sx={{ width: 84, height: 68, borderRadius: 1, flexShrink: 0 }} />
                                <Box sx={{ minWidth: 0, flex: 1 }}>
                                    <Stack direction="row" sx={{ gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                                        <Typography sx={{ fontWeight: 700 }}>{title(item)}</Typography>
                                        <ArchiveStatusChip value={item.publicationStatus} />
                                        <TrustedLevelChip value={item.trustedLevel} />
                                    </Stack>
                                    <Typography variant="body2" color="text.secondary" noWrap>{t(`archiveDetails.types.${item.archiveType as ArchiveType}`)} · {labels.get(item.ontologyRegionalMotifLocalName ?? '') ?? labels.get(item.ontologyRegionalEmbroideryLocalName ?? '') ?? labels.get(item.ontologyRegionLocalName ?? '') ?? t('curator.archive.unclassified')}</Typography>
                                    <Typography variant="caption" color="text.secondary">{source?.title ?? t('curator.archive.noSource')} · {new Date(item.updatedAt).toLocaleDateString(i18n.resolvedLanguage)}</Typography>
                                </Box>
                                <Stack direction="row" sx={{ alignItems: 'center', gap: .5, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                                    {item.publicationStatus === 'DRAFT' && workflowPermissions.edit && <Tooltip title={t('admin.edit')}><Button size="small" startIcon={<EditOutlinedIcon />} onClick={event => { event.stopPropagation(); setEditorTarget({ itemId: item.id }) }}>{t('admin.edit')}</Button></Tooltip>}
                                    <Tooltip title={t('curator.archive.preview')}><Button size="small" startIcon={<OpenInNewOutlinedIcon />} onClick={event => { event.stopPropagation(); setPreviewItemId(item.id) }}>{t('curator.archive.preview')}</Button></Tooltip>
                                    {workflowPermissions.edit && <Button size="small" startIcon={<ContentCopyOutlinedIcon />} onClick={event => { event.stopPropagation(); setEditorTarget({ itemId: null, duplicateFromId: item.id }) }}>{t('archiveFast.duplicate')}</Button>}
                                    <Box onClick={event => event.stopPropagation()}>
                                        <ArchiveWorkflowActions
                                            status={item.publicationStatus}
                                            pendingCommand={pendingAction?.id === item.id ? pendingAction.command : null}
                                            disabled={pendingAction !== null}
                                            permissions={workflowPermissions}
                                            onCommand={command => void runWorkflow(item, command)}
                                        />
                                    </Box>
                                    {item.publicationStatus === 'PUBLISHED' && (
                                        <Button component={Link} to={`/archive/items/${item.id}`} size="small" startIcon={<OpenInNewOutlinedIcon />} onClick={event => event.stopPropagation()}>
                                            {t('publication.actions.open-public')}
                                        </Button>
                                    )}
                                </Stack>
                            </Stack>
                        </Paper>
                    )
                })}
            </Stack>
            {filtered.length > pageSize && <Pagination count={Math.ceil(filtered.length / pageSize)} page={page} onChange={(_, next) => setPage(next)} sx={{ alignSelf: 'center' }} />}
            {previewItemId !== null && <ArchiveItemPreviewDialog itemId={previewItemId} onClose={() => setPreviewItemId(null)} onEdit={() => { const itemId = previewItemId; setPreviewItemId(null); setEditorTarget({ itemId }) }} />}
            {editorTarget && <ArchiveEditorPage key={`${editorTarget.itemId ?? 'new'}-${editorTarget.duplicateFromId ?? ''}`} itemId={editorTarget.itemId} duplicateFromId={editorTarget.duplicateFromId} embedded
                permissions={workflowPermissions}
                onClose={closeEditor}
                onSaved={() => { closeEditor(); setReloadKey(value => value + 1) }}
                onPreview={editorTarget.itemId ? () => setPreviewItemId(editorTarget.itemId) : undefined} />}
        </Stack>
    )
}

function FilterSelect({ label, value, options, onChange }: { label: string, value: string, options: { value: string, label: string }[], onChange: (value: string) => void }) {
    const { t } = useTranslation()
    return <FormSelectField name={`filter-${label}`} label={label} size="small" value={value}
        options={[{ value: '', label: t('admin.allCategories') }, ...options]}
        onChange={event => onChange(event.target.value)} />
}

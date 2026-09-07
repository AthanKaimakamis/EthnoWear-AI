import { archiveMediaDefaults, primaryCaption } from '../../components/admin/archive-editor/archiveMediaDefaults'
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
    Alert, AlertTitle, Box, Button, Paper,
    Stack, Tab, Tabs, Typography,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import OpenInNewOutlinedIcon from '@mui/icons-material/OpenInNewOutlined'
import SaveOutlinedIcon from '@mui/icons-material/SaveOutlined'
import { useNavigate, useParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import {
    createFullArchiveEntry, getAdminArchiveItemDetail, getPublicationReadiness,
    mediaAssetsApi, runPublicationCommand, sourceReferencesApi, sourcesApi,
    updateFullArchiveEntry, type PublicationCommand,
} from '../../api/ArchiveAdminApi'
import { getFullReference } from '../../api/ReferenceApi'
import AdminModal from '../../components/admin/AdminModal'
import AdminPageHeader from '../../components/admin/AdminPageHeader'
import ArchiveItemPreviewDialog from '../../components/admin/ArchiveItemPreviewDialog'
import ArchiveStatusChip from '../../components/admin/ArchiveStatusChip'
import ArchiveWorkflowActions from '../../components/admin/ArchiveWorkflowActions'
import MediaUploadDialog from '../../components/admin/MediaUploadDialog'
import PublicationReadinessPanel from '../../components/admin/PublicationReadinessPanel'
import TrustedLevelChip from '../../components/admin/TrustedLevelChip'
import {
    adminWorkflowPermissions, publicationErrorMessages, tabForRequirement,
    type ArchiveWorkflowPermissions,
} from '../../components/admin/archiveWorkflow'
import {
    BasicSection, ClassificationSection, DescriptionSection, MediaLibraryDialog,
    MediaSection, SourceSection, type FeatureSelections, type MediaDraft,
} from '../../components/admin/archive-editor/ArchiveEditorSections'
import SourceCreateDialog from '../../components/admin/document/SourceCreateDialog'
import SourceReferenceCreateDialog from '../../components/admin/archive-editor/SourceReferenceCreateDialog'
import FormSelectField from '../../components/forms/FormSelectField'
import ArchiveEditorSkeleton from '../../components/loading/ArchiveEditorSkeleton'
import type {
    ArchiveEntryWriteDto, ArchiveItemDetails, ArchiveItemFeatureDetails, ArchiveItemWriteDto, MediaAssetDetails,
    PublicationReadinessDetails, PublicationStatus,
    SourceDetails, SourceReferenceDetails,
} from '../../types/archive'
import type { OntologyFeatureType } from '../../types/catalogue'
import type { ReferenceResource } from '../../types/reference'
import { invalidatePublicQueries } from '../../app/queryClient'
import { changeArchiveItemField } from '../../app/regionalMotifs'
import { buildArchiveEntryPayload } from '../../components/admin/archive-editor/archiveEditorPayload'
import ImageInheritanceSummary from '../../components/admin/archive-editor/ImageInheritanceSummary'

const emptyItem: ArchiveItemWriteDto = {
    sourceReferenceId: 0,
    collectionId: null,
    inventoryNumber: null,
    titleBg: '',
    titleEn: '',
    descriptionBg: '',
    descriptionEn: '',
    archiveType: 'EMBROIDERY_SAMPLE',
    periodText: null,
    originText: null,
    currentLocation: null,
    trustedLevel: 'UNVERIFIED',
    ontologyRegionIri: null,
    ontologyRegionLocalName: null,
    ontologyRegionalEmbroideryIri: null,
    ontologyRegionalEmbroideryLocalName: null,
    ontologyRegionalMotifIri: null,
    ontologyRegionalMotifLocalName: null,
}

const emptyFeatures: FeatureSelections = {
    ORNAMENT: [],
    TECHNIQUE: [],
    COLOR: [],
}

const editorTabs = ['basic', 'media', 'review'] as const

type Props = {
    initialMedia?: MediaAssetDetails[]
    duplicateFromId?: number
    itemId?: number | null
    embedded?: boolean
    onClose?: () => void
    onSaved?: (item: ArchiveItemDetails) => void
    onPreview?: () => void
    permissions?: ArchiveWorkflowPermissions
}

export default function ArchiveEditorPage({
    itemId: itemIdOverride,
    duplicateFromId,
    initialMedia,
    embedded = false,
    onClose,
    onSaved,
    onPreview,
    permissions = adminWorkflowPermissions,
}: Props = {}) {
    const { id } = useParams()
    const itemId = itemIdOverride !== undefined ? itemIdOverride : id ? Number(id) : null
    const navigate = useNavigate()
    const { t, i18n } = useTranslation()
    const [tab, setTab] = useState(0)
    const [item, setItem] = useState<ArchiveItemWriteDto>(emptyItem)
    const [publicationStatus, setPublicationStatus] = useState<PublicationStatus>('DRAFT')
    const [features, setFeatures] = useState<FeatureSelections>(emptyFeatures)
    const [savedFeatures, setSavedFeatures] = useState<ArchiveItemFeatureDetails[]>([])
    const [media, setMedia] = useState<MediaDraft[]>(() => (initialMedia ?? []).map((asset, index) => ({ asset, role: index === 0 ? 'PRIMARY' : 'DETAIL', captionBg: asset.description ?? '', captionEn: '' })))
    const [duplicateMedia, setDuplicateMedia] = useState<MediaDraft[]>([])
    const [assets, setAssets] = useState<MediaAssetDetails[]>([])
    const [sources, setSources] = useState<SourceDetails[]>([])
    const [references, setReferences] = useState<SourceReferenceDetails[]>([])
    const [referenceData, setReferenceData] = useState<Awaited<ReturnType<typeof getFullReference>> | null>(null)
    const [readiness, setReadiness] = useState<PublicationReadinessDetails | null>(null)
    const [readinessLoading, setReadinessLoading] = useState(false)
    const [readinessErrors, setReadinessErrors] = useState<string[]>([])
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [pendingCommand, setPendingCommand] = useState<PublicationCommand | null>(null)
    const [errorMessages, setErrorMessages] = useState<string[]>([])
    const [dirty, setDirty] = useState(false)
    const [uploadOpen, setUploadOpen] = useState(false)
    const [libraryOpen, setLibraryOpen] = useState(false)
    const [previewOpen, setPreviewOpen] = useState(false)
    const [sourceCreateOpen, setSourceCreateOpen] = useState(false)
    const [citationCreateOpen, setCitationCreateOpen] = useState(false)
    const [newCitationSourceId, setNewCitationSourceId] = useState<number | null>(null)

    const loadReadiness = useCallback(async (archiveItemId: number, signal?: AbortSignal) => {
        setReadinessLoading(true)
        setReadinessErrors([])
        try {
            setReadiness(await getPublicationReadiness(archiveItemId, signal))
        } catch (caught) {
            if (!(caught instanceof DOMException && caught.name === 'AbortError')) {
                setReadiness(null)
                setReadinessErrors(publicationErrorMessages(caught, t('publication.readiness.loadFailed'), t))
            }
        } finally {
            if (!signal?.aborted) setReadinessLoading(false)
        }
    }, [t])

    useEffect(() => {
        const controller = new AbortController()
        const language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
        Promise.all([
            sourcesApi.findAll({ size: 1000 }, controller.signal),
            sourceReferencesApi.findAll({ size: 1000 }, controller.signal),
            mediaAssetsApi.findAll({ size: 1000 }, controller.signal),
            getFullReference(language),
            (itemId || duplicateFromId) ? getAdminArchiveItemDetail((itemId || duplicateFromId)!, controller.signal) : Promise.resolve(null),
        ]).then(([sourcePage, referencePage, assetPage, refs, details]) => {
            setSources(sourcePage.content)
            setReferences(referencePage.content)
            setAssets(assetPage.content)
            setReferenceData(refs)

            if (!details) return
            setItem(duplicateFromId ? { ...details.archiveItem, inventoryNumber: null, trustedLevel: 'UNVERIFIED' } : details.archiveItem)
            setPublicationStatus(duplicateFromId ? 'DRAFT' : details.archiveItem.publicationStatus)
            setSavedFeatures(duplicateFromId ? [] : details.features)
            setFeatures({
                ORNAMENT: selectedResources('ORNAMENT', details.features, refs.ornaments),
                TECHNIQUE: selectedResources('TECHNIQUE', details.features, refs.techniques),
                COLOR: selectedResources('COLOR', details.features, refs.colors),
            })
            setMedia((duplicateFromId ? [] : details.media).map(({ media: link, asset }) => ({
                id: link.id,
                asset,
                role: link.role,
                captionBg: link.captionBg ?? '',
                captionEn: link.captionEn ?? '',
            })))
            if (duplicateFromId) setDuplicateMedia(details.media.map(({ media: link, asset }) => ({ asset, role: link.role, captionBg: link.captionBg ?? '', captionEn: link.captionEn ?? '' })))
            if (!duplicateFromId) void loadReadiness(details.archiveItem.id, controller.signal)
        }).catch(caught => {
            if (!(caught instanceof DOMException && caught.name === 'AbortError')) {
                setErrorMessages(publicationErrorMessages(caught, t('publication.errors.load'), t))
            }
        }).finally(() => {
            if (!controller.signal.aborted) setLoading(false)
        })

        return () => controller.abort()
    }, [i18n.resolvedLanguage, itemId, duplicateFromId, loadReadiness, t])

    useEffect(() => {
        const warn = (event: BeforeUnloadEvent) => {
            if (dirty) event.preventDefault()
        }
        window.addEventListener('beforeunload', warn)
        return () => window.removeEventListener('beforeunload', warn)
    }, [dirty])

    const markDirty = () => {
        setDirty(true)
        setReadiness(null)
        setReadinessErrors([])
    }
    const setField = <K extends keyof ArchiveItemWriteDto>(key: K, value: ArchiveItemWriteDto[K]) => {
        setItem(current => changeArchiveItemField(current, key, value, referenceData))
        markDirty()
    }

    const sourceReferenceLabel = (reference: SourceReferenceDetails) => {
        const source = sources.find(candidate => candidate.id === reference.sourceId)
        const pages = reference.pageFrom
            ? `${t('curator.fields.page')} ${reference.pageFrom}${reference.pageTo ? `-${reference.pageTo}` : ''}`
            : reference.locator
        return [source?.title ?? `#${reference.sourceId}`, pages].filter(Boolean).join(' · ')
    }

    const resolvedItem = archiveMediaDefaults(item, media)
    const selectedReference = references.find(reference => reference.id === resolvedItem.sourceReferenceId)
    const selectedSource = selectedReference ? sources.find(source => source.id === selectedReference.sourceId) : null
    const editable = publicationStatus === 'DRAFT' && permissions.edit

    const aggregatePayload = useMemo<ArchiveEntryWriteDto>(
        () => buildArchiveEntryPayload(archiveMediaDefaults(item, media), features, savedFeatures, media),
        [features, item, media, savedFeatures],
    )

    async function saveDraft() {
        setSaving(true)
        setErrorMessages([])
        try {
            const saved = itemId
                ? await updateFullArchiveEntry(itemId, aggregatePayload)
                : await createFullArchiveEntry(aggregatePayload)
            setItem(saved.archiveItem)
            setPublicationStatus(saved.archiveItem.publicationStatus)
            setSavedFeatures(saved.features)
            setMedia(current => current.map(draft => ({
                ...draft,
                id: saved.media.find(link => link.mediaAssetId === draft.asset.id)?.id,
            })))
            setDirty(false)
            void invalidatePublicQueries()
            await loadReadiness(saved.archiveItem.id)
            onSaved?.(saved.archiveItem)
            if (!onSaved && !itemId) navigate(`/management/archive/${saved.archiveItem.id}/edit`, { replace: true })
        } catch (caught) {
            setErrorMessages(publicationErrorMessages(caught, t('publication.errors.save'), t))
        } finally {
            setSaving(false)
        }
    }

    async function runCommand(command: PublicationCommand) {
        if (!itemId || dirty) return
        setPendingCommand(command)
        setErrorMessages([])
        try {
            const updated = await runPublicationCommand(itemId, command)
            setItem(updated)
            setPublicationStatus(updated.publicationStatus)
            void invalidatePublicQueries()
            await loadReadiness(updated.id)
        } catch (caught) {
            setErrorMessages(publicationErrorMessages(caught, t('publication.errors.command'), t))
        } finally {
            setPendingCommand(null)
        }
    }

    function removeMedia(index: number) {
        setMedia(current => current.filter((_, candidate) => candidate !== index))
        markDirty()
    }

    function addAsset(asset: MediaAssetDetails) {
        if (!media.some(link => link.asset.id === asset.id)) {
            setMedia(current => [...current, {
                asset,
                role: current.length ? 'DETAIL' : 'PRIMARY',
                captionBg: i18n.resolvedLanguage === 'en' ? '' : asset.description ?? '',
                captionEn: i18n.resolvedLanguage === 'en' ? asset.description ?? '' : '',
            }])
            markDirty()
        }
        setAssets(current => current.some(candidate => candidate.id === asset.id) ? current : [...current, asset])
        setUploadOpen(false)
    }

    function closeEditor() {
        if (dirty && !window.confirm(t('curator.editor.unsavedConfirm'))) return
        onClose?.()
    }

    const showPreview = onPreview ?? (() => setPreviewOpen(true))
    const editorTitle = itemId ? t('curator.editor.editTitle') : t('curator.editor.newTitle')
    const reviewContent = (
        <Stack spacing={2.5}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: { sm: 'center' } }}>
                <Typography variant="h6">{t('publication.currentStatus')}</Typography>
                <ArchiveStatusChip value={publicationStatus} />
                <TrustedLevelChip value={item.trustedLevel} />
            </Stack>
            <FormSelectField
                name="trusted-level"
                label={t('curator.fields.trustedLevel')}
                value={item.trustedLevel}
                disabled={!editable}
                options={['VERIFIED', 'LIKELY', 'UNVERIFIED'].map(value => ({ value, label: t(`archiveDetails.trust.${value}`) }))}
                onChange={event => setField('trustedLevel', event.target.value as ArchiveItemWriteDto['trustedLevel'])}
            />
            {dirty && <Alert severity="info">{t('publication.readiness.saveChangesFirst')}</Alert>}
            <PublicationReadinessPanel
                readiness={dirty ? null : readiness}
                loading={readinessLoading}
                errorMessages={readinessErrors}
                onOpenRequirement={requirement => setTab(tabForRequirement(requirement) === 2 ? 1 : 0)}
            />
            {itemId && (
                <ArchiveWorkflowActions
                    status={publicationStatus}
                    readiness={readiness}
                    requireReadiness
                    pendingCommand={pendingCommand}
                    permissions={permissions}
                    disabled={dirty || saving}
                    onCommand={runCommand}
                />
            )}
        </Stack>
    )

    const editorContent = loading ? (
        <ArchiveEditorSkeleton />
    ) : (
        <Stack spacing={2.5}>
            {errorMessages.length > 0 && (
                <Alert severity="error" onClose={() => setErrorMessages([])}>
                    <AlertTitle>{t('publication.errors.title')}</AlertTitle>
                    {errorMessages.map(message => <Typography key={message} variant="body2">{message}</Typography>)}
                </Alert>
            )}
            {!editable && publicationStatus !== 'DRAFT' && <Alert severity="info">{t('publication.readOnly')}</Alert>}
            <Paper variant="outlined">
                <Tabs value={tab} onChange={(_, value) => setTab(value)} variant="scrollable" scrollButtons="auto" sx={{ borderBottom: 1, borderColor: 'divider', px: 1 }}>
                    {editorTabs.map(name => <Tab key={name} label={t(`curator.editor.tabs.${name}`)} />)}
                </Tabs>
                <Box component="fieldset" disabled={!editable && tab !== 2} sx={{ p: { xs: 2, md: 3 }, m: 0, minWidth: 0, border: 0, '& > * + *': { mt: 3 } }}>
                    {tab === 0 && <><BasicSection item={item} setField={setField} t={t} />{!item.titleBg?.trim() && primaryCaption(media, 'bg') && <Button onClick={() => setField('titleBg', primaryCaption(media, 'bg'))}>{t('archiveFast.useCaption')}</Button>}{!item.titleEn?.trim() && primaryCaption(media, 'en') && <Button onClick={() => setField('titleEn', primaryCaption(media, 'en'))}>{t('archiveFast.useCaption')}</Button>}</>}
                    {tab === 0 && referenceData && <ClassificationSection item={item} setField={setField} features={features} setFeatures={value => { setFeatures(value); markDirty() }} refs={referenceData} t={t} />}
                    {tab === 0 && referenceData && <ImageInheritanceSummary media={media} reference={referenceData} citationLabel={id => { const reference = references.find(value => value.id === id); return reference ? sourceReferenceLabel(reference) : t('archiveDetails.sources') }} />}
                    {tab === 1 && duplicateFromId && duplicateMedia.length > 0 && <Button variant="outlined" onClick={() => { setMedia(current => [...current, ...duplicateMedia.filter(value => !current.some(existing => existing.asset.id === value.asset.id))]); setDuplicateMedia([]); markDirty() }}>{i18n.resolvedLanguage === 'en' ? 'Include original images' : 'Включи оригиналните изображения'}</Button>}
                    {tab === 1 && <MediaSection media={media} setMedia={value => { setMedia(value); markDirty() }} onRemove={removeMedia} onUpload={() => setUploadOpen(true)} onLibrary={() => setLibraryOpen(true)} t={t} />}
                    {tab === 0 && <SourceSection references={references} sourceReferenceLabel={sourceReferenceLabel} value={resolvedItem.sourceReferenceId ?? 0} setValue={value => setField('sourceReferenceId', value)} selectedSource={selectedSource} canCreateCitation={sources.length > 0} onCreateSource={() => setSourceCreateOpen(true)} onCreateCitation={() => { setNewCitationSourceId(selectedSource?.id ?? newCitationSourceId ?? sources[0]?.id ?? null); setCitationCreateOpen(true) }} t={t} />}
                    {tab === 0 && <DescriptionSection item={item} setField={setField} t={t} />}
                    {tab === 2 && reviewContent}
                </Box>
            </Paper>
            <MediaUploadDialog open={uploadOpen} category="archive" sourceReferences={references} sources={sources} sourceReferenceLabel={sourceReferenceLabel} onClose={() => setUploadOpen(false)} onUploaded={asset => { addAsset(asset); setUploadOpen(true) }} />
            <MediaLibraryDialog open={libraryOpen} assets={assets} used={new Set(media.map(link => link.asset.id))} onClose={() => setLibraryOpen(false)} onSelect={addAsset} />
            {previewOpen && itemId && <ArchiveItemPreviewDialog itemId={itemId} onClose={() => setPreviewOpen(false)} onEdit={() => setPreviewOpen(false)} />}
            {sourceCreateOpen && (
                <SourceCreateDialog
                    initialValues={{ title: '', author: null, publisher: null, year: null, language: i18n.resolvedLanguage === 'en' ? 'en' : 'bg' }}
                    onClose={() => setSourceCreateOpen(false)}
                    onCreated={source => {
                        setSources(current => [...current, source])
                        setSourceCreateOpen(false)
                        setNewCitationSourceId(source.id)
                    }}
                />
            )}
            {citationCreateOpen && (
                <SourceReferenceCreateDialog
                    sources={sources}
                    initialSourceId={newCitationSourceId}
                    onClose={() => setCitationCreateOpen(false)}
                    onCreated={reference => {
                        setReferences(current => [...current, reference])
                        setField('sourceReferenceId', reference.id)
                        setCitationCreateOpen(false)
                    }}
                />
            )}
        </Stack>
    )

    const editorActions = (
        <>
            <Button onClick={closeEditor} disabled={saving || pendingCommand !== null}>{t('admin.cancel')}</Button>
            {itemId && <Button startIcon={<OpenInNewOutlinedIcon />} onClick={showPreview} disabled={saving}>{t('curator.archive.preview')}</Button>}
            {itemId && publicationStatus === 'PUBLISHED' && (
                <Button startIcon={<OpenInNewOutlinedIcon />} onClick={() => navigate(`/archive/items/${itemId}`)}>{t('publication.actions.open-public')}</Button>
            )}
            {editable && (
                <Button variant="contained" startIcon={<SaveOutlinedIcon />} disabled={loading || saving || pendingCommand !== null} onClick={saveDraft}>
                    {saving ? t('forms.saving') : t('publication.actions.save-draft')}
                </Button>
            )}
        </>
    )

    if (embedded) {
        return (
            <AdminModal open title={editorTitle} description={t('curator.editor.description')} onClose={closeEditor} closeDisabled={saving || pendingCommand !== null} maxWidth="xl" actions={editorActions}>
                {editorContent}
            </AdminModal>
        )
    }

    if (loading) return editorContent
    return (
        <Stack spacing={2.5}>
            <AdminPageHeader
                title={editorTitle}
                description={t('curator.editor.description')}
                actions={<><Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/management/archive')}>{t('curator.actions.back')}</Button>{editorActions}</>}
            />
            {editorContent}
        </Stack>
    )
}

function selectedResources(
    type: OntologyFeatureType,
    features: ArchiveItemFeatureDetails[],
    resources: ReferenceResource[],
) {
    return features.filter(feature => feature.featureType === type).map(feature =>
        resources.find(resource => resource.localName === feature.ontologyLocalName) ?? {
            iri: feature.ontologyIri, localName: feature.ontologyLocalName, label: feature.ontologyLocalName,
        })
}

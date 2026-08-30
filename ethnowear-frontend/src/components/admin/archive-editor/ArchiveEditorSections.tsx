import {
    Alert,
    Autocomplete,
    Avatar,
    Box,
    Button,
    Paper,
    Stack,
    TextField,
    Tooltip,
    Typography,
} from '@mui/material'
import AddPhotoAlternateOutlinedIcon from '@mui/icons-material/AddPhotoAlternateOutlined'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined'
import LinkOutlinedIcon from '@mui/icons-material/LinkOutlined'
import StarOutlinedIcon from '@mui/icons-material/StarOutlined'
import AddOutlinedIcon from '@mui/icons-material/AddOutlined'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import AdminModal from '../AdminModal'
import OntologyRelationshipField from '../OntologyRelationshipField'
import FormSelectField from '../../forms/FormSelectField'
import type { OptionCategory, SelectOption } from '../../forms/formTypes'
import type {
    ArchiveItemWriteDto,
    MediaAssetDetails,
    MediaRole,
    SourceDetails,
    SourceReferenceDetails,
} from '../../../types/archive'
import { ARCHIVE_TYPES } from '../../../types/archive'
import type { ReferenceData, ReferenceResource } from '../../../types/reference'

export type FeatureSelections = Record<
    'ORNAMENT' | 'TECHNIQUE' | 'MOTIF' | 'COLOR',
    ReferenceResource[]
>

export type MediaDraft = {
    id?: number
    asset: MediaAssetDetails
    role: MediaRole
    captionBg: string
    captionEn: string
}

export type SetArchiveItemField = <K extends keyof ArchiveItemWriteDto>(
    key: K,
    value: ArchiveItemWriteDto[K],
) => void

type SectionProps = {
    item: ArchiveItemWriteDto
    setField: SetArchiveItemField
    t: TFunction
}

export function BasicSection({ item, setField, t }: SectionProps) {
    return (
        <Stack spacing={2}>
            <Typography variant="h6">{t('curator.editor.tabs.basic')}</Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
                <TextField required={!item.titleEn?.trim()} label={t('curator.fields.titleBg')} value={item.titleBg ?? ''} onChange={event => setField('titleBg', event.target.value)} />
                <TextField required={!item.titleBg?.trim()} label={t('curator.fields.titleEn')} value={item.titleEn ?? ''} onChange={event => setField('titleEn', event.target.value)} />
                <FormSelectField
                    name="archive-type"
                    label={t('curator.fields.archiveType')}
                    value={item.archiveType}
                    options={ARCHIVE_TYPES.map(value => ({ value, label: t(`archiveDetails.types.${value}`) }))}
                    onChange={event => setField('archiveType', event.target.value as ArchiveItemWriteDto['archiveType'])}
                />
                <TextField label={t('curator.fields.inventory')} value={item.inventoryNumber ?? ''} onChange={event => setField('inventoryNumber', nullText(event.target.value))} />
                <TextField label={t('curator.fields.collection')} value={item.collectionId ?? ''} onChange={event => setField('collectionId', nullText(event.target.value))} />
                <TextField label={t('curator.fields.period')} value={item.periodText ?? ''} onChange={event => setField('periodText', nullText(event.target.value))} />
                <TextField label={t('curator.fields.origin')} value={item.originText ?? ''} onChange={event => setField('originText', nullText(event.target.value))} />
                <TextField label={t('curator.fields.location')} value={item.currentLocation ?? ''} onChange={event => setField('currentLocation', nullText(event.target.value))} />
            </Box>
        </Stack>
    )
}

export function ClassificationSection({
    item,
    setField,
    features,
    setFeatures,
    refs,
    t,
}: SectionProps & {
    features: FeatureSelections
    setFeatures: (value: FeatureSelections) => void
    refs: ReferenceData
}) {
    const single = (
        label: string,
        resources: ReferenceResource[],
        value: string | null,
        onChange: (resource: ReferenceResource | null) => void,
        categories: OptionCategory[] = [],
    ) => (
        <OntologyRelationshipField
            label={label}
            mode="single"
            options={resources.map(referenceOption)}
            categories={categories}
            value={value ? [value] : []}
            onChange={next => onChange(resources.find(resource => resource.localName === next[0]) ?? null)}
        />
    )

    const multi = (
        type: keyof FeatureSelections,
        resources: ReferenceResource[],
        categories: OptionCategory[] = [],
    ) => (
        <OntologyRelationshipField
            label={t(`curator.fields.${type.toLocaleLowerCase()}`)}
            options={resources.map(referenceOption)}
            categories={categories}
            value={features[type].map(resource => resource.localName)}
            onChange={next => {
                const selected = new Set(next)
                setFeatures({ ...features, [type]: resources.filter(resource => selected.has(resource.localName)) })
            }}
        />
    )

    const regionCategories = refs.regionGroups.map(resource => referenceCategory(resource, refs.regionsByRegionGroup))
    const embroideryByRegion = Object.entries(refs.regionByRegionalEmbroidery).reduce<Record<string, string[]>>(
        (result, [embroidery, region]) => ({ ...result, [region]: [...(result[region] ?? []), embroidery] }),
        {},
    )
    const embroideryCategories = refs.regions.map(resource => referenceCategory(resource, embroideryByRegion))
    const techniqueCategories = refs.techniqueTypes.map(resource => referenceCategory(resource, refs.techniquesByType))
    const ornamentCategories = refs.ornamentTypes.map(resource => referenceCategory(resource, refs.ornamentsByType))

    return (
        <Stack spacing={2}>
            <Alert severity="info">{t('curator.editor.identifiersHidden')}</Alert>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
                {single(t('curator.fields.region'), refs.regions, item.ontologyRegionLocalName, resource => {
                    setField('ontologyRegionLocalName', resource?.localName ?? null)
                    setField('ontologyRegionIri', resource?.iri ?? null)
                }, regionCategories)}
                {single(t('curator.fields.embroidery'), refs.regionalEmbroideryTypes, item.ontologyRegionalEmbroideryLocalName, resource => {
                    setField('ontologyRegionalEmbroideryLocalName', resource?.localName ?? null)
                    setField('ontologyRegionalEmbroideryIri', resource?.iri ?? null)
                }, embroideryCategories)}
                {multi('TECHNIQUE', refs.techniques, techniqueCategories)}
                {multi('ORNAMENT', refs.ornaments, ornamentCategories)}
                {multi('MOTIF', refs.motifs)}
                {multi('COLOR', refs.colors)}
            </Box>
        </Stack>
    )
}

function referenceOption(resource: ReferenceResource): SelectOption {
    return { value: resource.localName, label: `${resource.label} (${resource.localName})` }
}

function referenceCategory(resource: ReferenceResource, members: Record<string, string[]>): OptionCategory {
    return { ...referenceOption(resource), optionValues: members[resource.localName] ?? [] }
}

export function MediaSection({
    media,
    setMedia,
    onRemove,
    onUpload,
    onLibrary,
    t,
}: {
    media: MediaDraft[]
    setMedia: (value: MediaDraft[]) => void
    onRemove: (index: number) => void
    onUpload: () => void
    onLibrary: () => void
    t: TFunction
}) {
    const update = (index: number, change: Partial<MediaDraft>) => {
        setMedia(media.map((value, candidate) => candidate === index ? { ...value, ...change } : value))
    }
    const makePrimary = (index: number) => {
        setMedia(media.map((value, candidate) => ({
            ...value,
            role: candidate === index ? 'PRIMARY' : value.role === 'PRIMARY' ? 'DETAIL' : value.role,
        })))
    }

    return (
        <Stack spacing={2}>
            <Stack direction="row" spacing={1}>
                <Button variant="contained" startIcon={<AddPhotoAlternateOutlinedIcon />} onClick={onUpload}>{t('curator.media.upload')}</Button>
                <Button variant="outlined" startIcon={<LinkOutlinedIcon />} onClick={onLibrary}>{t('curator.media.library')}</Button>
            </Stack>
            <Alert severity="info">{t('curator.media.orderUnavailable')}</Alert>
            {media.length === 0 && (
                <Paper variant="outlined" sx={{ p: 4, textAlign: 'center' }}>
                    <Typography color="text.secondary">{t('curator.media.empty')}</Typography>
                </Paper>
            )}
            {media.map((link, index) => (
                <Paper key={`${link.asset.id}-${index}`} variant="outlined" sx={{ p: 2 }}>
                    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                        <Avatar variant="rounded" src={link.asset.mediaType === 'IMAGE' ? `/api/media/${link.asset.id}/content` : undefined} sx={{ width: 120, height: 92 }} />
                        <Stack spacing={1.5} sx={{ flex: 1 }}>
                            <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                                <Box>
                                    <Typography sx={{ fontWeight: 700 }}>{link.asset.fileName ?? t('curator.media.unnamed')}</Typography>
                                    <Typography variant="caption" color="text.secondary">{link.asset.description}</Typography>
                                </Box>
                                <Tooltip title={t('admin.delete')}>
                                    <Button color="error" onClick={() => onRemove(index)}><DeleteOutlineIcon /></Button>
                                </Tooltip>
                            </Stack>
                            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                                <FormSelectField
                                    name={`media-role-${link.asset.id}`}
                                    label={t('curator.fields.mediaRole')}
                                    value={link.role}
                                    options={['PRIMARY', 'DETAIL', 'SOURCE_SCAN', 'THUMBNAIL', 'OTHER'].map(value => ({ value, label: t(`curator.media.roles.${value}`) }))}
                                    onChange={event => update(index, { role: event.target.value as MediaRole })}
                                />
                                <Button variant={link.role === 'PRIMARY' ? 'contained' : 'outlined'} startIcon={<StarOutlinedIcon />} onClick={() => makePrimary(index)}>
                                    {t('curator.media.primary')}
                                </Button>
                            </Stack>
                            <TextField label={t('curator.fields.captionBg')} value={link.captionBg} onChange={event => update(index, { captionBg: event.target.value })} />
                            <TextField label={t('curator.fields.captionEn')} value={link.captionEn} onChange={event => update(index, { captionEn: event.target.value })} />
                        </Stack>
                    </Stack>
                </Paper>
            ))}
        </Stack>
    )
}

export function SourceSection({
    references,
    sourceReferenceLabel,
    value,
    setValue,
    selectedSource,
    canCreateCitation,
    onCreateSource,
    onCreateCitation,
    t,
}: {
    references: SourceReferenceDetails[]
    sourceReferenceLabel: (reference: SourceReferenceDetails) => string
    value: number
    setValue: (value: number) => void
    selectedSource: SourceDetails | null | undefined
    canCreateCitation: boolean
    onCreateSource: () => void
    onCreateCitation: () => void
    t: TFunction
}) {
    return (
        <Stack spacing={2}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ alignItems: { md: 'flex-start' } }}>
                <Autocomplete
                    sx={{ flex: 1 }}
                    options={references}
                    getOptionLabel={sourceReferenceLabel}
                    value={references.find(reference => reference.id === value) ?? null}
                    onChange={(_, next) => setValue(next?.id ?? 0)}
                    noOptionsText={t('curator.source.noCitations')}
                    renderInput={params => <TextField {...params} required label={t('curator.fields.citation')} />}
                />
                <Button variant="outlined" startIcon={<AddOutlinedIcon />} onClick={onCreateCitation} disabled={!canCreateCitation} sx={{ minHeight: 56 }}>
                    {t('curator.source.createCitation')}
                </Button>
                <Button variant="text" startIcon={<AddOutlinedIcon />} onClick={onCreateSource} sx={{ minHeight: 56 }}>
                    {t('curator.source.createSource')}
                </Button>
            </Stack>
            {selectedSource && (
                <Paper variant="outlined" sx={{ p: 2 }}>
                    <Typography sx={{ fontWeight: 700 }}>{selectedSource.title}</Typography>
                    <Typography color="text.secondary">{[selectedSource.author, selectedSource.year].filter(Boolean).join(' · ')}</Typography>
                </Paper>
            )}
            {!canCreateCitation && <Alert severity="info">{t('curator.source.createSourceFirst')}</Alert>}
        </Stack>
    )
}

export function DescriptionSection({ item, setField, t }: SectionProps) {
    return (
        <Stack spacing={2}>
            <TextField label={t('curator.fields.descriptionBg')} value={item.descriptionBg ?? ''} onChange={event => setField('descriptionBg', event.target.value)} multiline minRows={8} />
            <TextField label={t('curator.fields.descriptionEn')} value={item.descriptionEn ?? ''} onChange={event => setField('descriptionEn', event.target.value)} multiline minRows={8} />
        </Stack>
    )
}

export function MediaLibraryDialog({
    open,
    assets,
    used,
    onClose,
    onSelect,
}: {
    open: boolean
    assets: MediaAssetDetails[]
    used: Set<number>
    onClose: () => void
    onSelect: (asset: MediaAssetDetails) => void
}) {
    const { t } = useTranslation()
    return (
        <AdminModal open={open} onClose={onClose} maxWidth="md" title={t('curator.mediaLibrary.title')}>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' }, gap: 1.5 }}>
                {assets.map(asset => (
                    <Paper key={asset.id} variant="outlined" sx={{ overflow: 'hidden' }}>
                        <Avatar variant="square" src={asset.mediaType === 'IMAGE' ? `/api/media/${asset.id}/content` : undefined} sx={{ width: '100%', height: 120 }} />
                        <Box sx={{ p: 1 }}>
                            <Typography variant="body2" noWrap>{asset.fileName ?? `#${asset.id}`}</Typography>
                            <Button fullWidth size="small" disabled={used.has(asset.id)} onClick={() => onSelect(asset)}>
                                {used.has(asset.id) ? t('curator.media.added') : t('curator.media.select')}
                            </Button>
                        </Box>
                    </Paper>
                ))}
            </Box>
        </AdminModal>
    )
}

function nullText(value: string) {
    return value.trim() || null
}

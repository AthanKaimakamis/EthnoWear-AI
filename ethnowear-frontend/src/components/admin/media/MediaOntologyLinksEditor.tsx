import { useMemo, useState } from 'react'
import {
    Alert, Box, Button, Checkbox, Chip, FormControl, InputAdornment, InputLabel,
    MenuItem, Select, Skeleton, Stack, Table, TableBody, TableCell, TableContainer,
    TableHead, TableRow, TextField, Typography,
} from '@mui/material'
import LinkOutlinedIcon from '@mui/icons-material/LinkOutlined'
import SearchOutlinedIcon from '@mui/icons-material/SearchOutlined'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { mediaEntityLinksApi } from '../../../api/ArchiveAdminApi'
import { apiErrorMessage } from '../../../api/http'
import { getFullReference } from '../../../api/ReferenceApi'
import { apiEnumLabel } from '../../../app/apiEnumLabels'
import type { MediaEntityLinkDetails } from '../../../types/archive'
import type { OntologyFeatureType } from '../../../types/catalogue'
import type { Language, ReferenceData, ReferenceResource } from '../../../types/reference'
import AdminModal from '../AdminModal'

const entityTypes: OntologyFeatureType[] = [
    'REGIONAL_EMBROIDERY', 'REGIONAL_MOTIF', 'REGION', 'MOTIF', 'ORNAMENT', 'TECHNIQUE', 'COLOR',
]

type OntologyOption = ReferenceResource & { entityType: OntologyFeatureType }

type Props = {
    mediaAssetId: number
    canEdit: boolean
}

export default function MediaOntologyLinksEditor({ mediaAssetId, canEdit }: Props) {
    const { t, i18n } = useTranslation()
    const queryClient = useQueryClient()
    const [open, setOpen] = useState(false)
    const [query, setQuery] = useState('')
    const [entityType, setEntityType] = useState<OntologyFeatureType | ''>('')
    const [draft, setDraft] = useState<Set<string>>(new Set())
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const language: Language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
    const linksKey = ['admin', 'media-entity-links', mediaAssetId] as const

    const linksQuery = useQuery({
        queryKey: linksKey,
        queryFn: async ({ signal }) => {
            const result = await mediaEntityLinksApi.findAll({ size: 1000, sort: 'id,asc' }, signal)
            return result.content.filter(link => link.mediaAssetId === mediaAssetId)
        },
    })
    const referenceQuery = useQuery({
        queryKey: ['reference', 'full', language],
        queryFn: () => getFullReference(language),
        staleTime: 300_000,
    })

    const options = useMemo(() => referenceOptions(referenceQuery.data), [referenceQuery.data])
    const optionByKey = useMemo(() => new Map(options.map(option => [linkKey(option), option])), [options])
    const filtered = useMemo(() => {
        const normalized = query.trim().toLocaleLowerCase(language)
        return options.filter(option => (!entityType || option.entityType === entityType)
            && (!normalized || `${option.label} ${option.localName}`.toLocaleLowerCase(language).includes(normalized)))
    }, [entityType, language, options, query])
    const links = linksQuery.data ?? []

    function showEditor() {
        setDraft(new Set(links.map(linkKey)))
        setQuery('')
        setEntityType('')
        setError(null)
        setOpen(true)
    }

    function toggle(option: OntologyOption) {
        const key = linkKey(option)
        setDraft(current => {
            const next = new Set(current)
            if (next.has(key)) next.delete(key)
            else next.add(key)
            return next
        })
    }

    async function save() {
        if (saving) return
        setSaving(true)
        setError(null)
        try {
            const existingByKey = new Map(links.map(link => [linkKey(link), link]))
            const removed = links.filter(link => !draft.has(linkKey(link)))
            const added = [...draft]
                .filter(key => !existingByKey.has(key))
                .map(key => optionByKey.get(key))
                .filter((option): option is OntologyOption => Boolean(option))
            await Promise.all([
                ...removed.map(link => mediaEntityLinksApi.remove(link.id)),
                ...added.map(option => mediaEntityLinksApi.create({
                    mediaAssetId,
                    entityType: option.entityType,
                    ontologyIri: option.iri,
                    ontologyLocalName: option.localName,
                    description: null,
                })),
            ])
            await Promise.all([
                queryClient.invalidateQueries({ queryKey: linksKey }),
                queryClient.invalidateQueries({ queryKey: ['admin', 'media'] }),
            ])
            setOpen(false)
        } catch (caught) {
            setError(apiErrorMessage(caught, t('mediaOntology.saveFailed')))
        } finally {
            setSaving(false)
        }
    }

    if (linksQuery.isPending || referenceQuery.isPending) return <Skeleton variant="rounded" height={74} />
    if (linksQuery.isError || referenceQuery.isError) {
        return <Alert severity="error" action={<Button onClick={() => { void linksQuery.refetch(); void referenceQuery.refetch() }}>{t('common.retry')}</Button>}>{t('mediaOntology.loadFailed')}</Alert>
    }

    return <>
        <Box sx={{ border: 1, borderColor: 'divider', p: 1.5 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ gap: 1.5, alignItems: { sm: 'center' } }}>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{t('mediaOntology.title')}</Typography>
                    <Typography variant="body2" color="text.secondary">{t('mediaOntology.description')}</Typography>
                    <Stack direction="row" sx={{ gap: .75, flexWrap: 'wrap', mt: links.length ? 1 : 0 }}>
                        {links.map(link => <Chip key={link.id} size="small" variant="outlined" label={optionByKey.get(linkKey(link))?.label ?? link.ontologyLocalName} />)}
                        {links.length === 0 && <Typography variant="body2" color="text.secondary">{t('mediaOntology.empty')}</Typography>}
                    </Stack>
                </Box>
                {canEdit && <Button variant="outlined" startIcon={<LinkOutlinedIcon />} onClick={showEditor}>{t('mediaOntology.manage')}</Button>}
            </Stack>
        </Box>

        <AdminModal
            open={open}
            onClose={() => !saving && setOpen(false)}
            maxWidth="lg"
            title={t('mediaOntology.dialogTitle')}
            actions={<>
                <Button disabled={saving} onClick={() => setOpen(false)}>{t('admin.cancel')}</Button>
                <Button variant="contained" disabled={saving} onClick={() => void save()}>{t('forms.save')}</Button>
            </>}
        >
            <Stack spacing={2}>
                {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                    <TextField size="small" value={query} onChange={event => setQuery(event.target.value)} label={t('mediaOntology.search')} sx={{ flex: 1 }} slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchOutlinedIcon fontSize="small" /></InputAdornment> } }} />
                    <FormControl size="small" sx={{ minWidth: 240 }}>
                        <InputLabel id={`media-ontology-type-${mediaAssetId}`}>{t('mediaOntology.type')}</InputLabel>
                        <Select labelId={`media-ontology-type-${mediaAssetId}`} value={entityType} label={t('mediaOntology.type')} onChange={event => setEntityType(event.target.value as OntologyFeatureType | '')}>
                            <MenuItem value="">{t('admin.allCategories')}</MenuItem>
                            {entityTypes.map(type => <MenuItem key={type} value={type}>{apiEnumLabel(t, 'featureType', type)}</MenuItem>)}
                        </Select>
                    </FormControl>
                </Stack>
                <Typography variant="body2" color="text.secondary">{t('mediaOntology.selectedCount', { count: draft.size })}</Typography>
                <TableContainer sx={{ maxHeight: 460, border: 1, borderColor: 'divider' }}>
                    <Table stickyHeader size="small">
                        <TableHead><TableRow><TableCell padding="checkbox" /><TableCell>{t('mediaOntology.concept')}</TableCell><TableCell>{t('mediaOntology.type')}</TableCell></TableRow></TableHead>
                        <TableBody>
                            {filtered.length === 0
                                ? <TableRow><TableCell colSpan={3} align="center" sx={{ py: 6, color: 'text.secondary' }}>{t('admin.noResults')}</TableCell></TableRow>
                                : filtered.map(option => {
                                    const selected = draft.has(linkKey(option))
                                    return <TableRow key={linkKey(option)} hover selected={selected} onClick={() => toggle(option)} sx={{ cursor: 'pointer' }}>
                                        <TableCell padding="checkbox"><Checkbox checked={selected} onClick={event => event.stopPropagation()} onChange={() => toggle(option)} slotProps={{ input: { 'aria-label': option.label } }} /></TableCell>
                                        <TableCell><Typography variant="body2" sx={{ fontWeight: 700 }}>{option.label}</Typography><Typography variant="caption" color="text.secondary">{option.localName}</Typography></TableCell>
                                        <TableCell>{apiEnumLabel(t, 'featureType', option.entityType)}</TableCell>
                                    </TableRow>
                                })}
                        </TableBody>
                    </Table>
                </TableContainer>
            </Stack>
        </AdminModal>
    </>
}

function referenceOptions(reference?: ReferenceData): OntologyOption[] {
    if (!reference) return []
    const groups: Array<[OntologyFeatureType, ReferenceResource[]]> = [
        ['REGIONAL_EMBROIDERY', reference.regionalEmbroideryTypes],
        ['REGIONAL_MOTIF', reference.regionalMotifTypes],
        ['REGION', reference.regions],
        ['MOTIF', reference.motifs],
        ['ORNAMENT', reference.ornaments],
        ['TECHNIQUE', reference.techniques],
        ['COLOR', reference.colors],
    ]
    return groups.flatMap(([entityType, resources]) => resources.map(resource => ({ ...resource, entityType })))
}

function linkKey(link: Pick<MediaEntityLinkDetails, 'entityType' | 'ontologyIri'> | OntologyOption) {
    return `${link.entityType}:${'ontologyIri' in link ? link.ontologyIri : link.iri}`
}

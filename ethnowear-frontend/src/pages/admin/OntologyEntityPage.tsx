import { useCallback, useEffect, useMemo, useState } from 'react'
import {
    Alert, Box, Button, Chip, FormControl, InputLabel, MenuItem, Paper, Select, Snackbar, Stack, Typography,
} from '@mui/material'
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlineOutlined'
import HubOutlinedIcon from '@mui/icons-material/HubOutlined'
import TranslateOutlinedIcon from '@mui/icons-material/TranslateOutlined'
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined'
import { useParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { getFullReference } from '../../api/ReferenceApi.ts'
import { createOntologyEntity, deleteOntologyEntity, listOntologyEntities, synchronizeDerivedRegionTypes, updateOntologyEntity } from '../../api/OntologyAdminApi.ts'
import { apiErrorMessage } from '../../api/http.ts'
import OntologyEntityDialog, { type EntityOptions } from '../../components/admin/OntologyEntityDialog.tsx'
import ConfirmDialog from '../../components/admin/ConfirmDialog.tsx'
import AdminDataTable, { type AdminTableColumn } from '../../components/admin/table/AdminDataTable.tsx'
import AdminPageHeader from '../../components/admin/AdminPageHeader.tsx'
import type { OptionCategory, SelectOption } from '../../components/forms/formTypes.ts'
import type { OntologyEntity, OntologyEntityInput, OntologyEntityType } from '../../types/ontologyAdmin.ts'
import { invalidatePublicQueries } from '../../app/queryClient'
import { useAdminAuth } from '../../app/adminAuth'
import {
    filterOntologyEntities, ontologyProfileScore, ontologyRelations,
    type OntologyProfileFilter, type OntologyRelationshipFilter,
} from './ontologyTableModel'

const validTypes = new Set<OntologyEntityType>(['ornaments', 'techniques', 'motifs', 'regions', 'regional-embroideries', 'regional-motifs'])
const emptyOptions: EntityOptions = {
    regions: [], regionGroups: [], ornaments: [], techniques: [], motifs: [],
    ornamentCategories: [], techniqueCategories: [],
}

function option(item: { localName: string, label: string }): SelectOption {
    return { value: item.localName, label: `${item.label} (${item.localName})` }
}

function categoryOption(
    item: { localName: string, label: string },
    members: Record<string, string[]>,
): OptionCategory {
    return { ...option(item), optionValues: members[item.localName] ?? [] }
}

function OntologyEntityPage() {
    const { entityType } = useParams()
    const type = validTypes.has(entityType as OntologyEntityType) ? entityType as OntologyEntityType : 'ornaments'
    const { t, i18n } = useTranslation()
    const { admin } = useAdminAuth()
    const [items, setItems] = useState<OntologyEntity[]>([])
    const [options, setOptions] = useState<EntityOptions>(emptyOptions)
    const [loading, setLoading] = useState(true)
    const [categoryFilter, setCategoryFilter] = useState('')
    const [profileFilter, setProfileFilter] = useState<OntologyProfileFilter>('all')
    const [relationshipFilter, setRelationshipFilter] = useState<OntologyRelationshipFilter>('all')
    const [relationLabels, setRelationLabels] = useState<Record<string, string>>({})
    const [editing, setEditing] = useState<OntologyEntity | null | undefined>(undefined)
    const [deleting, setDeleting] = useState<OntologyEntity | null>(null)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [notice, setNotice] = useState<string | null>(null)

    const load = useCallback(async (signal?: AbortSignal) => {
        await Promise.resolve()
        setLoading(true)
        setError(null)
        try {
            const language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
            const [entities, reference] = await Promise.all([listOntologyEntities(type, signal), getFullReference(language)])
            setItems(entities)
            setCategoryFilter('')
            setProfileFilter('all')
            setRelationshipFilter('all')
            const resources = [
                ...reference.regions, ...reference.regionGroups, ...reference.ornaments,
                ...reference.techniques, ...reference.motifs, ...reference.regionalMotifTypes, ...reference.regionalEmbroideryTypes,
                ...reference.ornamentTypes, ...reference.techniqueTypes,
            ]
            setRelationLabels(Object.fromEntries(resources.map(item => [item.localName, item.label])))
            setOptions({
                regions: reference.regions.map(option),
                regionGroups: reference.regionGroups.map(option),
                ornaments: reference.ornaments.map(option),
                techniques: reference.techniques.map(option),
                motifs: reference.motifs.map(option),
                ornamentCategories: reference.ornamentTypes.map(item => categoryOption(item, reference.ornamentsByType)),
                techniqueCategories: reference.techniqueTypes.map(item => categoryOption(item, reference.techniquesByType)),
            })
        } catch (caught) {
            if (!(caught instanceof DOMException && caught.name === 'AbortError')) setError(apiErrorMessage(caught))
        } finally { setLoading(false) }
    }, [i18n.resolvedLanguage, type])

    useEffect(() => {
        const controller = new AbortController()
        const timeout = window.setTimeout(() => void load(controller.signal), 0)
        return () => {
            window.clearTimeout(timeout)
            controller.abort()
        }
    }, [load])

    const categoryOptions = type === 'ornaments' ? options.ornamentCategories
        : type === 'techniques' ? options.techniqueCategories
            : []
    const showRelationshipsColumn = true
    const visibleItems = useMemo(() => filterOntologyEntities(
        items, categoryFilter, profileFilter, relationshipFilter,
    ), [categoryFilter, items, profileFilter, relationshipFilter])
    const localizedName = useCallback((item: OntologyEntity) => i18n.resolvedLanguage === 'en'
        ? item.labelEn ?? item.labelBg ?? item.localName
        : item.labelBg ?? item.labelEn ?? item.localName, [i18n.resolvedLanguage])
    const columns = useMemo<AdminTableColumn<OntologyEntity>[]>(() => {
        const result: AdminTableColumn<OntologyEntity>[] = [
            {
                key: 'name', label: t('admin.columns.name'), sortValue: localizedName,
                cellSx: { minWidth: 250 },
                render: item => <Stack spacing={.25}>
                    <Typography sx={{ fontWeight: 700 }}>{localizedName(item)}</Typography>
                    <Typography variant="caption" color="text.secondary">
                        {[i18n.resolvedLanguage === 'en' ? item.labelBg : item.labelEn,
                            item.altLabelsBg.length + item.altLabelsEn.length > 0
                                ? t('admin.table.altNames', { count: item.altLabelsBg.length + item.altLabelsEn.length }) : null]
                            .filter(Boolean).join(' · ') || t('admin.table.noSecondLabel')}
                    </Typography>
                    <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>{item.localName}</Typography>
                </Stack>,
            },
            {
                key: 'description', label: t('admin.columns.description'),
                sortValue: item => (i18n.resolvedLanguage === 'en' ? item.commentEn : item.commentBg) ?? '',
                cellSx: { minWidth: 280, maxWidth: 420 },
                render: item => {
                    const description = i18n.resolvedLanguage === 'en' ? item.commentEn ?? item.commentBg : item.commentBg ?? item.commentEn
                    return description
                        ? <Typography variant="body2" color="text.secondary" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{description}</Typography>
                        : <Chip size="small" color="warning" variant="outlined" label={t('admin.table.noDescription')} />
                },
            },
        ]
        if (showRelationshipsColumn) result.push({
            key: 'relationships', label: t('admin.columns.relationships'), sortValue: item => ontologyRelations(item).length,
            cellSx: { minWidth: 260 },
            render: item => <Stack direction="row" sx={{ gap: .5, flexWrap: 'wrap' }}>
                {ontologyRelations(item).length === 0 && <Typography variant="body2" color="text.secondary">{t('admin.table.noRelationships')}</Typography>}
                {ontologyRelations(item).slice(0, 3).map(value => <Chip key={value} label={relationLabels[value] ?? value} size="small" variant="outlined" />)}
                {ontologyRelations(item).length > 3 && <Chip label={`+${ontologyRelations(item).length - 3}`} size="small" />}
            </Stack>,
        })
        result.push({
            key: 'profile', label: t('admin.columns.profile'), align: 'center', sortValue: ontologyProfileScore,
            render: item => {
                const score = ontologyProfileScore(item)
                return <Stack spacing={.5} sx={{ alignItems: 'center' }}>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>{score}/4</Typography>
                    <Chip size="small" color={score === 4 ? 'success' : 'warning'} variant="outlined"
                        label={score === 4 ? t('admin.table.complete') : t('admin.table.incomplete')} />
                </Stack>
            },
        })
        return result
    }, [i18n.resolvedLanguage, localizedName, relationLabels, showRelationshipsColumn, t])

    const completeCount = useMemo(() => items.filter(item => ontologyProfileScore(item) === 4).length, [items])
    const linkedCount = useMemo(() => items.filter(item => ontologyRelations(item).length > 0).length, [items])
    const missingTranslationCount = useMemo(() => items.filter(item => !item.labelBg?.trim() || !item.labelEn?.trim()).length, [items])
    const filtersActive = Boolean(categoryFilter || profileFilter !== 'all' || relationshipFilter !== 'all')

    async function save(input: OntologyEntityInput, changeReason?: string) {
        setSaving(true); setError(null)
        try {
            if (editing) await updateOntologyEntity(type, editing.localName, input, changeReason)
            else await createOntologyEntity(type, input, changeReason)
            void invalidatePublicQueries()
            setEditing(undefined); setNotice(t('admin.saved')); await load()
        } catch (caught) { setError(apiErrorMessage(caught)) } finally { setSaving(false) }
    }

    async function remove() {
        if (!deleting) return
        setSaving(true); setError(null)
        try {
            await deleteOntologyEntity(type, deleting.localName)
            void invalidatePublicQueries()
            setDeleting(null); setNotice(t('admin.deleted')); await load()
        } catch (caught) { setError(apiErrorMessage(caught)); setDeleting(null) } finally { setSaving(false) }
    }

    async function synchronizeDerived() {
        setSaving(true); setError(null)
        try {
            const result = await synchronizeDerivedRegionTypes()
            await invalidatePublicQueries()
            setNotice(t('admin.synchronizeDerivedResult', { count: result.created }))
            await load()
        } catch (caught) { setError(apiErrorMessage(caught)) } finally { setSaving(false) }
    }

    return (
        <Stack spacing={3}>
            <AdminPageHeader
                title={t(`admin.entities.${type === 'regional-embroideries' ? 'regionalEmbroideries' : type === 'regional-motifs' ? 'regionalMotifs' : type}`)}
                description={t('admin.subtitle')}
                actions={type === 'regions' && admin?.roles.includes('ADMINISTRATOR')
                    ? <Button variant="outlined" disabled={saving} onClick={() => void synchronizeDerived()}>{t('admin.synchronizeDerived')}</Button>
                    : undefined}
            />
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', lg: 'repeat(4, minmax(0, 1fr))' }, gap: 1.5 }}>
                <SummaryMetric label={t('admin.metrics.total')} value={items.length} icon={<HubOutlinedIcon />} />
                <SummaryMetric label={t('admin.metrics.complete')} value={completeCount} icon={<CheckCircleOutlineIcon />} tone="success" />
                <SummaryMetric label={t('admin.metrics.linked')} value={linkedCount} icon={<HubOutlinedIcon />} />
                <SummaryMetric label={t('admin.metrics.missingTranslation')} value={missingTranslationCount} icon={<TranslateOutlinedIcon />} tone={missingTranslationCount ? 'warning' : 'default'} />
            </Box>
            {error && editing === undefined && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
            <AdminDataTable
                rows={visibleItems}
                columns={columns}
                loading={loading}
                getRowId={item => item.localName}
                searchableText={item => [item.localName, item.labelBg, item.labelEn, item.commentBg, item.commentEn,
                    ...item.altLabelsBg, ...item.altLabelsEn, ...ontologyRelations(item),
                    ...ontologyRelations(item).map(value => relationLabels[value])].filter(Boolean).join(' ')}
                searchPlaceholder={t('admin.search')}
                defaultSortKey="name"
                rowsPerPageOptions={[20, 50, 100]}
                toolbarFilters={<Stack direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ flexWrap: 'wrap' }}>
                    {categoryOptions.length > 0 && <FormControl size="small" sx={{ minWidth: 210 }}>
                        <InputLabel id="admin-category-filter-label">{t('admin.categoryFilter')}</InputLabel>
                        <Select labelId="admin-category-filter-label" value={categoryFilter}
                            label={t('admin.categoryFilter')}
                            onChange={event => setCategoryFilter(event.target.value)}>
                            <MenuItem value="">{t('admin.allCategories')}</MenuItem>
                            {categoryOptions.map(category => <MenuItem key={category.value} value={category.value}>{category.label}</MenuItem>)}
                        </Select>
                    </FormControl>}
                    <FormControl size="small" sx={{ minWidth: 190 }}>
                        <InputLabel id="admin-profile-filter-label">{t('admin.profileFilter')}</InputLabel>
                        <Select labelId="admin-profile-filter-label" value={profileFilter} label={t('admin.profileFilter')}
                            onChange={event => setProfileFilter(event.target.value as OntologyProfileFilter)}>
                            <MenuItem value="all">{t('admin.filters.allProfiles')}</MenuItem>
                            <MenuItem value="complete">{t('admin.filters.complete')}</MenuItem>
                            <MenuItem value="missing-labels">{t('admin.filters.missingLabels')}</MenuItem>
                            <MenuItem value="missing-descriptions">{t('admin.filters.missingDescriptions')}</MenuItem>
                        </Select>
                    </FormControl>
                    <FormControl size="small" sx={{ minWidth: 190 }}>
                        <InputLabel id="admin-relationship-filter-label">{t('admin.relationshipFilter')}</InputLabel>
                        <Select labelId="admin-relationship-filter-label" value={relationshipFilter} label={t('admin.relationshipFilter')}
                            onChange={event => setRelationshipFilter(event.target.value as OntologyRelationshipFilter)}>
                            <MenuItem value="all">{t('admin.filters.allRelationships')}</MenuItem>
                            <MenuItem value="linked">{t('admin.filters.linked')}</MenuItem>
                            <MenuItem value="unlinked">{t('admin.filters.unlinked')}</MenuItem>
                        </Select>
                    </FormControl>
                    {filtersActive && <Button onClick={() => { setCategoryFilter(''); setProfileFilter('all'); setRelationshipFilter('all') }}>
                        {t('admin.filters.clear')}
                    </Button>}
                </Stack>}
                onAdd={() => { setError(null); setEditing(null) }}
                onEdit={item => { setError(null); setEditing(item) }}
                onDelete={setDeleting}
            />
            <OntologyEntityDialog key={`${type}:${editing?.localName ?? 'new'}:${editing === undefined ? 'closed' : 'open'}`}
                open={editing !== undefined} type={type} entity={editing ?? null} options={options}
                saving={saving} error={editing !== undefined ? error : null} onClose={() => setEditing(undefined)} onSubmit={save} />
            <ConfirmDialog open={Boolean(deleting)} title={t('admin.confirmDelete')} pending={saving}
                onCancel={() => setDeleting(null)} onConfirm={remove}>
                {t('admin.confirmDeleteText', { name: deleting?.localName })}
            </ConfirmDialog>
            <Snackbar open={Boolean(notice)} autoHideDuration={3000} onClose={() => setNotice(null)} message={notice} />
        </Stack>
    )
}

function SummaryMetric({ label, value, icon, tone = 'default' }: {
    label: string, value: number, icon: React.ReactNode, tone?: 'default' | 'success' | 'warning'
}) {
    const color = tone === 'success' ? 'success.main' : tone === 'warning' ? 'warning.main' : 'text.secondary'
    return <Paper variant="outlined" sx={{ p: 1.5, display: 'flex', alignItems: 'center', gap: 1.25, minHeight: 72 }}>
        <Box sx={{ color, display: 'flex' }}>{tone === 'warning' ? <WarningAmberOutlinedIcon /> : icon}</Box>
        <Box>
            <Typography variant="h6" sx={{ lineHeight: 1.1 }}>{value}</Typography>
            <Typography variant="caption" color="text.secondary">{label}</Typography>
        </Box>
    </Paper>
}

export default OntologyEntityPage

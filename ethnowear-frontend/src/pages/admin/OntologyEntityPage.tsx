import { useCallback, useEffect, useMemo, useState } from 'react'
import {
    Alert, Chip, FormControl, InputLabel, MenuItem, Select, Snackbar, Stack, Typography,
} from '@mui/material'
import { useParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { getFullReference } from '../../api/ReferenceApi.ts'
import { createOntologyEntity, deleteOntologyEntity, listOntologyEntities, updateOntologyEntity } from '../../api/OntologyAdminApi.ts'
import { apiErrorMessage } from '../../api/http.ts'
import OntologyEntityDialog, { type EntityOptions } from '../../components/admin/OntologyEntityDialog.tsx'
import ConfirmDialog from '../../components/admin/ConfirmDialog.tsx'
import AdminDataTable, { type AdminTableColumn } from '../../components/admin/table/AdminDataTable.tsx'
import AdminPageHeader from '../../components/admin/AdminPageHeader.tsx'
import type { OptionCategory, SelectOption } from '../../components/forms/formTypes.ts'
import type { OntologyEntity, OntologyEntityInput, OntologyEntityType } from '../../types/ontologyAdmin.ts'
import { invalidatePublicQueries } from '../../app/queryClient'

const validTypes = new Set<OntologyEntityType>(['ornaments', 'techniques', 'motifs', 'regions', 'regional-embroideries'])
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

function relations(entity: OntologyEntity) {
    return [entity.regionGroupLocalName, entity.regionLocalName,
        ...(entity.characteristicRegionLocalNames ?? []), ...(entity.ornamentLocalNames ?? []),
        ...(entity.techniqueLocalNames ?? []), ...(entity.motifLocalNames ?? [])].filter(Boolean) as string[]
}

function OntologyEntityPage() {
    const { entityType } = useParams()
    const type = validTypes.has(entityType as OntologyEntityType) ? entityType as OntologyEntityType : 'ornaments'
    const { t, i18n } = useTranslation()
    const [items, setItems] = useState<OntologyEntity[]>([])
    const [options, setOptions] = useState<EntityOptions>(emptyOptions)
    const [loading, setLoading] = useState(true)
    const [categoryFilter, setCategoryFilter] = useState('')
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
    const showRelationshipsColumn = type !== 'regions'
    const visibleItems = useMemo(() => categoryFilter
        ? items.filter(item => item.typeLocalNames?.includes(categoryFilter))
        : items, [categoryFilter, items])
    const localizedName = useCallback((item: OntologyEntity) => i18n.resolvedLanguage === 'en'
        ? item.labelEn ?? item.labelBg ?? item.localName
        : item.labelBg ?? item.labelEn ?? item.localName, [i18n.resolvedLanguage])
    const columns = useMemo<AdminTableColumn<OntologyEntity>[]>(() => {
        const result: AdminTableColumn<OntologyEntity>[] = [
            {
                key: 'name', label: t('admin.columns.name'), sortValue: localizedName,
                render: item => <Typography sx={{ fontWeight: 700 }}>{localizedName(item)}</Typography>,
            },
            {
                key: 'localName', label: 'Local name', sortValue: item => item.localName,
                render: item => <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{item.localName}</Typography>,
            },
        ]
        if (showRelationshipsColumn) result.push({
            key: 'relationships', label: t('admin.columns.relationships'), sortValue: item => relations(item).join(' '),
            render: item => <Stack direction="row" sx={{ gap: .5, flexWrap: 'wrap' }}>
                {relations(item).slice(0, 3).map(value => <Chip key={value} label={value} size="small" variant="outlined" />)}
                {relations(item).length > 3 && <Chip label={`+${relations(item).length - 3}`} size="small" />}
            </Stack>,
        })
        return result
    }, [localizedName, showRelationshipsColumn, t])

    async function save(input: OntologyEntityInput) {
        setSaving(true); setError(null)
        try {
            if (editing) await updateOntologyEntity(type, editing.localName, input)
            else await createOntologyEntity(type, input)
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

    return (
        <Stack spacing={3}>
            <AdminPageHeader
                title={t(`admin.entities.${type === 'regional-embroideries' ? 'regionalEmbroideries' : type}`)}
                description={t('admin.subtitle')}
            />
            {error && editing === undefined && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
            <AdminDataTable
                rows={visibleItems}
                columns={columns}
                loading={loading}
                getRowId={item => item.localName}
                searchableText={item => [item.localName, item.labelBg, item.labelEn, ...relations(item)].filter(Boolean).join(' ')}
                searchPlaceholder={t('admin.search')}
                defaultSortKey="name"
                rowsPerPageOptions={[20, 50, 100]}
                toolbarFilters={categoryOptions.length > 0 ? <FormControl size="small" sx={{ minWidth: 240 }}>
                        <InputLabel id="admin-category-filter-label">{t('admin.categoryFilter')}</InputLabel>
                        <Select labelId="admin-category-filter-label" value={categoryFilter}
                            label={t('admin.categoryFilter')}
                            onChange={event => setCategoryFilter(event.target.value)}>
                            <MenuItem value="">{t('admin.allCategories')}</MenuItem>
                            {categoryOptions.map(category => <MenuItem key={category.value} value={category.value}>{category.label}</MenuItem>)}
                        </Select>
                    </FormControl> : undefined}
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

export default OntologyEntityPage

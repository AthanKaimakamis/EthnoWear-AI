import { useCallback, useEffect, useMemo, useState } from 'react'
import {
    Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
    DialogContentText, DialogTitle, FormControl, IconButton, InputAdornment, InputLabel,
    MenuItem, Paper, Select, Snackbar, Stack,
    Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow,
    TableSortLabel, TextField, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import SearchIcon from '@mui/icons-material/Search'
import { useParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { getFullReference } from '../../api/ReferenceApi.ts'
import { createOntologyEntity, deleteOntologyEntity, listOntologyEntities, updateOntologyEntity } from '../../api/OntologyAdminApi.ts'
import { ApiError } from '../../api/http.ts'
import OntologyEntityDialog, { type EntityOptions } from '../../components/admin/OntologyEntityDialog.tsx'
import type { OptionCategory, SelectOption } from '../../components/forms/formTypes.ts'
import type { OntologyEntity, OntologyEntityInput, OntologyEntityType } from '../../types/ontologyAdmin.ts'

const validTypes = new Set<OntologyEntityType>(['ornaments', 'techniques', 'motifs', 'regions', 'regional-embroideries'])
const emptyOptions: EntityOptions = {
    regions: [], regionGroups: [], ornaments: [], techniques: [], motifs: [],
    ornamentCategories: [], techniqueCategories: [],
}

type SortKey = 'name' | 'localName' | 'relationships'
type SortOrder = 'asc' | 'desc'

function message(error: unknown) {
    if (error instanceof ApiError && error.details && typeof error.details === 'object' && 'message' in error.details) {
        return String(error.details.message)
    }
    return error instanceof Error ? error.message : 'Unexpected error'
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
    const [query, setQuery] = useState('')
    const [categoryFilter, setCategoryFilter] = useState('')
    const [sortKey, setSortKey] = useState<SortKey>('name')
    const [sortOrder, setSortOrder] = useState<SortOrder>('asc')
    const [page, setPage] = useState(0)
    const [rowsPerPage, setRowsPerPage] = useState(20)
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
            setPage(0)
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
            if (!(caught instanceof DOMException && caught.name === 'AbortError')) setError(message(caught))
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

    const filtered = useMemo(() => {
        const normalized = query.trim().toLocaleLowerCase()
        return items.filter(item => {
            const matchesQuery = !normalized || [item.localName, item.labelBg, item.labelEn, ...relations(item)]
                .some(value => value?.toLocaleLowerCase().includes(normalized))
            const matchesCategory = !categoryFilter || item.typeLocalNames?.includes(categoryFilter)
            return matchesQuery && matchesCategory
        })
    }, [categoryFilter, items, query])

    const sorted = useMemo(() => [...filtered].sort((left, right) => {
        const languageIsEnglish = i18n.resolvedLanguage === 'en'
        const leftName = languageIsEnglish ? left.labelEn ?? left.labelBg : left.labelBg ?? left.labelEn
        const rightName = languageIsEnglish ? right.labelEn ?? right.labelBg : right.labelBg ?? right.labelEn
        const leftValue = sortKey === 'name' ? leftName ?? ''
            : sortKey === 'localName' ? left.localName
                : relations(left).join(' ')
        const rightValue = sortKey === 'name' ? rightName ?? ''
            : sortKey === 'localName' ? right.localName
                : relations(right).join(' ')
        return leftValue.localeCompare(rightValue, i18n.resolvedLanguage) * (sortOrder === 'asc' ? 1 : -1)
    }), [filtered, i18n.resolvedLanguage, sortKey, sortOrder])

    const visible = sorted.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
    const categoryOptions = type === 'ornaments' ? options.ornamentCategories
        : type === 'techniques' ? options.techniqueCategories
            : []
    const showRelationshipsColumn = type !== 'regions'
    const columnCount = showRelationshipsColumn ? 4 : 3

    function changeSort(nextKey: SortKey) {
        if (sortKey === nextKey) {
            setSortOrder(current => current === 'asc' ? 'desc' : 'asc')
        } else {
            setSortKey(nextKey)
            setSortOrder('asc')
        }
        setPage(0)
    }

    async function save(input: OntologyEntityInput) {
        setSaving(true); setError(null)
        try {
            if (editing) await updateOntologyEntity(type, editing.localName, input)
            else await createOntologyEntity(type, input)
            setEditing(undefined); setNotice(t('admin.saved')); await load()
        } catch (caught) { setError(message(caught)) } finally { setSaving(false) }
    }

    async function remove() {
        if (!deleting) return
        setSaving(true); setError(null)
        try {
            await deleteOntologyEntity(type, deleting.localName)
            setDeleting(null); setNotice(t('admin.deleted')); await load()
        } catch (caught) { setError(message(caught)); setDeleting(null) } finally { setSaving(false) }
    }

    return (
        <Stack spacing={3}>
            <Box>
                <Typography variant="h4" component="h1" sx={{ m: 0, color: 'text.primary', fontWeight: 800, letterSpacing: 0 }}>
                    {t(`admin.entities.${type === 'regional-embroideries' ? 'regionalEmbroideries' : type}`)}
                </Typography>
                <Typography color="text.secondary">{t('admin.subtitle')}</Typography>
            </Box>
            {error && editing === undefined && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
            <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ p: 2 }}>
                    <TextField size="small" value={query} onChange={event => { setQuery(event.target.value); setPage(0) }}
                        placeholder={t('admin.search')} sx={{ flex: 1, maxWidth: 520 }}
                        slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
                    {categoryOptions.length > 0 && <FormControl size="small" sx={{ minWidth: 240 }}>
                        <InputLabel id="admin-category-filter-label">{t('admin.categoryFilter')}</InputLabel>
                        <Select labelId="admin-category-filter-label" value={categoryFilter}
                            label={t('admin.categoryFilter')}
                            onChange={event => { setCategoryFilter(event.target.value); setPage(0) }}>
                            <MenuItem value="">{t('admin.allCategories')}</MenuItem>
                            {categoryOptions.map(category => <MenuItem key={category.value} value={category.value}>{category.label}</MenuItem>)}
                        </Select>
                    </FormControl>}
                    <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setError(null); setEditing(null) }} sx={{ ml: { sm: 'auto' } }}>
                        {t('admin.add')}
                    </Button>
                </Stack>
                <TableContainer>
                    <Table size="small">
                        <TableHead><TableRow>
                            <TableCell sortDirection={sortKey === 'name' ? sortOrder : false}>
                                <TableSortLabel active={sortKey === 'name'} direction={sortKey === 'name' ? sortOrder : 'asc'} onClick={() => changeSort('name')}>
                                    {t('admin.columns.name')}
                                </TableSortLabel>
                            </TableCell>
                            <TableCell sortDirection={sortKey === 'localName' ? sortOrder : false}>
                                <TableSortLabel active={sortKey === 'localName'} direction={sortKey === 'localName' ? sortOrder : 'asc'} onClick={() => changeSort('localName')}>
                                    Local name
                                </TableSortLabel>
                            </TableCell>
                            {showRelationshipsColumn && <TableCell sortDirection={sortKey === 'relationships' ? sortOrder : false}>
                                <TableSortLabel active={sortKey === 'relationships'} direction={sortKey === 'relationships' ? sortOrder : 'asc'} onClick={() => changeSort('relationships')}>
                                    {t('admin.columns.relationships')}
                                </TableSortLabel>
                            </TableCell>}
                            <TableCell align="right">{t('admin.columns.actions')}</TableCell>
                        </TableRow></TableHead>
                        <TableBody>
                            {loading ? <TableRow><TableCell colSpan={columnCount} align="center" sx={{ py: 8 }}><CircularProgress size={28} /></TableCell></TableRow>
                                : visible.length === 0 ? <TableRow><TableCell colSpan={columnCount} align="center" sx={{ py: 8, color: 'text.secondary' }}>{t('admin.noResults')}</TableCell></TableRow>
                                : visible.map(item => <TableRow hover key={item.localName}>
                                    <TableCell><Typography sx={{ fontWeight: 700 }}>{i18n.resolvedLanguage === 'en' ? item.labelEn ?? item.labelBg : item.labelBg ?? item.labelEn}</Typography></TableCell>
                                    <TableCell><Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{item.localName}</Typography></TableCell>
                                    {showRelationshipsColumn && <TableCell><Stack direction="row" sx={{ gap: .5, flexWrap: 'wrap' }}>{relations(item).slice(0, 3).map(value => <Chip key={value} label={value} size="small" variant="outlined" />)}{relations(item).length > 3 && <Chip label={`+${relations(item).length - 3}`} size="small" />}</Stack></TableCell>}
                                    <TableCell align="right">
                                        <Tooltip title={t('admin.edit')}><IconButton size="small" onClick={() => { setError(null); setEditing(item) }}><EditOutlinedIcon fontSize="small" /></IconButton></Tooltip>
                                        <Tooltip title={t('admin.delete')}><IconButton size="small" color="error" onClick={() => setDeleting(item)}><DeleteOutlineIcon fontSize="small" /></IconButton></Tooltip>
                                    </TableCell>
                                </TableRow>)}
                        </TableBody>
                    </Table>
                </TableContainer>
                <TablePagination component="div" count={filtered.length} page={page} rowsPerPage={rowsPerPage}
                    onPageChange={(_, value) => setPage(value)} onRowsPerPageChange={event => { setRowsPerPage(Number(event.target.value)); setPage(0) }}
                    rowsPerPageOptions={[10, 20, 50]} />
            </Paper>
            <OntologyEntityDialog key={`${type}:${editing?.localName ?? 'new'}:${editing === undefined ? 'closed' : 'open'}`}
                open={editing !== undefined} type={type} entity={editing ?? null} options={options}
                saving={saving} error={editing !== undefined ? error : null} onClose={() => setEditing(undefined)} onSubmit={save} />
            <Dialog open={Boolean(deleting)} onClose={() => setDeleting(null)}>
                <DialogTitle>{t('admin.confirmDelete')}</DialogTitle><DialogContent><DialogContentText>{t('admin.confirmDeleteText', { name: deleting?.localName })}</DialogContentText></DialogContent>
                <DialogActions><Button onClick={() => setDeleting(null)}>{t('admin.cancel')}</Button><Button color="error" variant="contained" onClick={remove} disabled={saving}>{t('admin.delete')}</Button></DialogActions>
            </Dialog>
            <Snackbar open={Boolean(notice)} autoHideDuration={3000} onClose={() => setNotice(null)} message={notice} />
        </Stack>
    )
}

export default OntologyEntityPage

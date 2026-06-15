import { useEffect, useMemo, useState } from 'react'
import {
    Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
    DialogContentText, DialogTitle, IconButton, InputAdornment, Paper, Snackbar, Stack,
    Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow,
    TextField, Tooltip, Typography,
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
import type { SelectOption } from '../../components/forms/formTypes.ts'
import type { OntologyEntity, OntologyEntityInput, OntologyEntityType } from '../../types/ontologyAdmin.ts'

const validTypes = new Set<OntologyEntityType>(['ornaments', 'techniques', 'motifs', 'regions', 'regional-embroideries'])
const emptyOptions: EntityOptions = { regions: [], regionGroups: [], ornaments: [], techniques: [], motifs: [] }

function message(error: unknown) {
    if (error instanceof ApiError && error.details && typeof error.details === 'object' && 'message' in error.details) {
        return String(error.details.message)
    }
    return error instanceof Error ? error.message : 'Unexpected error'
}

function option(item: { localName: string, label: string }): SelectOption {
    return { value: item.localName, label: `${item.label} (${item.localName})` }
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
    const [page, setPage] = useState(0)
    const [rowsPerPage, setRowsPerPage] = useState(10)
    const [editing, setEditing] = useState<OntologyEntity | null | undefined>(undefined)
    const [deleting, setDeleting] = useState<OntologyEntity | null>(null)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [notice, setNotice] = useState<string | null>(null)

    async function load(signal?: AbortSignal) {
        setLoading(true)
        setError(null)
        try {
            const language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
            const [entities, reference] = await Promise.all([listOntologyEntities(type, signal), getFullReference(language)])
            setItems(entities)
            setOptions({ regions: reference.regions.map(option), regionGroups: reference.regionGroups.map(option),
                ornaments: reference.ornaments.map(option), techniques: reference.techniques.map(option), motifs: reference.motifs.map(option) })
        } catch (caught) {
            if (!(caught instanceof DOMException && caught.name === 'AbortError')) setError(message(caught))
        } finally { setLoading(false) }
    }

    useEffect(() => {
        const controller = new AbortController()
        void load(controller.signal)
        return () => controller.abort()
    }, [type, i18n.resolvedLanguage])

    const filtered = useMemo(() => {
        const normalized = query.trim().toLocaleLowerCase()
        if (!normalized) return items
        return items.filter(item => [item.localName, item.labelBg, item.labelEn, ...relations(item)]
            .some(value => value?.toLocaleLowerCase().includes(normalized)))
    }, [items, query])
    const visible = filtered.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)

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
                    <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setError(null); setEditing(null) }} sx={{ ml: { sm: 'auto' } }}>
                        {t('admin.add')}
                    </Button>
                </Stack>
                <TableContainer>
                    <Table size="small">
                        <TableHead><TableRow>
                            <TableCell>{t('admin.columns.name')}</TableCell><TableCell>Local name</TableCell>
                            <TableCell>{t('admin.columns.relationships')}</TableCell><TableCell align="right">{t('admin.columns.actions')}</TableCell>
                        </TableRow></TableHead>
                        <TableBody>
                            {loading ? <TableRow><TableCell colSpan={4} align="center" sx={{ py: 8 }}><CircularProgress size={28} /></TableCell></TableRow>
                                : visible.length === 0 ? <TableRow><TableCell colSpan={4} align="center" sx={{ py: 8, color: 'text.secondary' }}>{t('admin.noResults')}</TableCell></TableRow>
                                : visible.map(item => <TableRow hover key={item.localName}>
                                    <TableCell><Typography sx={{ fontWeight: 700 }}>{i18n.resolvedLanguage === 'en' ? item.labelEn ?? item.labelBg : item.labelBg ?? item.labelEn}</Typography></TableCell>
                                    <TableCell><Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{item.localName}</Typography></TableCell>
                                    <TableCell><Stack direction="row" sx={{ gap: .5, flexWrap: 'wrap' }}>{relations(item).slice(0, 3).map(value => <Chip key={value} label={value} size="small" variant="outlined" />)}{relations(item).length > 3 && <Chip label={`+${relations(item).length - 3}`} size="small" />}</Stack></TableCell>
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
                    rowsPerPageOptions={[5, 10, 25]} />
            </Paper>
            <OntologyEntityDialog open={editing !== undefined} type={type} entity={editing ?? null} options={options}
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

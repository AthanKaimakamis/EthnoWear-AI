import { useId, useMemo, useState } from 'react'
import {
    Box,
    Button,
    Checkbox,
    FormControl,
    InputAdornment,
    InputLabel,
    MenuItem,
    Select,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TableSortLabel,
    TextField,
    Typography,
} from '@mui/material'
import LinkIcon from '@mui/icons-material/Link'
import SearchIcon from '@mui/icons-material/Search'
import { useTranslation } from 'react-i18next'
import AppDialog from '../AppDialog.tsx'
import type { OptionCategory, SelectOption } from '../forms/formTypes.ts'

type Props = {
    label: string
    options: SelectOption[]
    categories: OptionCategory[]
    value: string[]
    disabled?: boolean
    onChange: (value: string[]) => void
}

type SortKey = 'name' | 'localName' | 'category'
type SortOrder = 'asc' | 'desc'

function displayLabel(option: SelectOption) {
    const technicalSuffix = ` (${option.value})`
    return option.label.endsWith(technicalSuffix)
        ? option.label.slice(0, -technicalSuffix.length)
        : option.label
}

function OntologyRelationshipField(props: Props) {
    const { t, i18n } = useTranslation()
    const categoryLabelId = useId()
    const [open, setOpen] = useState(false)
    const [draft, setDraft] = useState<Set<string>>(new Set())
    const [query, setQuery] = useState('')
    const [category, setCategory] = useState('')
    const [sortKey, setSortKey] = useState<SortKey>('name')
    const [sortOrder, setSortOrder] = useState<SortOrder>('asc')

    const categoryByOption = useMemo(() => {
        const result = new Map<string, string[]>()
        props.categories.forEach(item => item.optionValues.forEach(value => {
            result.set(value, [...(result.get(value) ?? []), item.label])
        }))
        return result
    }, [props.categories])

    const filteredOptions = useMemo(() => {
        const normalized = query.trim().toLocaleLowerCase()
        const categoryValues = props.categories.find(item => item.value === category)?.optionValues
        const valuesInCategory = categoryValues ? new Set(categoryValues) : null

        return props.options.filter(option => {
            const matchesQuery = !normalized || `${option.label} ${option.value}`.toLocaleLowerCase().includes(normalized)
            const matchesCategory = !valuesInCategory || valuesInCategory.has(option.value)
            return matchesQuery && matchesCategory
        }).sort((left, right) => {
            const leftValue = sortKey === 'name' ? displayLabel(left)
                : sortKey === 'localName' ? left.value
                    : (categoryByOption.get(left.value) ?? []).join(' ')
            const rightValue = sortKey === 'name' ? displayLabel(right)
                : sortKey === 'localName' ? right.value
                    : (categoryByOption.get(right.value) ?? []).join(' ')
            return leftValue.localeCompare(rightValue, i18n.resolvedLanguage) * (sortOrder === 'asc' ? 1 : -1)
        })
    }, [category, categoryByOption, i18n.resolvedLanguage, props.categories, props.options, query, sortKey, sortOrder])

    const selectedVisibleCount = filteredOptions.filter(option => draft.has(option.value)).length
    const allVisibleSelected = filteredOptions.length > 0 && selectedVisibleCount === filteredOptions.length
    const someVisibleSelected = selectedVisibleCount > 0 && !allVisibleSelected

    function showDialog() {
        setDraft(new Set(props.value))
        setQuery('')
        setCategory('')
        setOpen(true)
    }

    function cancel() {
        setOpen(false)
    }

    function save() {
        const optionOrder = new Map(props.options.map((option, index) => [option.value, index]))
        props.onChange([...draft].sort((left, right) => (
            (optionOrder.get(left) ?? Number.MAX_SAFE_INTEGER) - (optionOrder.get(right) ?? Number.MAX_SAFE_INTEGER)
        )))
        setOpen(false)
    }

    function toggle(value: string) {
        setDraft(current => {
            const next = new Set(current)
            if (next.has(value)) next.delete(value)
            else next.add(value)
            return next
        })
    }

    function toggleFiltered() {
        setDraft(current => {
            const next = new Set(current)
            if (allVisibleSelected) filteredOptions.forEach(option => next.delete(option.value))
            else filteredOptions.forEach(option => next.add(option.value))
            return next
        })
    }

    function changeSort(nextKey: SortKey) {
        if (sortKey === nextKey) {
            setSortOrder(current => current === 'asc' ? 'desc' : 'asc')
        } else {
            setSortKey(nextKey)
            setSortOrder('asc')
        }
    }

    return (
        <>
            <Box sx={{ border: 1, borderColor: 'divider', p: 2 }}>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: { sm: 'center' } }}>
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>{props.label}</Typography>
                        <Typography variant="body2" color="text.secondary">
                            {t('admin.relationshipSelector.selectedCount', { count: props.value.length })}
                        </Typography>
                    </Box>
                    <Button type="button" variant="outlined" startIcon={<LinkIcon />} disabled={props.disabled} onClick={showDialog}>
                        {t('admin.relationshipSelector.manageEntity', { entity: props.label })}
                    </Button>
                </Stack>
            </Box>

            <AppDialog
                open={open}
                onClose={cancel}
                maxWidth="lg"
                title={t('admin.relationshipSelector.title', { entity: props.label })}
            >
                <Stack spacing={2}>
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                        <TextField
                            size="small"
                            value={query}
                            onChange={event => setQuery(event.target.value)}
                            label={t('admin.relationshipSelector.search')}
                            sx={{ flex: 1 }}
                            slotProps={{
                                input: {
                                    startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment>,
                                },
                            }}
                        />
                        <FormControl size="small" sx={{ minWidth: { sm: 260 } }}>
                            <InputLabel id={categoryLabelId}>{t('admin.categoryFilter')}</InputLabel>
                            <Select
                                labelId={categoryLabelId}
                                value={category}
                                label={t('admin.categoryFilter')}
                                onChange={event => setCategory(event.target.value)}
                            >
                                <MenuItem value="">{t('admin.allCategories')}</MenuItem>
                                {props.categories.map(item => (
                                    <MenuItem key={item.value} value={item.value}>{item.label}</MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    </Stack>

                    <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
                        <Typography variant="body2" color="text.secondary">
                            {t('admin.relationshipSelector.selectionSummary', { selected: draft.size, visible: filteredOptions.length })}
                        </Typography>
                        <Button type="button" size="small" onClick={toggleFiltered} disabled={filteredOptions.length === 0}>
                            {allVisibleSelected
                                ? t('admin.relationshipSelector.clearFiltered')
                                : t('admin.relationshipSelector.selectFiltered')}
                        </Button>
                    </Stack>

                    <TableContainer sx={{ maxHeight: 440, border: 1, borderColor: 'divider' }}>
                        <Table stickyHeader size="small">
                            <TableHead>
                                <TableRow>
                                    <TableCell padding="checkbox">
                                        <Checkbox
                                            checked={allVisibleSelected}
                                            indeterminate={someVisibleSelected}
                                            disabled={filteredOptions.length === 0}
                                            onChange={toggleFiltered}
                                            slotProps={{ input: { 'aria-label': t('admin.relationshipSelector.selectFiltered') } }}
                                        />
                                    </TableCell>
                                    <TableCell sortDirection={sortKey === 'name' ? sortOrder : false}>
                                        <TableSortLabel
                                            active={sortKey === 'name'}
                                            direction={sortKey === 'name' ? sortOrder : 'asc'}
                                            onClick={() => changeSort('name')}
                                        >
                                            {t('admin.columns.name')}
                                        </TableSortLabel>
                                    </TableCell>
                                    <TableCell sortDirection={sortKey === 'localName' ? sortOrder : false}>
                                        <TableSortLabel
                                            active={sortKey === 'localName'}
                                            direction={sortKey === 'localName' ? sortOrder : 'asc'}
                                            onClick={() => changeSort('localName')}
                                        >
                                            Local name
                                        </TableSortLabel>
                                    </TableCell>
                                    <TableCell sortDirection={sortKey === 'category' ? sortOrder : false}>
                                        <TableSortLabel
                                            active={sortKey === 'category'}
                                            direction={sortKey === 'category' ? sortOrder : 'asc'}
                                            onClick={() => changeSort('category')}
                                        >
                                            {t('admin.categoryFilter')}
                                        </TableSortLabel>
                                    </TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {filteredOptions.length === 0 ? (
                                    <TableRow>
                                        <TableCell colSpan={4} align="center" sx={{ py: 6, color: 'text.secondary' }}>
                                            {t('admin.noResults')}
                                        </TableCell>
                                    </TableRow>
                                ) : filteredOptions.map(option => (
                                    <TableRow
                                        hover
                                        key={option.value}
                                        selected={draft.has(option.value)}
                                        onClick={() => toggle(option.value)}
                                        sx={{ cursor: 'pointer' }}
                                    >
                                        <TableCell padding="checkbox">
                                            <Checkbox
                                                checked={draft.has(option.value)}
                                                onClick={event => event.stopPropagation()}
                                                onChange={() => toggle(option.value)}
                                                slotProps={{ input: { 'aria-label': option.label } }}
                                            />
                                        </TableCell>
                                        <TableCell><Typography variant="body2" sx={{ fontWeight: 700 }}>{displayLabel(option)}</Typography></TableCell>
                                        <TableCell><Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{option.value}</Typography></TableCell>
                                        <TableCell>{(categoryByOption.get(option.value) ?? []).join(', ') || t('archiveReference.uncategorized')}</TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </TableContainer>

                    <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
                        <Button type="button" onClick={cancel}>{t('admin.cancel')}</Button>
                        <Button type="button" variant="contained" onClick={save}>{t('forms.save')}</Button>
                    </Stack>
                </Stack>
            </AppDialog>
        </>
    )
}

export default OntologyRelationshipField

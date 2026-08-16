import { useMemo, useState, type ReactNode } from 'react'
import {
    Box, Button, CircularProgress, IconButton, InputAdornment, Paper, Stack,
    Table, TableBody, TableCell, TableContainer, TableHead, TablePagination,
    TableRow, TableSortLabel, TextField, Tooltip,
} from '@mui/material'
import type { SxProps, Theme } from '@mui/material/styles'
import AddIcon from '@mui/icons-material/Add'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import SearchIcon from '@mui/icons-material/Search'
import { useTranslation } from 'react-i18next'

export type AdminTableColumn<T> = {
    key: string
    label: ReactNode
    render: (row: T) => ReactNode
    sortValue: (row: T) => string | number | boolean | null | undefined
    align?: 'left' | 'center' | 'right'
    cellSx?: SxProps<Theme>
}

type Props<T> = {
    rows: T[]
    columns: AdminTableColumn<T>[]
    loading: boolean
    getRowId: (row: T) => string | number
    searchableText: (row: T) => string
    searchPlaceholder: string
    toolbarFilters?: ReactNode
    defaultSortKey?: string
    rowsPerPageOptions?: number[]
    onAdd: () => void
    onEdit: (row: T) => void
    onDelete: (row: T) => void
}

function compare(left: unknown, right: unknown, locale: string) {
    return String(left ?? '').localeCompare(String(right ?? ''), locale, { numeric: true })
}

export default function AdminDataTable<T>({
    rows,
    columns,
    loading,
    getRowId,
    searchableText,
    searchPlaceholder,
    toolbarFilters,
    defaultSortKey,
    rowsPerPageOptions = [20, 50, 100],
    onAdd,
    onEdit,
    onDelete,
}: Props<T>) {
    const { t, i18n } = useTranslation()
    const initialSortKey = defaultSortKey && columns.some(column => column.key === defaultSortKey)
        ? defaultSortKey
        : columns[0]?.key ?? ''
    const [query, setQuery] = useState('')
    const [sortKey, setSortKey] = useState(initialSortKey)
    const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc')
    const [page, setPage] = useState(0)
    const [rowsPerPage, setRowsPerPage] = useState(rowsPerPageOptions[0] ?? 20)

    const filtered = useMemo(() => {
        const normalized = query.trim().toLocaleLowerCase()
        return normalized
            ? rows.filter(row => searchableText(row).toLocaleLowerCase().includes(normalized))
            : rows
    }, [query, rows, searchableText])

    const sorted = useMemo(() => {
        const column = columns.find(candidate => candidate.key === sortKey)
        if (!column) return filtered
        const direction = sortOrder === 'asc' ? 1 : -1
        return [...filtered].sort((left, right) => compare(column.sortValue(left), column.sortValue(right), i18n.resolvedLanguage ?? 'bg') * direction)
    }, [columns, filtered, i18n.resolvedLanguage, sortKey, sortOrder])

    const lastPage = Math.max(0, Math.ceil(sorted.length / rowsPerPage) - 1)
    const currentPage = Math.min(page, lastPage)
    const visibleRows = sorted.slice(currentPage * rowsPerPage, currentPage * rowsPerPage + rowsPerPage)

    function changeSort(nextKey: string) {
        if (sortKey === nextKey) setSortOrder(current => current === 'asc' ? 'desc' : 'asc')
        else { setSortKey(nextKey); setSortOrder('asc') }
        setPage(0)
    }

    return (
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ p: 2 }}>
                <TextField
                    size="small"
                    value={query}
                    onChange={event => { setQuery(event.target.value); setPage(0) }}
                    placeholder={searchPlaceholder}
                    sx={{ flex: 1, maxWidth: 560 }}
                    slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
                />
                {toolbarFilters}
                <Button variant="contained" startIcon={<AddIcon />} onClick={onAdd} sx={{ ml: { sm: 'auto' } }}>
                    {t('admin.add')}
                </Button>
            </Stack>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            {columns.map(column => (
                                <TableCell key={column.key} align={column.align} sortDirection={sortKey === column.key ? sortOrder : false}>
                                    <TableSortLabel
                                        active={sortKey === column.key}
                                        direction={sortKey === column.key ? sortOrder : 'asc'}
                                        onClick={() => changeSort(column.key)}
                                    >
                                        {column.label}
                                    </TableSortLabel>
                                </TableCell>
                            ))}
                            <TableCell align="right">{t('admin.columns.actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {loading ? (
                            <TableRow><TableCell colSpan={columns.length + 1} align="center" sx={{ py: 8 }}><CircularProgress size={28} /></TableCell></TableRow>
                        ) : visibleRows.length === 0 ? (
                            <TableRow><TableCell colSpan={columns.length + 1} align="center" sx={{ py: 8, color: 'text.secondary' }}>{t('admin.noResults')}</TableCell></TableRow>
                        ) : visibleRows.map(row => (
                            <TableRow hover key={getRowId(row)}>
                                {columns.map(column => (
                                    <TableCell key={column.key} align={column.align} sx={column.cellSx}>
                                        {column.render(row)}
                                    </TableCell>
                                ))}
                                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                                    <Tooltip title={t('admin.edit')}>
                                        <IconButton size="small" onClick={() => onEdit(row)}><EditOutlinedIcon fontSize="small" /></IconButton>
                                    </Tooltip>
                                    <Tooltip title={t('admin.delete')}>
                                        <IconButton size="small" color="error" onClick={() => onDelete(row)}><DeleteOutlineIcon fontSize="small" /></IconButton>
                                    </Tooltip>
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>
            <Box>
                <TablePagination
                    component="div"
                    count={filtered.length}
                    page={currentPage}
                    rowsPerPage={rowsPerPage}
                    onPageChange={(_, value) => setPage(value)}
                    onRowsPerPageChange={event => { setRowsPerPage(Number(event.target.value)); setPage(0) }}
                    rowsPerPageOptions={rowsPerPageOptions}
                />
            </Box>
        </Paper>
    )
}

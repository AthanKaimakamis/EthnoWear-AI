import { useMemo, useState } from 'react'
import { Alert, Box, Button, Collapse, FormControl, IconButton, InputAdornment, InputLabel, LinearProgress, MenuItem, Paper, Select, Skeleton, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TableSortLabel, TextField, Tooltip, Typography } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import FilterAltOutlinedIcon from '@mui/icons-material/FilterAltOutlined'
import OpenInNewOutlinedIcon from '@mui/icons-material/OpenInNewOutlined'
import SearchIcon from '@mui/icons-material/Search'
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router'
import { documentQueryKeys, listDocuments } from '../../api/DocumentAdminApi'
import { sourceReferencesApi, sourcesApi } from '../../api/ArchiveAdminApi'
import { apiErrorMessage } from '../../api/http'
import AdminPageHeader from '../../components/admin/AdminPageHeader'
import DocumentStatusChip from '../../components/admin/document/DocumentStatusChip'
import DocumentUploadDialog from '../../components/admin/document/DocumentUploadDialog'
import { documentTypes, indexingStates, processingStates, provenanceStatuses, provenanceTrustStates, reviewStates } from '../../components/admin/document/documentOptions'
import useDebouncedValue from '../../hooks/useDebouncedValue'
import type { DocumentQuery, DocumentSummary } from '../../types/document'

const pageSizes = [20, 50, 100]
const allowedSorts = ['title', 'language', 'processingState', 'reviewState', 'indexingState', 'updatedAt'] as const
type SortProperty = typeof allowedSorts[number]

function optionalEnum<T extends string>(value: string | null, options: readonly T[]): T | undefined {
    return value && options.includes(value as T) ? value as T : undefined
}

export default function DocumentsPage() {
    const { t, i18n } = useTranslation()
    const navigate = useNavigate()
    const queryClient = useQueryClient()
    const [params, setParams] = useSearchParams()
    const [search, setSearch] = useState(params.get('search') ?? '')
    const [filtersOpen, setFiltersOpen] = useState(params.size > 3)
    const [uploadOpen, setUploadOpen] = useState(false)
    const [notice, setNotice] = useState<string | null>(null)
    const debouncedSearch = useDebouncedValue(search, 350)
    const page = Math.max(0, Number(params.get('page') ?? 0) || 0)
    const size = pageSizes.includes(Number(params.get('size'))) ? Number(params.get('size')) : 20
    const [rawProperty, rawDirection] = (params.get('sort') ?? 'updatedAt,desc').split(',')
    const sortProperty = allowedSorts.includes(rawProperty as SortProperty) ? rawProperty as SortProperty : 'updatedAt'
    const sortDirection = rawDirection === 'asc' ? 'asc' : 'desc'
    const query = useMemo<DocumentQuery>(() => ({
        page, size, sort: `${sortProperty},${sortDirection}`, searchText: debouncedSearch.trim() || undefined,
        documentType: optionalEnum(params.get('type'), documentTypes),
        provenanceStatus: optionalEnum(params.get('provenance'), provenanceStatuses),
        provenanceTrustState: optionalEnum(params.get('trust'), provenanceTrustStates),
        processingState: optionalEnum(params.get('processing'), processingStates),
        reviewState: optionalEnum(params.get('review'), reviewStates),
        indexingState: optionalEnum(params.get('indexing'), indexingStates),
        language: params.get('language') || undefined,
        sourceId: params.get('source') ? Number(params.get('source')) : undefined,
    }), [debouncedSearch, page, params, size, sortDirection, sortProperty])
    const documentsQuery = useQuery({ queryKey: documentQueryKeys.list(query), queryFn: ({ signal }) => listDocuments(query, signal), placeholderData: keepPreviousData })
    const sourcesQuery = useQuery({ queryKey: ['admin', 'sources', 'document-filter'], queryFn: ({ signal }) => sourcesApi.findAll({ size: 100, sort: 'title,asc' }, signal), staleTime: 300_000 })
    const referencesQuery = useQuery({ queryKey: ['admin', 'source-references', 'document-upload'], queryFn: ({ signal }) => sourceReferencesApi.findAll({ size: 100 }, signal), staleTime: 300_000 })
    const sources = sourcesQuery.data?.content ?? []
    const filterKeys = ['type', 'provenance', 'trust', 'processing', 'review', 'indexing', 'language', 'source']
    const activeFilterCount = filterKeys.filter(key => params.has(key)).length

    function updateParam(key: string, value?: string) {
        setParams(current => { const next = new URLSearchParams(current); if (value) next.set(key, value); else next.delete(key); if (key !== 'page') next.delete('page'); return next })
    }
    function clearFilters() {
        setSearch('')
        setParams(current => { const next = new URLSearchParams(); if (current.get('size')) next.set('size', current.get('size')!); if (current.get('sort')) next.set('sort', current.get('sort')!); return next })
    }
    function changeSort(property: SortProperty) {
        updateParam('sort', `${property},${property === sortProperty && sortDirection === 'asc' ? 'desc' : 'asc'}`)
    }

    return <Stack spacing={3}>
        <AdminPageHeader title={t('documents.title')} description={t('documents.description')} actions={<Button variant="contained" startIcon={<AddIcon />} onClick={() => setUploadOpen(true)}>{t('documents.upload')}</Button>} />
        {notice && <Alert severity="success" onClose={() => setNotice(null)}>{notice}</Alert>}
        {documentsQuery.isError && <Alert severity="error" action={<Button color="inherit" onClick={() => documentsQuery.refetch()}>{t('documents.retry')}</Button>}>{apiErrorMessage(documentsQuery.error, t('documents.loadFailed'))}</Alert>}
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ p: 2, alignItems: { md: 'center' } }}>
                <TextField value={search} onChange={event => { setSearch(event.target.value); updateParam('search', event.target.value || undefined) }} size="small" placeholder={t('documents.search')} sx={{ flex: 1, maxWidth: 560 }} slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
                <Button variant={activeFilterCount ? 'contained' : 'outlined'} startIcon={<FilterAltOutlinedIcon />} onClick={() => setFiltersOpen(value => !value)}>{t('documents.filters')}{activeFilterCount > 0 && ` (${activeFilterCount})`}</Button>
                {(activeFilterCount > 0 || search) && <Button onClick={clearFilters}>{t('documents.clearFilters')}</Button>}
            </Stack>
            <Collapse in={filtersOpen}><Box sx={{ px: 2, pb: 2, display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' }, gap: 1.5 }}>
                <FilterSelect label={t('documents.filter.type')} value={params.get('type') ?? ''} onChange={value => updateParam('type', value)} options={documentTypes.map(value => ({ value, label: t(`documents.type.${value}`) }))} />
                <FilterSelect label={t('documents.filter.provenance')} value={params.get('provenance') ?? ''} onChange={value => updateParam('provenance', value)} options={provenanceStatuses.map(value => ({ value, label: t(`documents.status.provenance.${value}`) }))} />
                <FilterSelect label={t('documents.filter.trust')} value={params.get('trust') ?? ''} onChange={value => updateParam('trust', value)} options={provenanceTrustStates.map(value => ({ value, label: t(`documents.status.trust.${value}`) }))} />
                <FilterSelect label={t('documents.filter.processing')} value={params.get('processing') ?? ''} onChange={value => updateParam('processing', value)} options={processingStates.map(value => ({ value, label: t(`documents.status.processing.${value}`) }))} />
                <FilterSelect label={t('documents.filter.review')} value={params.get('review') ?? ''} onChange={value => updateParam('review', value)} options={reviewStates.map(value => ({ value, label: t(`documents.status.review.${value}`) }))} />
                <FilterSelect label={t('documents.filter.indexing')} value={params.get('indexing') ?? ''} onChange={value => updateParam('indexing', value)} options={indexingStates.map(value => ({ value, label: t(`documents.status.indexing.${value}`) }))} />
                <FilterSelect label={t('documents.filter.language')} value={params.get('language') ?? ''} onChange={value => updateParam('language', value)} options={[{ value: 'bg', label: 'Български' }, { value: 'en', label: 'English' }]} />
                <FilterSelect label={t('documents.filter.source')} value={params.get('source') ?? ''} onChange={value => updateParam('source', value)} options={sources.map(source => ({ value: String(source.id), label: source.title }))} />
            </Box></Collapse>
            {documentsQuery.isFetching && !documentsQuery.isPending && <LinearProgress />}
            <TableContainer><Table size="small" aria-label={t('documents.title')}><TableHead><TableRow>
                <SortableHeader active={sortProperty === 'title'} direction={sortDirection} onClick={() => changeSort('title')}>{t('documents.columns.title')}</SortableHeader>
                <TableCell>{t('documents.columns.source')}</TableCell>
                <SortableHeader active={sortProperty === 'language'} direction={sortDirection} onClick={() => changeSort('language')}>{t('documents.columns.language')}</SortableHeader>
                <TableCell align="right">{t('documents.columns.pages')}</TableCell>
                <SortableHeader active={sortProperty === 'processingState'} direction={sortDirection} onClick={() => changeSort('processingState')}>{t('documents.columns.processing')}</SortableHeader>
                <SortableHeader active={sortProperty === 'reviewState'} direction={sortDirection} onClick={() => changeSort('reviewState')}>{t('documents.columns.review')}</SortableHeader>
                <TableCell>{t('documents.columns.trust')}</TableCell>
                <SortableHeader active={sortProperty === 'indexingState'} direction={sortDirection} onClick={() => changeSort('indexingState')}>{t('documents.columns.indexing')}</SortableHeader>
                <SortableHeader active={sortProperty === 'updatedAt'} direction={sortDirection} onClick={() => changeSort('updatedAt')}>{t('documents.columns.updated')}</SortableHeader>
                <TableCell align="right">{t('documents.columns.actions')}</TableCell>
            </TableRow></TableHead><TableBody>
                {documentsQuery.isPending ? Array.from({ length: 8 }, (_, index) => <DocumentSkeletonRow key={index} />) : documentsQuery.data?.content.length === 0 ? <TableRow><TableCell colSpan={10} align="center" sx={{ py: 8, color: 'text.secondary' }}>{t('documents.noResults')}</TableCell></TableRow> : documentsQuery.data?.content.map(document => <DocumentRow key={document.id} document={document} language={i18n.resolvedLanguage ?? 'bg'} onOpen={() => navigate(`/admin/documents/${document.id}`)} />)}
            </TableBody></Table></TableContainer>
            <TablePagination component="div" count={documentsQuery.data?.totalElements ?? 0} page={page} rowsPerPage={size} rowsPerPageOptions={pageSizes} onPageChange={(_, value) => updateParam('page', String(value))} onRowsPerPageChange={event => updateParam('size', event.target.value)} />
        </Paper>
        <DocumentUploadDialog open={uploadOpen} sources={sources} references={referencesQuery.data?.content ?? []} onClose={() => setUploadOpen(false)} onSourceCreated={() => { void queryClient.invalidateQueries({ queryKey: ['admin', 'sources'] }) }} onUploaded={(result, kind, queued) => { setUploadOpen(false); void queryClient.invalidateQueries({ queryKey: documentQueryKeys.all }); setNotice(t(kind === 'pdf' ? 'documents.uploadDialog.pdfQueued' : queued ? 'documents.uploadDialog.captureQueued' : 'documents.uploadDialog.captureSaved')); navigate(`/admin/documents/${result.documentId}`) }} />
    </Stack>
}

function FilterSelect({ label, value, options, onChange }: { label: string; value: string; options: { value: string; label: string }[]; onChange: (value?: string) => void }) {
    const { t } = useTranslation()
    return <FormControl size="small" fullWidth><InputLabel>{label}</InputLabel><Select label={label} value={value} onChange={event => onChange(event.target.value || undefined)}><MenuItem value=""><em>{t('documents.filter.all')}</em></MenuItem>{options.map(option => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
}
function SortableHeader({ active, direction, onClick, children }: { active: boolean; direction: 'asc' | 'desc'; onClick: () => void; children: React.ReactNode }) {
    return <TableCell sortDirection={active ? direction : false}><TableSortLabel active={active} direction={active ? direction : 'asc'} onClick={onClick}>{children}</TableSortLabel></TableCell>
}
function DocumentRow({ document, language, onOpen }: { document: DocumentSummary; language: string; onOpen: () => void }) {
    const { t } = useTranslation()
    return (
        <TableRow hover onClick={onOpen} sx={{ cursor: 'pointer' }}>
            <TableCell>
                <Typography sx={{ fontWeight: 700 }}>{document.title}</Typography>
                <Typography variant="caption" color="text.secondary">
                    {[t(`documents.type.${document.documentType}`), document.author, document.publicationYear].filter(Boolean).join(' · ')}
                </Typography>
            </TableCell>
            <TableCell>{document.sourceTitle ?? '—'}</TableCell>
            <TableCell>{document.language?.toUpperCase() ?? '—'}</TableCell>
            <TableCell align="right">
                <Typography variant="body2">{t('documents.progress.pages', { count: document.progress.totalPages })}</Typography>
                <Typography variant="caption" color="text.secondary">{t('documents.progress.approved', { approved: document.progress.approvedTranscriptionPages, total: document.progress.totalPages })}</Typography>
            </TableCell>
            <TableCell><DocumentStatusChip kind="processing" value={document.processingState} /></TableCell>
            <TableCell><DocumentStatusChip kind="review" value={document.reviewState} /></TableCell>
            <TableCell><DocumentStatusChip kind="trust" value={document.provenanceTrustState} /></TableCell>
            <TableCell><DocumentStatusChip kind="indexing" value={document.indexingState} /></TableCell>
            <TableCell>{new Intl.DateTimeFormat(language, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(document.updatedAt))}</TableCell>
            <TableCell align="right">
                <Tooltip title={t('curator.actions.details')}>
                    <IconButton size="small" onClick={event => { event.stopPropagation(); onOpen() }}>
                        <OpenInNewOutlinedIcon fontSize="small" />
                    </IconButton>
                </Tooltip>
            </TableCell>
        </TableRow>
    )
}
function DocumentSkeletonRow() { return <TableRow>{Array.from({ length: 10 }, (_, index) => <TableCell key={index}><Skeleton width={index === 0 ? 180 : 90} /></TableCell>)}</TableRow> }

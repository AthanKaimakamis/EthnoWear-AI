import { useEffect, useMemo, useState } from 'react'
import {
    Alert,
    Box,
    Button,
    Card,
    CardContent,
    CardMedia,
    Collapse,
    FormControl,
    Grid,
    MenuItem,
    Pagination,
    Select,
    Stack,
    Typography,
} from '@mui/material'
import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import FilterListIcon from '@mui/icons-material/FilterList'
import type { SelectChangeEvent } from '@mui/material/Select'
import { useTranslation } from 'react-i18next'
import { searchCatalogue } from '../api/CatalogueApi'
import type { Language, ReferenceResource } from '../types/reference'
import type {
    CatalogFacetType,
    ConceptCatalogResultDetails,
    OntologyFeatureType,
} from '../types/catalogue'
import {
    emptyEmbroideryFilters,
    type EmbroideryFilterOptions,
    type EmbroideryFilters,
    type FilterCombinationMode,
} from '../types/embroideryFilters'
import EmbroideryFilterPanel from '../components/embroidery/EmbroideryFilterPanel'
import EmbroideryPageSkeleton from '../components/loading/EmbroideryPageSkeleton'

function facetOptions(
    catalogue: ConceptCatalogResultDetails | null,
    facetType: CatalogFacetType,
    entityType: OntologyFeatureType,
): ReferenceResource[] {
    return catalogue?.facets
        .find((facet) => facet.facetType === facetType && facet.entityType === entityType)
        ?.values
        .filter((value) => value.selected || value.count > 0)
        .map((value) => ({
            iri: value.iri,
            localName: value.localName,
            label: value.label,
        })) ?? []
}

function ArchivePage() {
    const { t, i18n } = useTranslation()
    const language: Language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
    const [catalogue, setCatalogue] = useState<ConceptCatalogResultDetails | null>(null)
    const [filters, setFilters] = useState<EmbroideryFilters>(emptyEmbroideryFilters)
    const [combinationMode, setCombinationMode] = useState<FilterCombinationMode>('and')
    const [page, setPage] = useState(1)
    const [itemsPerPage, setItemsPerPage] = useState(12)
    const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        const controller = new AbortController()

        async function loadCatalogue() {
            try {
                setError(null)
                const data = await searchCatalogue({
                    entityType: 'REGIONAL_EMBROIDERY',
                    language,
                    relatedEntityLocalNames: {
                        REGION: filters.regionLocalNames,
                        ORNAMENT: filters.ornamentLocalNames,
                        TECHNIQUE: filters.techniqueLocalNames,
                    },
                    relatedCategoryLocalNames: {
                        REGION: filters.regionGroupLocalNames,
                        ORNAMENT: filters.ornamentTypeLocalNames,
                    },
                    combinationMode: combinationMode.toUpperCase() as 'AND' | 'OR',
                }, {
                    page: page - 1,
                    size: itemsPerPage,
                    sort: 'label,asc',
                }, controller.signal)

                setCatalogue(data)
            } catch (err) {
                if (!controller.signal.aborted) {
                    const message =
                        err instanceof Error
                            ? err.message
                            : t('archive.loadError')

                    setError(message)
                }
            } finally {
                if (!controller.signal.aborted) {
                    setLoading(false)
                }
            }
        }

        void loadCatalogue()

        return () => {
            controller.abort()
        }
    }, [combinationMode, filters, itemsPerPage, language, page, t])

    const allFilterOptions: EmbroideryFilterOptions = useMemo(() => {
        return {
            regionGroups: facetOptions(catalogue, 'RELATED_CATEGORY', 'REGION'),
            regions: facetOptions(catalogue, 'RELATED_ENTITY', 'REGION'),
            ornamentTypes: facetOptions(catalogue, 'RELATED_CATEGORY', 'ORNAMENT'),
            ornaments: facetOptions(catalogue, 'RELATED_ENTITY', 'ORNAMENT'),
            techniques: facetOptions(catalogue, 'RELATED_ENTITY', 'TECHNIQUE'),
        }
    }, [catalogue])

    const filteredRegionalEmbroideries = catalogue?.items ?? []

    const filterOptions: EmbroideryFilterOptions = useMemo(() => {
        return allFilterOptions
    }, [allFilterOptions])

    const selectedFilterCount =
        filters.regionGroupLocalNames.length +
        filters.regionLocalNames.length +
        filters.ornamentTypeLocalNames.length +
        filters.ornamentLocalNames.length +
        filters.techniqueLocalNames.length

    const pageCount = catalogue?.page.totalPages ?? 0
    const firstResultIndex = (page - 1) * itemsPerPage
    const totalResults = catalogue?.page.totalElements ?? 0

    function handleFiltersChange(nextFilters: EmbroideryFilters) {
        setFilters(nextFilters)
        setPage(1)
    }

    function handleClearFilters() {
        setFilters(emptyEmbroideryFilters)
        setPage(1)
    }

    function handleItemsPerPageChange(event: SelectChangeEvent<number>) {
        setItemsPerPage(Number(event.target.value))
        setPage(1)
    }

    if (loading) {
        return <EmbroideryPageSkeleton />
    }

    return (
        <Box
            sx={{
                display: 'grid',
                gridTemplateColumns: {
                    xs: 'minmax(0, 1fr)',
                    md: '300px minmax(0, 1fr)',
                },
                gap: 3,
                px: { xs: 2, md: 5 },
                py: 3,
                alignItems: 'start',
                textAlign: 'left',
            }}
        >
                <Box sx={{ display: { xs: 'none', md: 'block' } }}>
                    <EmbroideryFilterPanel
                        filters={filters}
                        options={filterOptions}
                        combinationMode={combinationMode}
                        onCombinationModeChange={mode => { setCombinationMode(mode); setPage(1) }}
                        onChange={handleFiltersChange}
                        onClear={handleClearFilters}
                    />
                </Box>

                <Box sx={{ display: { xs: 'block', md: 'none' } }}>
                    <Button
                        variant="text"
                        color="inherit"
                        aria-expanded={mobileFiltersOpen}
                        aria-controls="mobile-embroidery-filters"
                        fullWidth
                        onClick={() => setMobileFiltersOpen((open) => !open)}
                        sx={{
                            minHeight: 52,
                            px: 2,
                            py: 1.25,
                            border: 1,
                            borderLeft: 4,
                            borderColor: 'divider',
                            borderLeftColor: 'primary.main',
                            borderRadius: 1,
                            bgcolor: '#EEF1F1',
                            '&:hover': {
                                bgcolor: '#E4E8E7',
                            },
                        }}
                    >
                        <Box
                            sx={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                width: '100%',
                            }}
                        >
                            <Stack
                                direction="row"
                                spacing={1.25}
                                sx={{ alignItems: 'center' }}
                            >
                                <FilterListIcon color="primary" fontSize="small" />
                                <Typography
                                    component="span"
                                    variant="body2"
                                    sx={{ fontWeight: 700 }}
                                >
                                    {mobileFiltersOpen ? t('filters.hide') : t('filters.show')}
                                </Typography>
                            </Stack>

                            {mobileFiltersOpen ? (
                                <ExpandLessIcon fontSize="small" />
                            ) : (
                                <ExpandMoreIcon fontSize="small" />
                            )}
                        </Box>
                    </Button>

                    <Collapse in={mobileFiltersOpen} unmountOnExit>
                        <Box id="mobile-embroidery-filters" sx={{ mt: 1.5 }}>
                            <EmbroideryFilterPanel
                                filters={filters}
                                options={filterOptions}
                                combinationMode={combinationMode}
                                onCombinationModeChange={mode => { setCombinationMode(mode); setPage(1) }}
                                onChange={handleFiltersChange}
                                onClear={handleClearFilters}
                            />
                        </Box>
                    </Collapse>
                </Box>

                <Box sx={{ minWidth: 0 }}>
                    <Stack spacing={3} sx={{ minWidth: 0 }}>
                        <Box sx={{ pt: 0.25 }}>
                            <Typography variant="h4" sx={{ fontWeight: 800 }}>
                                {t('archive.title')}
                            </Typography>

                            <Typography color="text.secondary">
                                {t('archive.subtitle')}
                            </Typography>
                        </Box>

                        {error && (
                            <Alert severity="error">
                                {error}
                            </Alert>
                        )}

                        {selectedFilterCount > 0 && (
                            <Alert
                                severity="info"
                                action={
                                    <Button
                                        color="inherit"
                                        size="small"
                                        onClick={handleClearFilters}
                                        sx={{ whiteSpace: 'nowrap' }}
                                    >
                                        {t('filters.clearShort')}
                                    </Button>
                                }
                            >
                                {t('archive.selectedFilters', { count: selectedFilterCount })}
                            </Alert>
                        )}

                        {!error && filteredRegionalEmbroideries.length === 0 && (
                            <Alert severity="warning">
                                {t('archive.noResults')}
                            </Alert>
                        )}

                        {filteredRegionalEmbroideries.length > 0 && (
                            <Stack
                                direction={{ xs: 'column', sm: 'row' }}
                                spacing={2}
                                sx={{
                                    alignItems: { xs: 'stretch', sm: 'center' },
                                    justifyContent: 'space-between',
                                }}
                            >
                                <Typography variant="body2" color="text.secondary">
                                    {t('archive.showing', {
                                        from: firstResultIndex + 1,
                                        to: Math.min(
                                            firstResultIndex + itemsPerPage,
                                            totalResults
                                        ),
                                        total: totalResults,
                                    })}
                                </Typography>

                                <Stack
                                    direction="row"
                                    spacing={1.25}
                                    sx={{ alignItems: 'center' }}
                                >
                                    <Typography
                                        component="span"
                                        variant="body2"
                                        color="text.secondary"
                                        sx={{ whiteSpace: 'nowrap' }}
                                    >
                                        {t('archive.itemsPerPage')}
                                    </Typography>

                                    <FormControl size="small">
                                        <Select
                                            id="items-per-page"
                                            value={itemsPerPage}
                                            onChange={handleItemsPerPageChange}
                                            inputProps={{
                                                'aria-label': t('archive.itemsPerPage'),
                                            }}
                                            sx={{
                                                minWidth: 76,
                                                fontWeight: 600,
                                            }}
                                        >
                                            {[12, 24, 48].map((option) => (
                                                <MenuItem key={option} value={option}>
                                                    {option}
                                                </MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                </Stack>
                            </Stack>
                        )}

                        <Grid container spacing={3}>
                            {filteredRegionalEmbroideries.map((item) => (
                                <Grid
                                    key={item.localName}
                                    size={{ xs: 12, sm: 6, lg: 4 }}
                                >
                                    <Card
                                        sx={{
                                            height: '100%',
                                            display: 'flex',
                                            flexDirection: 'column',
                                            borderRadius: 1,
                                            bgcolor: 'background.paper',
                                            transition: 'transform 160ms ease, box-shadow 160ms ease',
                                            '&:hover': {
                                                transform: 'translateY(-2px)',
                                                boxShadow: '0 6px 16px rgba(24, 28, 24, 0.15)',
                                            },
                                        }}
                                    >
                                        <CardMedia
                                            component="img"
                                            image="/Image-not-found.png"
                                            alt={item.label || item.localName}
                                            sx={{
                                                height: 150,
                                                objectFit: 'contain',
                                                bgcolor: '#F1F2F5',
                                                borderBottom: 1,
                                                borderColor: 'divider',
                                            }}
                                        />

                                        <CardContent sx={{ flexGrow: 1, p: 2 }}>
                                            <Stack spacing={1.5}>
                                                <Box>
                                                    <Typography
                                                        variant="h6"
                                                        sx={{ fontWeight: 700 }}
                                                    >
                                                        {item.label || item.localName}
                                                    </Typography>

                                                    <Typography
                                                        variant="body2"
                                                        color="text.secondary"
                                                    >
                                                        {item.comment || t('archive.fallbackDescription')}
                                                    </Typography>
                                                </Box>

                                            </Stack>
                                        </CardContent>
                                    </Card>
                                </Grid>
                            ))}
                        </Grid>

                        {pageCount > 1 && (
                            <Box
                                sx={{
                                    display: 'flex',
                                    justifyContent: 'center',
                                    pt: 1,
                                }}
                            >
                                <Pagination
                                    count={pageCount}
                                    page={page}
                                    onChange={(_, nextPage) => setPage(nextPage)}
                                    color="primary"
                                    variant="outlined"
                                    shape="rounded"
                                    showFirstButton
                                    showLastButton
                                />
                            </Box>
                        )}
                    </Stack>
                </Box>
        </Box>
    )
}

export default ArchivePage

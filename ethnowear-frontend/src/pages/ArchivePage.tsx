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
import { getFullReference } from '../api/ReferenceApi'
import type { Language, ReferenceData, ReferenceResource } from '../types/reference'
import {
    emptyEmbroideryFilters,
    type EmbroideryFilterOptions,
    type EmbroideryFilters,
    type FilterCombinationMode,
} from '../types/embroideryFilters'
import EmbroideryFilterPanel from '../components/embroidery/EmbroideryFilterPanel'
import EmbroideryPageSkeleton from '../components/loading/EmbroideryPageSkeleton'

type FilterKey = keyof EmbroideryFilters

function matchesEmbroidery(
    embroideryLocalName: string,
    filters: EmbroideryFilters,
    reference: ReferenceData,
    combinationMode: FilterCombinationMode,
) {
    const regionLocalName = reference.regionByRegionalEmbroidery[embroideryLocalName]
    const regionOrnaments = reference.ornamentsByRegion[regionLocalName] ?? []
    const regionTechniques = reference.techniquesByRegion?.[regionLocalName] ?? []
    const activeGroupMatches: boolean[] = []

    if (filters.regionGroupLocalNames.length > 0) {
        activeGroupMatches.push(filters.regionGroupLocalNames.some((groupLocalName) =>
            reference.regionsByRegionGroup[groupLocalName]?.includes(regionLocalName)
        ))
    }

    if (filters.regionLocalNames.length > 0) {
        activeGroupMatches.push(filters.regionLocalNames.includes(regionLocalName))
    }

    if (filters.ornamentLocalNames.length > 0) {
        activeGroupMatches.push(filters.ornamentLocalNames.some((ornamentLocalName) =>
            regionOrnaments.includes(ornamentLocalName)
        ))
    }

    if (filters.ornamentTypeLocalNames.length > 0) {
        const selectedTypeOrnaments = new Set(
            filters.ornamentTypeLocalNames.flatMap(
                (typeLocalName) => reference.ornamentsByType[typeLocalName] ?? []
            )
        )

        activeGroupMatches.push(
            regionOrnaments.some((ornamentLocalName) => selectedTypeOrnaments.has(ornamentLocalName))
        )
    }

    if (filters.techniqueLocalNames.length > 0) {
        activeGroupMatches.push(filters.techniqueLocalNames.some((techniqueLocalName) =>
            regionTechniques.includes(techniqueLocalName)
        ))
    }

    if (activeGroupMatches.length === 0) {
        return true
    }

    return combinationMode === 'and'
        ? activeGroupMatches.every(Boolean)
        : activeGroupMatches.some(Boolean)
}

function visibleOptions(
    options: ReferenceResource[],
    filterKey: FilterKey,
    filters: EmbroideryFilters,
    reference: ReferenceData,
    combinationMode: FilterCombinationMode,
) {
    const selectedValues = filters[filterKey]

    return options.filter((option) => {
        if (selectedValues.includes(option.localName)) {
            return true
        }

        const candidateFilters: EmbroideryFilters = {
            ...filters,
            [filterKey]: [option.localName],
        }

        return reference.regionalEmbroideryTypes.some((embroidery) =>
            matchesEmbroidery(embroidery.localName, candidateFilters, reference, combinationMode)
        )
    })
}

function ArchivePage() {
    const { t, i18n } = useTranslation()
    const language: Language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
    const [reference, setReference] = useState<ReferenceData | null>(null)
    const [filters, setFilters] = useState<EmbroideryFilters>(emptyEmbroideryFilters)
    const [combinationMode, setCombinationMode] = useState<FilterCombinationMode>('and')
    const [page, setPage] = useState(1)
    const [itemsPerPage, setItemsPerPage] = useState(12)
    const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        let ignore = false

        async function loadReferenceData() {
            try {
                setLoading(true)
                setError(null)

                const data = await getFullReference(language)

                if (!ignore) {
                    setReference(data)
                }
            } catch (err) {
                if (!ignore) {
                    const message =
                        err instanceof Error
                            ? err.message
                            : t('archive.loadError')

                    setError(message)
                }
            } finally {
                if (!ignore) {
                    setLoading(false)
                }
            }
        }

        loadReferenceData()

        return () => {
            ignore = true
        }
    }, [language, t])

    const allFilterOptions: EmbroideryFilterOptions = useMemo(() => {
        return {
            regionGroups: reference?.regionGroups ?? [],
            regions: reference?.regions ?? [],
            ornamentTypes: reference?.ornamentTypes ?? [],
            ornaments: reference?.ornaments ?? [],
            techniques: reference?.techniques ?? [],
        }
    }, [reference])

    const filteredRegionalEmbroideries = useMemo(() => {
        const allItems = reference?.regionalEmbroideryTypes ?? []

        if (!reference) {
            return []
        }

        return allItems.filter((item) => matchesEmbroidery(
            item.localName, filters, reference, combinationMode
        ))
    }, [combinationMode, filters, reference])

    const filterOptions: EmbroideryFilterOptions = useMemo(() => {
        if (!reference) {
            return allFilterOptions
        }

        return {
            regionGroups: visibleOptions(
                allFilterOptions.regionGroups,
                'regionGroupLocalNames',
                filters,
                reference,
                combinationMode
            ),
            regions: visibleOptions(
                allFilterOptions.regions,
                'regionLocalNames',
                filters,
                reference,
                combinationMode
            ),
            ornamentTypes: visibleOptions(
                allFilterOptions.ornamentTypes,
                'ornamentTypeLocalNames',
                filters,
                reference,
                combinationMode
            ),
            ornaments: visibleOptions(
                allFilterOptions.ornaments,
                'ornamentLocalNames',
                filters,
                reference,
                combinationMode
            ),
            techniques: visibleOptions(
                allFilterOptions.techniques,
                'techniqueLocalNames',
                filters,
                reference,
                combinationMode
            ),
        }
    }, [allFilterOptions, combinationMode, filters, reference])

    const selectedFilterCount =
        filters.regionGroupLocalNames.length +
        filters.regionLocalNames.length +
        filters.ornamentTypeLocalNames.length +
        filters.ornamentLocalNames.length +
        filters.techniqueLocalNames.length

    const pageCount = Math.ceil(filteredRegionalEmbroideries.length / itemsPerPage)
    const firstResultIndex = (page - 1) * itemsPerPage

    const paginatedRegionalEmbroideries = filteredRegionalEmbroideries.slice(
        firstResultIndex,
        firstResultIndex + itemsPerPage
    )

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
                                            filteredRegionalEmbroideries.length
                                        ),
                                        total: filteredRegionalEmbroideries.length,
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
                            {paginatedRegionalEmbroideries.map((item) => (
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

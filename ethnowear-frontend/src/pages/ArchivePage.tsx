import { useEffect, useMemo, useState } from 'react'
import { Alert, Box, Button, Collapse, Grid, Stack, Typography } from '@mui/material'
import ArrowForwardIcon from '@mui/icons-material/ArrowForward'
import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import FilterListIcon from '@mui/icons-material/FilterList'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { searchCatalogue } from '../api/CatalogueApi'
import { getRegionalEmbroideryArchive } from '../api/PublicArchiveApi'
import { conceptPath } from '../app/archiveRoutes'
import type { Language, ReferenceResource } from '../types/reference'
import type { CatalogFacetType, ConceptCatalogResultDetails, OntologyFeatureType } from '../types/catalogue'
import type { RegionalEmbroideryArchiveOverviewDetails } from '../types/archive'
import {
    emptyEmbroideryFilters,
    type EmbroideryFilterOptions,
    type EmbroideryFilters,
    type FilterCombinationMode,
} from '../types/embroideryFilters'
import ArchiveEvidenceCard from '../components/archive/ArchiveEvidenceCard'
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
        .map((value) => ({ iri: value.iri, localName: value.localName, label: value.label })) ?? []
}

function ArchivePage() {
    const { t, i18n } = useTranslation()
    const language: Language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
    const [overview, setOverview] = useState<RegionalEmbroideryArchiveOverviewDetails | null>(null)
    const [catalogue, setCatalogue] = useState<ConceptCatalogResultDetails | null>(null)
    const [filters, setFilters] = useState<EmbroideryFilters>(emptyEmbroideryFilters)
    const [combinationMode, setCombinationMode] = useState<FilterCombinationMode>('and')
    const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        const controller = new AbortController()

        async function loadOverview() {
            try {
                setOverview(await getRegionalEmbroideryArchive(language, 4, controller.signal))
            } catch (err) {
                if (!controller.signal.aborted) {
                    setError(err instanceof Error ? err.message : t('archive.loadError'))
                }
            }
        }

        void loadOverview()
        return () => controller.abort()
    }, [language, t])

    useEffect(() => {
        const controller = new AbortController()

        async function loadCatalogue() {
            try {
                setLoading(true)
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
                }, { page: 0, size: 500, sort: 'label,asc' }, controller.signal)

                setCatalogue(data)
            } catch (err) {
                if (!controller.signal.aborted) {
                    setError(err instanceof Error ? err.message : t('archive.loadError'))
                }
            } finally {
                if (!controller.signal.aborted) setLoading(false)
            }
        }

        void loadCatalogue()
        return () => controller.abort()
    }, [combinationMode, filters, language, t])

    const filterOptions = useMemo<EmbroideryFilterOptions>(() => ({
        regionGroups: facetOptions(catalogue, 'RELATED_CATEGORY', 'REGION'),
        regions: facetOptions(catalogue, 'RELATED_ENTITY', 'REGION'),
        ornamentTypes: facetOptions(catalogue, 'RELATED_CATEGORY', 'ORNAMENT'),
        ornaments: facetOptions(catalogue, 'RELATED_ENTITY', 'ORNAMENT'),
        techniques: facetOptions(catalogue, 'RELATED_ENTITY', 'TECHNIQUE'),
    }), [catalogue])

    const visibleSections = useMemo(() => {
        const matchingLocalNames = new Set(catalogue?.items.map((item) => item.localName) ?? [])
        return overview?.sections.filter((section) => (
            matchingLocalNames.has(section.regionalEmbroidery.localName)
        )) ?? []
    }, [catalogue, overview])

    const selectedFilterCount =
        filters.regionGroupLocalNames.length +
        filters.regionLocalNames.length +
        filters.ornamentTypeLocalNames.length +
        filters.ornamentLocalNames.length +
        filters.techniqueLocalNames.length

    function clearFilters() {
        setFilters(emptyEmbroideryFilters)
    }

    const filterPanel = (
        <EmbroideryFilterPanel
            filters={filters}
            options={filterOptions}
            combinationMode={combinationMode}
            onCombinationModeChange={setCombinationMode}
            onChange={setFilters}
            onClear={clearFilters}
        />
    )

    if (loading && !catalogue) return <EmbroideryPageSkeleton />

    return (
        <Box
            sx={{
                display: 'grid',
                gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: '300px minmax(0, 1fr)' },
                gap: 3,
                px: { xs: 2, md: 5 },
                py: 3,
                alignItems: 'start',
                textAlign: 'left',
            }}
        >
            <Box sx={{ display: { xs: 'none', md: 'block' }, position: 'sticky', top: 140 }}>
                {filterPanel}
            </Box>

            <Box sx={{ display: { xs: 'block', md: 'none' } }}>
                <Button
                    variant="text"
                    color="inherit"
                    aria-expanded={mobileFiltersOpen}
                    fullWidth
                    onClick={() => setMobileFiltersOpen((open) => !open)}
                    sx={{ minHeight: 52, border: 1, borderLeft: 4, borderColor: 'divider', borderLeftColor: 'primary.main', bgcolor: '#EEF1F1' }}
                >
                    <Stack direction="row" sx={{ width: '100%', alignItems: 'center', justifyContent: 'space-between' }}>
                        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                            <FilterListIcon color="primary" fontSize="small" />
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                {mobileFiltersOpen ? t('filters.hide') : t('filters.show')}
                            </Typography>
                        </Stack>
                        {mobileFiltersOpen ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                    </Stack>
                </Button>
                <Collapse in={mobileFiltersOpen} unmountOnExit>
                    <Box sx={{ mt: 1.5 }}>{filterPanel}</Box>
                </Collapse>
            </Box>

            <Stack spacing={4} sx={{ minWidth: 0 }}>
                <Box>
                    <Typography variant="h4" component="h1" sx={{ fontWeight: 800 }}>
                        {t('archive.title')}
                    </Typography>
                    <Typography color="text.secondary">{t('archive.subtitle')}</Typography>
                </Box>

                {error && <Alert severity="error">{error}</Alert>}

                {selectedFilterCount > 0 && (
                    <Alert
                        severity="info"
                        action={<Button color="inherit" size="small" onClick={clearFilters}>{t('filters.clearShort')}</Button>}
                    >
                        {t('archive.selectedFilters', { count: selectedFilterCount })}
                    </Alert>
                )}

                {!error && visibleSections.length === 0 && (
                    <Alert severity="warning">{t('archive.noResults')}</Alert>
                )}

                {visibleSections.map((section) => (
                    <Box component="section" key={section.regionalEmbroidery.localName}>
                        <Stack spacing={2}>
                            <Stack
                                direction={{ xs: 'column', sm: 'row' }}
                                spacing={1.5}
                                sx={{ alignItems: { sm: 'flex-end' }, justifyContent: 'space-between' }}
                            >
                                <Box>
                                    <Typography variant="h5" sx={{ fontWeight: 800 }}>
                                        {section.regionalEmbroidery.label}
                                    </Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        {t('archive.archiveItemCount', { count: section.totalItems })}
                                    </Typography>
                                </Box>
                                <Button
                                    component={Link}
                                    to={conceptPath('REGIONAL_EMBROIDERY', section.regionalEmbroidery.localName)}
                                    endIcon={<ArrowForwardIcon />}
                                    sx={{ alignSelf: { xs: 'flex-start', sm: 'auto' } }}
                                >
                                    {t('archive.openCategory')}
                                </Button>
                            </Stack>

                            {section.previewItems.length > 0 ? (
                                <Grid container spacing={2.5}>
                                    {section.previewItems.map((item) => (
                                        <Grid key={item.archiveItemId} size={{ xs: 12, sm: 6, xl: 3 }}>
                                            <ArchiveEvidenceCard item={item} language={language} />
                                        </Grid>
                                    ))}
                                </Grid>
                            ) : (
                                <Typography variant="body2" color="text.secondary">
                                    {t('archive.noArchiveItems')}
                                </Typography>
                            )}
                        </Stack>
                    </Box>
                ))}
            </Stack>
        </Box>
    )
}

export default ArchivePage

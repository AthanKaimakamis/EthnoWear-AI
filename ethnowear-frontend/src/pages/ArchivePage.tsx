import { useMemo, useState } from 'react'
import { Alert, Box, Button, Grid, LinearProgress, Stack, Typography } from '@mui/material'
import ArrowForwardIcon from '@mui/icons-material/ArrowForward'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { catalogueQueryOptions, regionalEmbroideryArchiveQueryOptions } from '../api/PublicQueryOptions'
import { conceptPath } from '../app/archiveRoutes'
import type { Language } from '../types/reference'
import {
    emptyEmbroideryFilters,
    type EmbroideryFilterOptions,
    type EmbroideryFilters,
    type FilterCombinationMode,
} from '../types/embroideryFilters'
import ArchiveEvidenceCard from '../components/archive/ArchiveEvidenceCard'
import EmbroideryFilterPanel from '../components/embroidery/EmbroideryFilterPanel'
import EmbroideryPageSkeleton from '../components/loading/EmbroideryPageSkeleton'
import ArchiveBrowseLayout from '../components/archive/browse/ArchiveBrowseLayout'
import { catalogueFacetOptions } from '../components/archive/browse/catalogueFacets'

function ArchivePage() {
    const { t, i18n } = useTranslation()
    const language: Language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
    const [filters, setFilters] = useState<EmbroideryFilters>(emptyEmbroideryFilters)
    const [combinationMode, setCombinationMode] = useState<FilterCombinationMode>('and')
    const overviewQuery = useQuery(regionalEmbroideryArchiveQueryOptions(language, 4))
    const catalogueOptions = catalogueQueryOptions({
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
    }, { page: 0, size: 500, sort: 'label,asc' })
    const catalogueQuery = useQuery({
        ...catalogueOptions,
        placeholderData: (previousData, previousQuery) => {
            const previousScope = previousQuery?.queryKey[2] as { entityType?: string, language?: string } | undefined
            return previousScope?.entityType === 'REGIONAL_EMBROIDERY' && previousScope.language === language
                ? previousData
                : undefined
        },
    })
    const overview = overviewQuery.data
    const catalogue = catalogueQuery.data

    const filterOptions = useMemo<EmbroideryFilterOptions>(() => ({
        regionGroups: catalogueFacetOptions(catalogue ?? null, 'RELATED_CATEGORY', 'REGION'),
        regions: catalogueFacetOptions(catalogue ?? null, 'RELATED_ENTITY', 'REGION'),
        ornamentTypes: catalogueFacetOptions(catalogue ?? null, 'RELATED_CATEGORY', 'ORNAMENT'),
        ornaments: catalogueFacetOptions(catalogue ?? null, 'RELATED_ENTITY', 'ORNAMENT'),
        techniques: catalogueFacetOptions(catalogue ?? null, 'RELATED_ENTITY', 'TECHNIQUE'),
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

    const caughtError = catalogueQuery.error ?? overviewQuery.error
    const error = caughtError instanceof Error ? caughtError.message : caughtError ? t('archive.loadError') : null
    const initialLoading = catalogueQuery.isPending || overviewQuery.isPending

    if (initialLoading) return <EmbroideryPageSkeleton />

    return (
        <ArchiveBrowseLayout filters={filterPanel}>
            <Stack spacing={4} sx={{ minWidth: 0 }}>
                <Box>
                    <Typography variant="h4" component="h1" sx={{ fontWeight: 800 }}>
                        {t('archive.title')}
                    </Typography>
                    <Typography color="text.secondary">{t('archive.subtitle')}</Typography>
                </Box>

                {(catalogueQuery.isFetching || overviewQuery.isFetching) && <LinearProgress aria-label={t('archive.loading')} />}

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
        </ArchiveBrowseLayout>
    )
}

export default ArchivePage

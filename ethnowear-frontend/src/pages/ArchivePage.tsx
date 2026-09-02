import { useMemo } from 'react'
import { Alert, Box, Button, Grid, LinearProgress, Stack, Typography } from '@mui/material'
import ArrowForwardIcon from '@mui/icons-material/ArrowForward'
import { Link, useSearchParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { catalogueQueryOptions, regionalEmbroideryArchiveQueryOptions } from '../api/PublicQueryOptions'
import { conceptPath } from '../app/archiveRoutes'
import type { Language } from '../types/reference'
import {
    type EmbroideryFilterOptions,
    type EmbroideryFilters,
    type FilterCombinationMode,
} from '../types/embroideryFilters'
import ArchiveEvidenceCard from '../components/archive/ArchiveEvidenceCard'
import EmbroideryFilterPanel from '../components/embroidery/EmbroideryFilterPanel'
import EmbroideryPageSkeleton from '../components/loading/EmbroideryPageSkeleton'
import ArchiveBrowseLayout from '../components/archive/browse/ArchiveBrowseLayout'
import { catalogueFacetOptions } from '../components/archive/browse/catalogueFacets'
import { archiveFilterParams, readListParam, replaceListParam } from '../app/archiveFilterActions'

const embroideryFilterParams = {
    regionGroupLocalNames: 'regionGroups',
    regionLocalNames: archiveFilterParams.regions,
    ornamentTypeLocalNames: 'ornamentTypes',
    ornamentLocalNames: 'ornaments',
    techniqueLocalNames: 'techniques',
} satisfies Record<keyof EmbroideryFilters, string>

function ArchivePage() {
    const { t, i18n } = useTranslation()
    const language: Language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
    const [searchParams, setSearchParams] = useSearchParams()
    const filters: EmbroideryFilters = {
        regionGroupLocalNames: readListParam(searchParams, embroideryFilterParams.regionGroupLocalNames),
        regionLocalNames: readListParam(searchParams, embroideryFilterParams.regionLocalNames),
        ornamentTypeLocalNames: readListParam(searchParams, embroideryFilterParams.ornamentTypeLocalNames),
        ornamentLocalNames: readListParam(searchParams, embroideryFilterParams.ornamentLocalNames),
        techniqueLocalNames: readListParam(searchParams, embroideryFilterParams.techniqueLocalNames),
    }
    const selectedEntities = readListParam(searchParams, archiveFilterParams.entities)
    const combinationMode: FilterCombinationMode = searchParams.get('mode') === 'or' ? 'or' : 'and'
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
        const requestedEntities = new Set(selectedEntities)
        return overview?.sections.filter((section) => (
            matchingLocalNames.has(section.regionalEmbroidery.localName)
            && (requestedEntities.size === 0 || requestedEntities.has(section.regionalEmbroidery.localName))
        )) ?? []
    }, [catalogue, overview, selectedEntities])

    const selectedFilterCount =
        filters.regionGroupLocalNames.length +
        filters.regionLocalNames.length +
        filters.ornamentTypeLocalNames.length +
        filters.ornamentLocalNames.length +
        filters.techniqueLocalNames.length +
        selectedEntities.length

    function updateFilters(nextFilters: EmbroideryFilters) {
        setSearchParams(current => {
            const next = new URLSearchParams(current)
            for (const [field, key] of Object.entries(embroideryFilterParams)) {
                replaceListParam(next, key, nextFilters[field as keyof EmbroideryFilters])
            }
            return next
        })
    }

    function updateCombinationMode(mode: FilterCombinationMode) {
        setSearchParams(current => {
            const next = new URLSearchParams(current)
            if (mode === 'or') next.set('mode', mode)
            else next.delete('mode')
            return next
        })
    }

    function clearFilters() {
        setSearchParams(current => {
            const next = new URLSearchParams(current)
            Object.values(embroideryFilterParams).forEach(key => next.delete(key))
            next.delete(archiveFilterParams.entities)
            next.delete('mode')
            return next
        })
    }

    const filterPanel = (
        <EmbroideryFilterPanel
            filters={filters}
            options={filterOptions}
            combinationMode={combinationMode}
            onCombinationModeChange={updateCombinationMode}
            onChange={updateFilters}
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

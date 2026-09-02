import { useMemo } from 'react'
import { Alert, Box, Button, Grid, LinearProgress, Stack, Typography } from '@mui/material'
import ArrowForwardIcon from '@mui/icons-material/ArrowForward'
import { Link, useSearchParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { catalogueQueryOptions, referenceDataQueryOptions, regionalMotifArchiveQueryOptions } from '../api/PublicQueryOptions'
import { conceptPath } from '../app/archiveRoutes'
import ArchiveEvidenceCard from '../components/archive/ArchiveEvidenceCard'
import EmbroideryPageSkeleton from '../components/loading/EmbroideryPageSkeleton'
import type { Language } from '../types/reference'
import { archiveFilterParams, readListParam, replaceListParam } from '../app/archiveFilterActions'
import ArchiveBrowseLayout from '../components/archive/browse/ArchiveBrowseLayout'
import EmbroideryFilterPanel from '../components/embroidery/EmbroideryFilterPanel'
import { catalogueFacetOptions } from '../components/archive/browse/catalogueFacets'
import type { EmbroideryFilterOptions, EmbroideryFilters, FilterCombinationMode } from '../types/embroideryFilters'

const motifFilterParams = {
    regionGroupLocalNames: 'regionGroups',
    regionLocalNames: archiveFilterParams.regions,
    ornamentTypeLocalNames: 'ornamentTypes',
    ornamentLocalNames: 'ornaments',
    techniqueLocalNames: 'techniques',
} satisfies Record<keyof EmbroideryFilters, string>

export default function RegionalMotifArchivePage() {
    const { t, i18n } = useTranslation()
    const language: Language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
    const [searchParams, setSearchParams] = useSearchParams()
    const selectedEntities = readListParam(searchParams, archiveFilterParams.entities)
    const filters: EmbroideryFilters = {
        regionGroupLocalNames: readListParam(searchParams, motifFilterParams.regionGroupLocalNames),
        regionLocalNames: readListParam(searchParams, motifFilterParams.regionLocalNames),
        ornamentTypeLocalNames: readListParam(searchParams, motifFilterParams.ornamentTypeLocalNames),
        ornamentLocalNames: readListParam(searchParams, motifFilterParams.ornamentLocalNames),
        techniqueLocalNames: readListParam(searchParams, motifFilterParams.techniqueLocalNames),
    }
    const combinationMode: FilterCombinationMode = searchParams.get('mode') === 'or' ? 'or' : 'and'
    const overviewQuery = useQuery(regionalMotifArchiveQueryOptions(language, 4))
    const referenceQuery = useQuery(referenceDataQueryOptions(language))
    const catalogueQuery = useQuery(catalogueQueryOptions({
        entityType: 'REGIONAL_MOTIF',
        language,
        relatedEntityLocalNames: { REGION: filters.regionLocalNames, ORNAMENT: filters.ornamentLocalNames, TECHNIQUE: filters.techniqueLocalNames },
        relatedCategoryLocalNames: { REGION: filters.regionGroupLocalNames, ORNAMENT: filters.ornamentTypeLocalNames },
        combinationMode: combinationMode.toUpperCase() as 'AND' | 'OR',
    }, { page: 0, size: 500, sort: 'label,asc' }))
    const selectedFilterCount = Object.values(filters).reduce((count, values) => count + values.length, 0) + selectedEntities.length
    const filterOptions = useMemo<EmbroideryFilterOptions>(() => {
        const facetOrReference = (kind: 'RELATED_CATEGORY' | 'RELATED_ENTITY', type: 'REGION' | 'ORNAMENT' | 'TECHNIQUE', fallback: EmbroideryFilterOptions[keyof EmbroideryFilterOptions]) => {
            const facets = catalogueFacetOptions(catalogueQuery.data ?? null, kind, type)
            return facets.length > 0 ? facets : fallback
        }
        return {
            regionGroups: facetOrReference('RELATED_CATEGORY', 'REGION', referenceQuery.data?.regionGroups ?? []),
            regions: facetOrReference('RELATED_ENTITY', 'REGION', referenceQuery.data?.regions ?? []),
            ornamentTypes: facetOrReference('RELATED_CATEGORY', 'ORNAMENT', referenceQuery.data?.ornamentTypes ?? []),
            ornaments: facetOrReference('RELATED_ENTITY', 'ORNAMENT', referenceQuery.data?.ornaments ?? []),
            techniques: facetOrReference('RELATED_ENTITY', 'TECHNIQUE', referenceQuery.data?.techniques ?? []),
        }
    }, [catalogueQuery.data, referenceQuery.data])
    const sections = useMemo(() => {
        const overviewByLocalName = new Map(
            overviewQuery.data?.sections.map(section => [section.regionalMotif.localName, section]) ?? [],
        )
        const requested = new Set(selectedEntities)
        const catalogueMatches = new Set(catalogueQuery.data?.items.map(item => item.localName) ?? [])
        const selectedRegions = new Set(filters.regionLocalNames)
        const selectedRegionGroups = new Set(filters.regionGroupLocalNames)
        const regionsFromGroups = new Set(Object.entries(referenceQuery.data?.regionsByRegionGroup ?? {})
            .filter(([group]) => selectedRegionGroups.has(group))
            .flatMap(([, regions]) => regions))
        const hasRelationFilters = filters.ornamentTypeLocalNames.length > 0 || filters.ornamentLocalNames.length > 0 || filters.techniqueLocalNames.length > 0
        return (referenceQuery.data?.regionalMotifTypes ?? [])
            .filter(item => {
                if (requested.size > 0 && !requested.has(item.localName)) return false
                const region = referenceQuery.data?.regionByRegionalMotif[item.localName]
                if (selectedRegions.size > 0 && (!region || !selectedRegions.has(region))) return false
                if (selectedRegionGroups.size > 0 && (!region || !regionsFromGroups.has(region))) return false
                return !hasRelationFilters || catalogueMatches.has(item.localName)
            })
            .map(item => overviewByLocalName.get(item.localName) ?? {
                regionalMotif: {
                    entityType: 'REGIONAL_MOTIF' as const,
                    iri: item.iri,
                    localName: item.localName,
                    label: item.label,
                    comment: item.comment ?? null,
                    categories: [],
                    evidenceCount: item.evidenceCount ?? 0,
                    representativeMediaAssetId: item.representativeMediaAssetId ?? null,
                },
                totalItems: 0,
                previewItems: [],
            })
    }, [catalogueQuery.data, filters, overviewQuery.data, referenceQuery.data, selectedEntities])

    function updateFilters(nextFilters: EmbroideryFilters) {
        setSearchParams(current => {
            const next = new URLSearchParams(current)
            for (const [field, key] of Object.entries(motifFilterParams)) replaceListParam(next, key, nextFilters[field as keyof EmbroideryFilters])
            return next
        })
    }

    function updateCombinationMode(mode: FilterCombinationMode) {
        setSearchParams(current => {
            const next = new URLSearchParams(current)
            if (mode === 'or') next.set('mode', 'or')
            else next.delete('mode')
            return next
        })
    }

    function clearFilters() {
        setSearchParams(current => {
            const next = new URLSearchParams(current)
            Object.values(motifFilterParams).forEach(key => next.delete(key))
            next.delete(archiveFilterParams.entities)
            next.delete('mode')
            return next
        })
    }

    if (overviewQuery.isPending || referenceQuery.isPending || catalogueQuery.isPending) return <EmbroideryPageSkeleton />

    const filterPanel = <EmbroideryFilterPanel filters={filters} options={filterOptions} combinationMode={combinationMode}
        onCombinationModeChange={updateCombinationMode} onChange={updateFilters} onClear={clearFilters} />

    return <ArchiveBrowseLayout filters={filterPanel}>
        <Stack spacing={4}>
            <Box>
                <Typography variant="h4" component="h1" sx={{ fontWeight: 800 }}>{t('regionalMotifArchive.title')}</Typography>
                <Typography color="text.secondary">{t('regionalMotifArchive.subtitle')}</Typography>
            </Box>
            {(overviewQuery.isFetching || referenceQuery.isFetching || catalogueQuery.isFetching) && <LinearProgress aria-label={t('archive.loading')} />}
            {(overviewQuery.isError || referenceQuery.isError || catalogueQuery.isError) && <Alert severity="error">{t('archive.loadError')}</Alert>}
            {selectedFilterCount > 0 && <Alert severity="info" action={<Button color="inherit" size="small" onClick={clearFilters}>{t('filters.clearShort')}</Button>}>
                {t('archive.selectedFilters', { count: selectedFilterCount })}
            </Alert>}
            {!overviewQuery.isError && !referenceQuery.isError && !catalogueQuery.isError && sections.length === 0 && <Alert severity="info">{t('regionalMotifArchive.empty')}</Alert>}
            {sections.map(section => <Box component="section" key={section.regionalMotif.localName}>
                <Stack spacing={2}>
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ justifyContent: 'space-between', alignItems: { sm: 'flex-end' } }}>
                        <Box>
                            <Typography variant="h5" sx={{ fontWeight: 800 }}>{section.regionalMotif.label}</Typography>
                            {section.regionalMotif.comment && <Typography color="text.secondary">{section.regionalMotif.comment}</Typography>}
                            <Typography variant="body2" color="text.secondary">{t('archive.archiveItemCount', { count: section.totalItems })}</Typography>
                        </Box>
                        <Button component={Link} to={conceptPath('REGIONAL_MOTIF', section.regionalMotif.localName)} endIcon={<ArrowForwardIcon />}>
                            {t('archive.openCategory')}
                        </Button>
                    </Stack>
                    {section.previewItems.length > 0 ? <Grid container spacing={2.5}>
                        {section.previewItems.map(item => <Grid key={item.archiveItemId} size={{ xs: 12, sm: 6, xl: 3 }}>
                            <ArchiveEvidenceCard item={item} language={language} />
                        </Grid>)}
                    </Grid> : <Typography variant="body2" color="text.secondary">{t('archive.noArchiveItems')}</Typography>}
                </Stack>
            </Box>)}
        </Stack>
    </ArchiveBrowseLayout>
}

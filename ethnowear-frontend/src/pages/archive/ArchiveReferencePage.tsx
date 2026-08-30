import { useMemo, useState } from 'react'
import { Alert, Box, InputAdornment, LinearProgress, Stack, TextField, Typography } from '@mui/material'
import SearchIcon from '@mui/icons-material/Search'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { catalogueQueryOptions } from '../../api/PublicQueryOptions'
import type { Language } from '../../types/reference'
import type { OntologyFeatureType } from '../../types/catalogue'
import ArchiveReferencePageSkeleton from '../../components/loading/ArchiveReferencePageSkeleton'
import ArchiveBrowseLayout from '../../components/archive/browse/ArchiveBrowseLayout'
import CatalogueCategorySection from '../../components/archive/browse/CatalogueCategorySection'
import { buildCatalogueCategories } from '../../components/archive/browse/catalogueCategories'
import CatalogueFilterPanel from '../../components/archive/browse/CatalogueFilterPanel'
import { catalogueFacetOptions } from '../../components/archive/browse/catalogueFacets'
import useDebouncedValue from '../../hooks/useDebouncedValue'

type ArchiveReferenceKind = 'motifs' | 'techniques' | 'ornaments'

type Props = {
    kind: ArchiveReferenceKind
}

const rootCategoryByType: Partial<Record<OntologyFeatureType, string>> = {
    TECHNIQUE: 'Technique',
    ORNAMENT: 'Ornament',
    MOTIF: 'Motif',
}

function entityTypeForKind(kind: ArchiveReferenceKind): OntologyFeatureType {
    if (kind === 'techniques') return 'TECHNIQUE'
    if (kind === 'ornaments') return 'ORNAMENT'
    return 'MOTIF'
}

function toggleSelection(current: string[], value: string) {
    return current.includes(value) ? current.filter(item => item !== value) : [...current, value]
}

export default function ArchiveReferencePage({ kind }: Props) {
    const { t, i18n } = useTranslation()
    const language: Language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
    const entityType = entityTypeForKind(kind)
    const rootCategory = rootCategoryByType[entityType]
    const [selectedRegions, setSelectedRegions] = useState<string[]>([])
    const [selectedCategories, setSelectedCategories] = useState<string[]>([])
    const [searchText, setSearchText] = useState('')
    const debouncedSearchText = useDebouncedValue(searchText, 300)
    const catalogueOptions = catalogueQueryOptions({
        entityType,
        language,
        searchText: debouncedSearchText,
        categoryLocalNames: selectedCategories,
        relatedEntityLocalNames: { REGION: selectedRegions },
        combinationMode: 'AND',
    }, { page: 0, size: 200, sort: 'label,asc' })
    const catalogueQuery = useQuery({
        ...catalogueOptions,
        placeholderData: (previousData, previousQuery) => {
            const previousScope = previousQuery?.queryKey[2] as { entityType?: string, language?: string } | undefined
            return previousScope?.entityType === entityType && previousScope.language === language
                ? previousData
                : undefined
        },
    })
    const catalogue = catalogueQuery.data
    const error = catalogueQuery.error instanceof Error
        ? catalogueQuery.error.message
        : catalogueQuery.error ? t('archive.loadError') : null

    const availableRegions = useMemo(
        () => catalogueFacetOptions(catalogue ?? null, 'RELATED_ENTITY', 'REGION'),
        [catalogue],
    )
    const availableCategories = useMemo(
        () => catalogueFacetOptions(catalogue ?? null, 'CATEGORY', entityType)
            .filter(category => category.localName !== rootCategory),
        [catalogue, entityType, rootCategory],
    )
    const categorySections = useMemo(
        () => buildCatalogueCategories(catalogue?.items ?? [], t('archiveReference.uncategorized'), rootCategory ? [rootCategory] : []),
        [catalogue, rootCategory, t],
    )

    function clearFilters() {
        setSelectedRegions([])
        setSelectedCategories([])
        setSearchText('')
    }

    if (catalogueQuery.isPending) return <ArchiveReferencePageSkeleton />

    const filterPanel = (
        <CatalogueFilterPanel
            groups={[
                {
                    key: 'regions',
                    title: t('filters.regions'),
                    items: availableRegions,
                    selectedValues: selectedRegions,
                    onToggle: value => setSelectedRegions(current => toggleSelection(current, value)),
                },
                {
                    key: 'categories',
                    title: t('filters.categories'),
                    items: availableCategories,
                    selectedValues: selectedCategories,
                    onToggle: value => setSelectedCategories(current => toggleSelection(current, value)),
                },
            ]}
            onClear={clearFilters}
        />
    )

    return (
        <ArchiveBrowseLayout filters={filterPanel}>
            <Stack spacing={3}>
                <Stack spacing={2}>
                    <Box>
                        <Typography variant="h4" sx={{ fontWeight: 800 }}>{t(`archiveReference.${kind}.title`)}</Typography>
                        <Typography color="text.secondary">{t(`archiveReference.${kind}.subtitle`)}</Typography>
                    </Box>
                    <TextField
                        value={searchText}
                        onChange={event => setSearchText(event.target.value)}
                        placeholder={t('archiveReference.search')}
                        aria-label={t('archiveReference.search')}
                        fullWidth
                        slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
                    />
                    {catalogueQuery.isFetching && <LinearProgress aria-label={t('archiveReference.loading')} />}
                </Stack>
                {error && <Alert severity="error">{error}</Alert>}
                {!error && categorySections.length === 0 && <Alert severity="warning">{t('archiveReference.noResults')}</Alert>}
                {!error && (
                    <Stack spacing={5}>
                        {categorySections.map(section => (
                            <CatalogueCategorySection key={section.category.localName} section={section} entityType={entityType} />
                        ))}
                    </Stack>
                )}
            </Stack>
        </ArchiveBrowseLayout>
    )
}

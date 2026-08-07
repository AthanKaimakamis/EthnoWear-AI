import { useEffect, useMemo, useState } from 'react'
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Alert,
    Box,
    Button,
    Card,
    CardContent,
    CardMedia,
    Chip,
    Collapse,
    Divider,
    InputAdornment,
    Paper,
    Stack,
    TextField,
    Typography,
} from '@mui/material'
import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import FilterListIcon from '@mui/icons-material/FilterList'
import SearchIcon from '@mui/icons-material/Search'
import { useTranslation } from 'react-i18next'
import { getFullReference } from '../../api/ReferenceApi.ts'
import type { Language, ReferenceData, ReferenceResource } from '../../types/reference.ts'
import ArchiveReferencePageSkeleton from '../../components/loading/ArchiveReferencePageSkeleton.tsx'
import SearchableFilterList from '../../components/filtres/SearchableFilterList.tsx'

type ArchiveReferenceKind = 'motifs' | 'techniques' | 'ornaments'

type Props = {
    kind: ArchiveReferenceKind
}

type CategorySection = {
    category: ReferenceResource
    items: ReferenceResource[]
}

type ArchiveReferenceFiltersProps = {
    regions: ReferenceResource[]
    categories: ReferenceResource[]
    selectedRegions: string[]
    selectedCategories: string[]
    onToggleRegion: (localName: string) => void
    onToggleCategory: (localName: string) => void
    onClear: () => void
}

const imageNotFoundUrl = '/Image-not-found.png'

function normalizeSearch(value: string) {
    return value.trim().toLocaleLowerCase()
}

function searchableValues(item: ReferenceResource) {
    return [
        item.localName,
        item.label,
        item.comment,
        item.labels?.bg,
        item.labels?.en,
        item.comments?.bg,
        item.comments?.en,
        ...(item.altLabels ?? []),
        ...(item.altLabelsByLanguage?.bg ?? []),
        ...(item.altLabelsByLanguage?.en ?? []),
    ].filter((value): value is string => Boolean(value))
}

function matchesSearch(item: ReferenceResource, query: string) {
    const normalizedQuery = normalizeSearch(query)

    if (!normalizedQuery) {
        return true
    }

    return searchableValues(item).some((value) =>
        normalizeSearch(value).includes(normalizedQuery)
    )
}

function itemRegions(
    itemLocalName: string,
    kind: ArchiveReferenceKind,
    reference: ReferenceData
) {
    const relationMap = kind === 'techniques'
        ? reference.techniquesByRegion ?? {}
        : reference.ornamentsByRegion

    return Object.entries(relationMap)
        .filter(([, itemLocalNames]) => itemLocalNames.includes(itemLocalName))
        .map(([regionLocalName]) => regionLocalName)
}

function relationMapForKind(kind: ArchiveReferenceKind, reference: ReferenceData) {
    if (kind === 'techniques') {
        return reference.techniquesByType ?? {}
    }

    if (kind === 'ornaments') {
        return reference.ornamentsByType ?? {}
    }

    return {}
}

function categoriesForKind(kind: ArchiveReferenceKind, reference: ReferenceData) {
    if (kind === 'techniques') {
        return reference.techniqueTypes ?? []
    }

    if (kind === 'ornaments') {
        return reference.ornamentTypes ?? []
    }

    return []
}

function sourceItemsForKind(kind: ArchiveReferenceKind, reference: ReferenceData) {
    if (kind === 'techniques') {
        return reference.techniques
    }

    if (kind === 'ornaments') {
        return reference.ornaments
    }

    return reference.motifs
}

function buildCategorySections(
    kind: ArchiveReferenceKind,
    reference: ReferenceData,
    items: ReferenceResource[],
    searchText: string,
    uncategorizedLabel: string
) {
    const categories = categoriesForKind(kind, reference)
    const relationMap = relationMapForKind(kind, reference)
    const itemsByLocalName = new Map(items.map((item) => [item.localName, item]))

    const sections: CategorySection[] = categories.map((category) => {
        const categoryItemLocalNames = relationMap[category.localName] ?? []
        const categoryMatchesSearch = matchesSearch(category, searchText)
        const categoryItems = categoryItemLocalNames
            .map((localName) => itemsByLocalName.get(localName))
            .filter((item): item is ReferenceResource => Boolean(item))
            .filter((item) => categoryMatchesSearch || matchesSearch(item, searchText))

        return {
            category,
            items: categoryItems,
        }
    })

    const categorizedItemLocalNames = new Set(
        Object.values(relationMap).flatMap((localNames) => localNames)
    )

    const uncategorizedItems = items.filter((item) =>
        !categorizedItemLocalNames.has(item.localName) && matchesSearch(item, searchText)
    )

    if (uncategorizedItems.length > 0) {
        sections.push({
            category: {
                iri: '#Uncategorized',
                localName: 'Uncategorized',
                label: uncategorizedLabel,
            },
            items: uncategorizedItems,
        })
    }

    return sections.filter((section) => section.items.length > 0)
}

function ArchiveConceptCard({ item }: { item: ReferenceResource }) {
    return (
        <Card
            sx={{
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                borderRadius: 1,
                overflow: 'hidden',
                border: 1,
                borderColor: 'divider',
            }}
        >
            <CardMedia
                component="img"
                height="150"
                image={item.imageUrl || imageNotFoundUrl}
                alt={item.label || item.localName}
                sx={{
                    bgcolor: '#eef0f2',
                    objectFit: 'cover',
                    borderBottom: 1,
                    borderColor: 'divider',
                }}
            />

            <CardContent sx={{ flexGrow: 1 }}>
                <Stack spacing={1}>
                    <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.2 }}>
                        {item.label || item.localName}
                    </Typography>

                    <Typography variant="body2" color="text.secondary">
                        {item.comment || item.localName}
                    </Typography>

                    <Stack direction="row" spacing={0.75} useFlexGap sx={{ flexWrap: 'wrap' }}>
                        <Chip
                            size="small"
                            variant="outlined"
                            label={item.localName}
                            sx={{ maxWidth: '100%' }}
                        />
                    </Stack>
                </Stack>
            </CardContent>
        </Card>
    )
}

function ArchiveCategorySection({ section }: { section: CategorySection }) {
    const { t } = useTranslation()

    return (
        <Box component="section">
            <Stack spacing={1.5}>
                <Box>
                    <Typography variant="h5" sx={{ fontWeight: 800 }}>
                        {section.category.label || section.category.localName}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        {t('archiveReference.categoryCount', { count: section.items.length })}
                    </Typography>
                </Box>

                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: {
                            xs: '1fr',
                            sm: 'repeat(2, minmax(0, 1fr))',
                            lg: 'repeat(3, minmax(0, 1fr))',
                        },
                        gap: 3,
                    }}
                >
                    {section.items.map((item) => (
                        <ArchiveConceptCard key={item.localName} item={item} />
                    ))}
                </Box>
            </Stack>
        </Box>
    )
}

function ArchiveReferenceFilters(props: ArchiveReferenceFiltersProps) {
    const { t } = useTranslation()

    function filterAccordion(
        title: string,
        items: ReferenceResource[],
        selected: string[],
        onToggle: (localName: string) => void,
    ) {
        if (items.length === 0) {
            return null
        }

        return (
            <Accordion defaultExpanded disableGutters elevation={0} sx={{ bgcolor: 'transparent', '&::before': { display: 'none' } }}>
                <AccordionSummary expandIcon={<ExpandMoreIcon />} sx={{ px: 0.5 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>{title}</Typography>
                </AccordionSummary>
                <AccordionDetails sx={{ px: 0.5, pt: 0 }}>
                    <SearchableFilterList
                        title={title}
                        items={items}
                        selectedValues={selected}
                        onToggle={onToggle}
                    />
                </AccordionDetails>
            </Accordion>
        )
    }

    return (
        <Paper elevation={1} sx={{ p: 2, borderRadius: 1, border: 1, borderColor: 'divider', bgcolor: '#EEF1F1' }}>
            <Stack spacing={1.5}>
                <Box>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>{t('filters.title')}</Typography>
                    <Typography variant="caption" color="text.secondary">{t('filters.availableOnly')}</Typography>
                </Box>
                <Divider />
                {filterAccordion(t('filters.regions'), props.regions, props.selectedRegions, props.onToggleRegion)}
                {filterAccordion(t('filters.categories'), props.categories, props.selectedCategories, props.onToggleCategory)}
                <Button variant="outlined" disabled={props.selectedRegions.length + props.selectedCategories.length === 0} onClick={props.onClear}>
                    {t('filters.clear')}
                </Button>
            </Stack>
        </Paper>
    )
}

function ArchiveReferencePage({ kind }: Props) {
    const { t, i18n } = useTranslation()
    const language: Language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
    const [reference, setReference] = useState<ReferenceData | null>(null)
    const [selectedRegions, setSelectedRegions] = useState<string[]>([])
    const [selectedCategories, setSelectedCategories] = useState<string[]>([])
    const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false)
    const [searchText, setSearchText] = useState('')
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
                    setError(err instanceof Error ? err.message : t('archive.loadError'))
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

    const sourceItems = useMemo(() => reference ? sourceItemsForKind(kind, reference) : [], [kind, reference])
    const categoryRelationMap = useMemo(() => reference ? relationMapForKind(kind, reference) : {}, [kind, reference])

    const items: ReferenceResource[] = useMemo(() => {
        if (!reference) {
            return []
        }

        return sourceItems.filter((item) => {
            const regions = itemRegions(item.localName, kind, reference)
            const matchesRegion = selectedRegions.length === 0 || selectedRegions.some(
                (regionLocalName) => regions.includes(regionLocalName)
            )
            const matchesCategory = selectedCategories.length === 0 || selectedCategories.some(
                (categoryLocalName) => categoryRelationMap[categoryLocalName]?.includes(item.localName)
            )
            return matchesRegion && matchesCategory
        })
    }, [categoryRelationMap, kind, reference, selectedCategories, selectedRegions, sourceItems])

    const availableRegions = useMemo(() => {
        if (!reference || kind === 'motifs') return []
        return reference.regions.filter(region => selectedRegions.includes(region.localName) || sourceItems.some(item => {
            const matchesCategory = selectedCategories.length === 0 || selectedCategories.some(
                category => categoryRelationMap[category]?.includes(item.localName)
            )
            return matchesCategory && itemRegions(item.localName, kind, reference).includes(region.localName)
        }))
    }, [categoryRelationMap, kind, reference, selectedCategories, selectedRegions, sourceItems])

    const availableCategories = useMemo(() => {
        if (!reference) return []
        return categoriesForKind(kind, reference).filter(category => selectedCategories.includes(category.localName)
            || (categoryRelationMap[category.localName] ?? []).some(itemLocalName => {
                if (selectedRegions.length === 0) return true
                return selectedRegions.some(region => itemRegions(itemLocalName, kind, reference).includes(region))
            }))
    }, [categoryRelationMap, kind, reference, selectedCategories, selectedRegions])

    const categorySections = useMemo(() => {
        if (!reference) {
            return []
        }

        return buildCategorySections(
            kind,
            reference,
            items,
            searchText,
            t('archiveReference.uncategorized')
        )
    }, [items, kind, reference, searchText, t])

    function toggleRegion(regionLocalName: string) {
        setSelectedRegions((current) => current.includes(regionLocalName)
            ? current.filter((item) => item !== regionLocalName)
            : [...current, regionLocalName]
        )
    }

    function toggleCategory(categoryLocalName: string) {
        setSelectedCategories(current => current.includes(categoryLocalName)
            ? current.filter(item => item !== categoryLocalName)
            : [...current, categoryLocalName]
        )
    }

    function clearFilters() {
        setSelectedRegions([])
        setSelectedCategories([])
        setSearchText('')
    }

    if (loading) {
        return <ArchiveReferencePageSkeleton />
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
            <Box sx={{ display: { xs: 'none', md: 'block' }, position: 'sticky', top: 140 }}>
                <ArchiveReferenceFilters
                    regions={availableRegions}
                    categories={availableCategories}
                    selectedRegions={selectedRegions}
                    selectedCategories={selectedCategories}
                    onToggleRegion={toggleRegion}
                    onToggleCategory={toggleCategory}
                    onClear={clearFilters}
                />
            </Box>

            <Box sx={{ display: { xs: 'block', md: 'none' } }}>
                <Button
                    variant="text"
                    color="inherit"
                    fullWidth
                    aria-expanded={mobileFiltersOpen}
                    onClick={() => setMobileFiltersOpen(open => !open)}
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
                    <Box sx={{ mt: 1.5 }}>
                        <ArchiveReferenceFilters
                            regions={availableRegions}
                            categories={availableCategories}
                            selectedRegions={selectedRegions}
                            selectedCategories={selectedCategories}
                            onToggleRegion={toggleRegion}
                            onToggleCategory={toggleCategory}
                            onClear={clearFilters}
                        />
                    </Box>
                </Collapse>
            </Box>

            <Stack spacing={3} sx={{ minWidth: 0 }}>
                <Stack spacing={2}>
                    <Box>
                        <Typography variant="h4" sx={{ fontWeight: 800 }}>
                            {t(`archiveReference.${kind}.title`)}
                        </Typography>
                        <Typography color="text.secondary">
                            {t(`archiveReference.${kind}.subtitle`)}
                        </Typography>
                    </Box>

                    <TextField
                        value={searchText}
                        onChange={(event) => setSearchText(event.target.value)}
                        placeholder={t('archiveReference.search')}
                        aria-label={t('archiveReference.search')}
                        fullWidth
                        slotProps={{
                            input: {
                                startAdornment: (
                                    <InputAdornment position="start">
                                        <SearchIcon fontSize="small" />
                                    </InputAdornment>
                                ),
                            },
                        }}
                    />
                </Stack>

                {error && <Alert severity="error">{error}</Alert>}

                {kind === 'motifs' && (
                    <Alert severity="info">
                        {t('archiveReference.motifs.empty')}
                    </Alert>
                )}

                {!loading && !error && categorySections.length === 0 && (
                    <Alert severity="warning">
                        {t('archiveReference.noResults')}
                    </Alert>
                )}

                {!loading && !error && (
                    <Stack spacing={5}>
                        {categorySections.map((section) => (
                            <ArchiveCategorySection
                                key={section.category.localName}
                                section={section}
                            />
                        ))}
                    </Stack>
                )}
            </Stack>
        </Box>
    )
}

export default ArchiveReferencePage

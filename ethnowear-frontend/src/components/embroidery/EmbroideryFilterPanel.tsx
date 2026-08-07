import {
    Box,
    Button,
    Divider,
    Paper,
    Stack,
    ToggleButton,
    ToggleButtonGroup,
    Typography
} from "@mui/material"
import type {
    DisabledEmbroideryFilters,
    EmbroideryFilterOptions,
    EmbroideryFilters,
    FilterCombinationMode,
} from '../../types/embroideryFilters.ts'
import FilterAccordion from "../filtres/FilterAccordion.tsx";
import { useTranslation } from 'react-i18next'

type FilterKey = keyof EmbroideryFilters

type EmbroideryFilterPanelProps = {
    filters: EmbroideryFilters
    options: EmbroideryFilterOptions
    disabledFilters?: DisabledEmbroideryFilters
    combinationMode: FilterCombinationMode
    onChange: (filters: EmbroideryFilters) => void
    onCombinationModeChange: (mode: FilterCombinationMode) => void
    onClear: () => void
}

function EmbroideryFilterPanel({
                                   filters,
                                   options,
                                   disabledFilters,
                                   combinationMode,
                                   onChange,
                                   onCombinationModeChange,
                                   onClear,
                               }: EmbroideryFilterPanelProps) {
    const { t } = useTranslation()

    function toggleValue(key: FilterKey, value: string) {
        const currentValues = filters[key]

        const nextValues = currentValues.includes(value)
            ? currentValues.filter((item) => item !== value)
            : [...currentValues, value]

        onChange({
            ...filters,
            [key]: nextValues,
        })
    }

    return (
        <Paper
            elevation={1}
            sx={{
                p: 2,
                width: '100%',
                boxSizing: 'border-box',
                position: { md: 'sticky' },
                top: { md: 88 },
                borderRadius: 1,
                border: 1,
                borderColor: 'divider',
                textAlign: 'left',
                bgcolor: '#EEF1F1',
                boxShadow: '0 2px 7px rgba(24, 28, 24, 0.08)',
            }}
        >
            <Stack spacing={1.5}>
                <Box>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>
                        {t('filters.title')}
                    </Typography>

                    <Typography variant="caption" color="text.secondary">
                        {t('filters.subtitle')}
                    </Typography>
                </Box>

                <Divider />

                <Box>
                    <Typography variant="caption" color="text.secondary">
                        {t('filters.combinationMode')}
                    </Typography>
                    <ToggleButtonGroup
                        exclusive
                        fullWidth
                        size="small"
                        value={combinationMode}
                        onChange={(_, value: FilterCombinationMode | null) => value && onCombinationModeChange(value)}
                        aria-label={t('filters.combinationMode')}
                        sx={{ mt: 0.75 }}
                    >
                        <ToggleButton value="and">{t('filters.matchAll')}</ToggleButton>
                        <ToggleButton value="or">{t('filters.matchAny')}</ToggleButton>
                    </ToggleButtonGroup>
                </Box>

                {(options.regionGroups.length > 0 || options.regions.length > 0) && <FilterAccordion
                    title={t('filters.regions')}
                    filters={filters}
                    disabledFilters={disabledFilters}
                    onToggle={toggleValue}
                    groups={[
                        {
                            title: t('filters.regionGroups'),
                            filterKey: 'regionGroupLocalNames',
                            items: options.regionGroups,
                        },
                        {
                            title: t('filters.regions'),
                            filterKey: 'regionLocalNames',
                            items: options.regions,
                        },
                    ]}
                />}

                {options.techniques.length > 0 && <FilterAccordion
                    title={t('filters.techniques')}
                    filters={filters}
                    disabledFilters={disabledFilters}
                    onToggle={toggleValue}
                    groups={[
                        {
                            filterKey: 'techniqueLocalNames',
                            items: options.techniques,
                        },
                    ]}
                />}

                {(options.ornamentTypes.length > 0 || options.ornaments.length > 0) && <FilterAccordion
                    title={t('filters.ornaments')}
                    filters={filters}
                    disabledFilters={disabledFilters}
                    onToggle={toggleValue}
                    groups={[
                        {
                            title: t('filters.ornamentTypes'),
                            filterKey: 'ornamentTypeLocalNames',
                            items: options.ornamentTypes,
                        },
                        {
                            title: t('filters.ornaments'),
                            filterKey: 'ornamentLocalNames',
                            items: options.ornaments,
                        },
                    ]}
                />}

                <Button
                    variant="outlined"
                    color="primary"
                    onClick={onClear}
                    fullWidth
                >
                    {t('filters.clear')}
                </Button>
            </Stack>
        </Paper>
    )
}

export default EmbroideryFilterPanel

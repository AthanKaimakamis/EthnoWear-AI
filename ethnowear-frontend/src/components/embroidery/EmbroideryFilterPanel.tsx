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
import FilterSection from '../filters/FilterSection.tsx'
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

                {(options.regionGroups.length > 0 || options.regions.length > 0) && <FilterSection
                    title={t('filters.regions')}
                    groups={[
                        {
                            key: 'regionGroupLocalNames',
                            title: t('filters.regionGroups'),
                            items: options.regionGroups,
                            selectedValues: filters.regionGroupLocalNames,
                            disabledValues: disabledFilters?.regionGroupLocalNames,
                            onToggle: value => toggleValue('regionGroupLocalNames', value),
                        },
                        {
                            key: 'regionLocalNames',
                            title: t('filters.regions'),
                            items: options.regions,
                            selectedValues: filters.regionLocalNames,
                            disabledValues: disabledFilters?.regionLocalNames,
                            onToggle: value => toggleValue('regionLocalNames', value),
                        },
                    ]}
                />}

                {options.techniques.length > 0 && <FilterSection
                    title={t('filters.techniques')}
                    groups={[
                        {
                            key: 'techniqueLocalNames',
                            items: options.techniques,
                            selectedValues: filters.techniqueLocalNames,
                            disabledValues: disabledFilters?.techniqueLocalNames,
                            onToggle: value => toggleValue('techniqueLocalNames', value),
                        },
                    ]}
                />}

                {(options.ornamentTypes.length > 0 || options.ornaments.length > 0) && <FilterSection
                    title={t('filters.ornaments')}
                    groups={[
                        {
                            key: 'ornamentTypeLocalNames',
                            title: t('filters.ornamentTypes'),
                            items: options.ornamentTypes,
                            selectedValues: filters.ornamentTypeLocalNames,
                            disabledValues: disabledFilters?.ornamentTypeLocalNames,
                            onToggle: value => toggleValue('ornamentTypeLocalNames', value),
                        },
                        {
                            key: 'ornamentLocalNames',
                            title: t('filters.ornaments'),
                            items: options.ornaments,
                            selectedValues: filters.ornamentLocalNames,
                            disabledValues: disabledFilters?.ornamentLocalNames,
                            onToggle: value => toggleValue('ornamentLocalNames', value),
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

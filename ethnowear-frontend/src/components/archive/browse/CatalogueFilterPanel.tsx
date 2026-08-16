import { Box, Button, Divider, Paper, Stack, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { ReferenceResource } from '../../../types/reference'
import FilterSection from '../../filters/FilterSection'

export type CatalogueFilterGroup = {
    key: string
    title: string
    items: ReferenceResource[]
    selectedValues: string[]
    onToggle: (value: string) => void
}

type Props = {
    groups: CatalogueFilterGroup[]
    onClear: () => void
}

export default function CatalogueFilterPanel({ groups, onClear }: Props) {
    const { t } = useTranslation()
    const selectedCount = groups.reduce((total, group) => total + group.selectedValues.length, 0)

    return (
        <Paper elevation={1} sx={{ p: 2, borderRadius: 1, border: 1, borderColor: 'divider', bgcolor: '#EEF1F1' }}>
            <Stack spacing={1.5}>
                <Box>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>{t('filters.title')}</Typography>
                    <Typography variant="caption" color="text.secondary">{t('filters.availableOnly')}</Typography>
                </Box>
                <Divider />
                {groups.map(group => (
                    <FilterSection key={group.key} title={group.title} groups={[{
                        key: group.key,
                        items: group.items,
                        selectedValues: group.selectedValues,
                        onToggle: group.onToggle,
                    }]} />
                ))}
                <Button variant="outlined" disabled={selectedCount === 0} onClick={onClear}>{t('filters.clear')}</Button>
            </Stack>
        </Paper>
    )
}

import { useMemo, useState } from 'react'
import {
    Box,
    Checkbox,
    FormControlLabel,
    InputAdornment,
    Stack,
    TextField,
    Typography,
} from '@mui/material'
import SearchIcon from '@mui/icons-material/Search'
import { useTranslation } from 'react-i18next'
import type { ReferenceResource } from '../../types/reference.ts'

type Props = {
    title: string
    items: ReferenceResource[]
    selectedValues: string[]
    disabledValues?: string[]
    showTitle?: boolean
    maxListHeight?: number
    onToggle: (value: string) => void
}

function searchableText(item: ReferenceResource) {
    return [
        item.localName,
        item.label,
        item.labels?.bg,
        item.labels?.en,
        ...(item.altLabels ?? []),
        ...(item.altLabelsByLanguage?.bg ?? []),
        ...(item.altLabelsByLanguage?.en ?? []),
    ].filter((value): value is string => Boolean(value)).join(' ').toLocaleLowerCase()
}

function SearchableFilterList({
    title,
    items,
    selectedValues,
    disabledValues = [],
    showTitle = false,
    maxListHeight = 220,
    onToggle,
}: Props) {
    const { t } = useTranslation()
    const [query, setQuery] = useState('')
    const selected = useMemo(() => new Set(selectedValues), [selectedValues])
    const disabled = useMemo(() => new Set(disabledValues), [disabledValues])
    const visibleItems = useMemo(() => {
        const normalizedQuery = query.trim().toLocaleLowerCase()
        return normalizedQuery
            ? items.filter(item => searchableText(item).includes(normalizedQuery))
            : items
    }, [items, query])

    return (
        <Box sx={{ minWidth: 0 }}>
            {showTitle && (
                <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ display: 'block', mb: 0.5, textTransform: 'uppercase', fontWeight: 700 }}
                >
                    {title}
                </Typography>
            )}

            <TextField
                fullWidth
                size="small"
                value={query}
                onChange={event => setQuery(event.target.value)}
                placeholder={t('filters.searchWithin', { name: title })}
                aria-label={t('filters.searchWithin', { name: title })}
                sx={{ mb: 1 }}
                slotProps={{
                    input: {
                        startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment>,
                    },
                }}
            />

            <Box
                sx={{
                    maxHeight: maxListHeight,
                    overflowY: 'auto',
                    overflowX: 'hidden',
                    overscrollBehavior: 'contain',
                    pr: 0.5,
                    scrollbarWidth: 'thin',
                    scrollbarColor: '#AEB4AE transparent',
                    '&::-webkit-scrollbar': { width: 6 },
                    '&::-webkit-scrollbar-thumb': { bgcolor: '#AEB4AE', borderRadius: 3 },
                }}
            >
                {visibleItems.length === 0 ? (
                    <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>
                        {t('filters.noMatchingOptions')}
                    </Typography>
                ) : (
                    <Stack spacing={0.25}>
                        {visibleItems.map(item => (
                            <FormControlLabel
                                key={item.localName}
                                disabled={disabled.has(item.localName)}
                                control={(
                                    <Checkbox
                                        size="small"
                                        checked={selected.has(item.localName)}
                                        onChange={() => onToggle(item.localName)}
                                        sx={{ p: 0.75 }}
                                    />
                                )}
                                label={(
                                    <Typography
                                        variant="body2"
                                        title={item.localName}
                                        sx={{ lineHeight: 1.3, textAlign: 'left', overflowWrap: 'anywhere' }}
                                    >
                                        {item.label || item.localName}
                                    </Typography>
                                )}
                                sx={{
                                    width: '100%',
                                    m: 0,
                                    alignItems: 'center',
                                    '& .MuiFormControlLabel-label': { flex: 1, minWidth: 0 },
                                }}
                            />
                        ))}
                    </Stack>
                )}
            </Box>
        </Box>
    )
}

export default SearchableFilterList

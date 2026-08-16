import { useState, type ReactNode } from 'react'
import { Box, Button, Collapse, Stack, Typography } from '@mui/material'
import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import FilterListIcon from '@mui/icons-material/FilterList'
import { useTranslation } from 'react-i18next'

type Props = {
    filters: ReactNode
    children: ReactNode
}

export default function ArchiveBrowseLayout({ filters, children }: Props) {
    const { t } = useTranslation()
    const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false)

    return (
        <Box sx={{
            display: 'grid',
            gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: '300px minmax(0, 1fr)' },
            gap: 3,
            px: { xs: 2, md: 5 },
            py: 3,
            alignItems: 'start',
            textAlign: 'left',
        }}>
            <Box sx={{ display: { xs: 'none', md: 'block' }, position: 'sticky', top: 140 }}>{filters}</Box>
            <Box sx={{ display: { xs: 'block', md: 'none' } }}>
                <Button
                    variant="text"
                    color="inherit"
                    aria-expanded={mobileFiltersOpen}
                    fullWidth
                    onClick={() => setMobileFiltersOpen(open => !open)}
                    sx={{ minHeight: 52, border: 1, borderLeft: 4, borderColor: 'divider', borderLeftColor: 'primary.main', bgcolor: '#EEF1F1' }}
                >
                    <Stack direction="row" sx={{ width: '100%', alignItems: 'center', justifyContent: 'space-between' }}>
                        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                            <FilterListIcon color="primary" fontSize="small" />
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>{mobileFiltersOpen ? t('filters.hide') : t('filters.show')}</Typography>
                        </Stack>
                        {mobileFiltersOpen ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                    </Stack>
                </Button>
                <Collapse in={mobileFiltersOpen} unmountOnExit><Box sx={{ mt: 1.5 }}>{filters}</Box></Collapse>
            </Box>
            <Box sx={{ minWidth: 0 }}>{children}</Box>
        </Box>
    )
}

import { Box, Stack, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { OntologyFeatureType } from '../../../types/catalogue'
import CatalogueConceptCard from './CatalogueConceptCard'
import type { CatalogueCategory } from './catalogueCategories'
import { Link } from 'react-router'
import ArrowForwardIcon from '@mui/icons-material/ArrowForward'
import { conceptPath } from '../../../app/archiveRoutes'

type Props = {
    section: CatalogueCategory
    entityType: OntologyFeatureType
}

export default function CatalogueCategorySection({ section, entityType }: Props) {
    const { t } = useTranslation()
    const illustrated = section.items.filter(item => item.representativeMediaAssetId || item.imageUrl || (item.evidenceCount ?? 0) > 0)
    const empty = section.items.filter(item => !illustrated.includes(item))
    return (
        <Box component="section">
            <Stack spacing={1.5}>
                <Box>
                    <Typography variant="h5" sx={{ fontWeight: 800 }}>{section.category.label || section.category.localName}</Typography>
                    <Typography variant="body2" color="text.secondary">{t('archiveReference.categoryCount', { count: section.items.length })}</Typography>
                </Box>
                {illustrated.length > 0 && <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(3, minmax(0, 1fr))', xl: 'repeat(4, minmax(0, 1fr))' }, gap: 2 }}>
                    {illustrated.map(item => <CatalogueConceptCard key={item.localName} item={item} entityType={entityType} />)}
                </Box>}
                {empty.length > 0 && <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'repeat(2, minmax(0, 1fr))', xl: 'repeat(3, minmax(0, 1fr))' }, columnGap: 3 }}>
                    {empty.map(item => <Box key={item.localName} component={Link} to={conceptPath(entityType, item.localName)} sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 1.5, px: 1, minWidth: 0, borderBottom: 1, borderColor: 'divider', color: 'text.primary', textDecoration: 'none', '&:hover': { bgcolor: 'action.hover', color: 'primary.main' }, '&:focus-visible': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 2 } }}>
                        <Box sx={{ flex: 1, minWidth: 0 }}><Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{item.label || item.localName}</Typography><Typography variant="caption" color="text.secondary">{t('archiveReference.evidenceCount', { count: 0 })}</Typography></Box>
                        <ArrowForwardIcon sx={{ fontSize: 18, flexShrink: 0, color: 'text.secondary' }} />
                    </Box>)}
                </Box>}
            </Stack>
        </Box>
    )
}

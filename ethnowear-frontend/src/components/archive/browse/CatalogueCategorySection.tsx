import { Box, Stack, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { OntologyFeatureType } from '../../../types/catalogue'
import CatalogueConceptCard from './CatalogueConceptCard'
import type { CatalogueCategory } from './catalogueCategories'

type Props = {
    section: CatalogueCategory
    entityType: OntologyFeatureType
}

export default function CatalogueCategorySection({ section, entityType }: Props) {
    const { t } = useTranslation()
    return (
        <Box component="section">
            <Stack spacing={1.5}>
                <Box>
                    <Typography variant="h5" sx={{ fontWeight: 800 }}>{section.category.label || section.category.localName}</Typography>
                    <Typography variant="body2" color="text.secondary">{t('archiveReference.categoryCount', { count: section.items.length })}</Typography>
                </Box>
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(3, minmax(0, 1fr))' }, gap: 3 }}>
                    {section.items.map(item => <CatalogueConceptCard key={item.localName} item={item} entityType={entityType} />)}
                </Box>
            </Stack>
        </Box>
    )
}

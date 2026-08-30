import { Card, CardActionArea, CardContent, CardMedia, Chip, Stack, Typography } from '@mui/material'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { conceptPath } from '../../../app/archiveRoutes'
import { archiveMediaUrl } from '../../../api/PublicArchiveApi'
import type { OntologyFeatureType } from '../../../types/catalogue'
import type { ReferenceResource } from '../../../types/reference'

const imageNotFoundUrl = '/Image-not-found.png'

type Props = {
    item: ReferenceResource
    entityType: OntologyFeatureType
}

export default function CatalogueConceptCard({ item, entityType }: Props) {
    const { t } = useTranslation()
    const imageUrl = item.representativeMediaAssetId
        ? archiveMediaUrl(item.representativeMediaAssetId)
        : item.imageUrl || imageNotFoundUrl

    return (
        <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column', borderRadius: 1, overflow: 'hidden', border: 1, borderColor: 'divider' }}>
            <CardActionArea component={Link} to={conceptPath(entityType, item.localName)} sx={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'stretch' }}>
                <CardMedia component="img" height="150" image={imageUrl} alt={item.label || item.localName}
                    sx={{ bgcolor: '#eef0f2', objectFit: 'cover', borderBottom: 1, borderColor: 'divider' }} />
                <CardContent sx={{ flexGrow: 1 }}>
                    <Stack spacing={1}>
                        <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.2 }}>{item.label || item.localName}</Typography>
                        {item.comment && <Typography variant="body2" color="text.secondary">{item.comment}</Typography>}
                        <Chip
                            size="small"
                            variant="outlined"
                            label={t('archiveReference.evidenceCount', { count: item.evidenceCount ?? 0 })}
                            sx={{ alignSelf: 'flex-start' }}
                        />
                    </Stack>
                </CardContent>
            </CardActionArea>
        </Card>
    )
}

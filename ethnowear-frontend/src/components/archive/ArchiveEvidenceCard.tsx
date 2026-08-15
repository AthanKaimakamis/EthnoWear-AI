import {
    Box,
    Card,
    CardActionArea,
    CardContent,
    CardMedia,
    Chip,
    Stack,
    Typography,
} from '@mui/material'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { archiveItemPath } from '../../app/archiveRoutes'
import { archiveMediaUrl } from '../../api/PublicArchiveApi'
import type { ArchiveEvidenceDetails } from '../../types/archive'
import type { Language } from '../../types/reference'

type Props = {
    item: ArchiveEvidenceDetails
    language: Language
}

function titleFor(item: ArchiveEvidenceDetails, language: Language) {
    return (language === 'en' ? item.titleEn : item.titleBg)
        ?? item.titleBg
        ?? item.titleEn
}

function captionFor(item: ArchiveEvidenceDetails, language: Language) {
    const media = item.previewMedia
    if (!media) return null

    return (language === 'en' ? media.captionEn : media.captionBg)
        ?? media.captionBg
        ?? media.captionEn
}

function ArchiveEvidenceCard({ item, language }: Props) {
    const { t } = useTranslation()
    const title = titleFor(item, language) ?? t('archiveDetails.untitled')
    const caption = captionFor(item, language)

    return (
        <Card sx={{ height: '100%', overflow: 'hidden' }}>
            <CardActionArea
                component={Link}
                to={archiveItemPath(item.archiveItemId)}
                sx={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'stretch' }}
            >
                <CardMedia
                    component="img"
                    image={item.previewMedia
                        ? archiveMediaUrl(item.previewMedia.mediaAssetId)
                        : '/Image-not-found.png'}
                    alt={caption ?? title}
                    sx={{ height: 180, objectFit: item.previewMedia ? 'cover' : 'contain', bgcolor: '#F1F2F5' }}
                />

                <CardContent sx={{ flexGrow: 1 }}>
                    <Stack spacing={1.25}>
                        <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.25 }}>
                            {title}
                        </Typography>

                        {caption && (
                            <Typography variant="body2" color="text.secondary">
                                {caption}
                            </Typography>
                        )}

                        <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap' }}>
                            <Chip label={t(`archiveDetails.trust.${item.trustedLevel}`)} size="small" variant="outlined" />
                            {item.periodText && <Chip label={item.periodText} size="small" variant="outlined" />}
                        </Box>
                    </Stack>
                </CardContent>
            </CardActionArea>
        </Card>
    )
}

export default ArchiveEvidenceCard

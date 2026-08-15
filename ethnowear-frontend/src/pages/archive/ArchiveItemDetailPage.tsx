import { useEffect, useMemo, useState } from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    Divider,
    Stack,
    Typography,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import ArrowForwardIcon from '@mui/icons-material/ArrowForward'
import { Link, useParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { getArchiveItemDetails } from '../../api/PublicArchiveApi'
import { conceptPath } from '../../app/archiveRoutes'
import MediaGallery, { type MediaGalleryItem } from '../../components/archive/MediaGallery'
import SourceCitation from '../../components/archive/SourceCitation'
import PageLoading from '../../components/loading/PageLoading'
import type { ArchiveItemDetailDetails } from '../../types/archive'
import type { Language } from '../../types/reference'

function ArchiveItemDetailPage() {
    const { id } = useParams()
    const { t, i18n } = useTranslation()
    const language: Language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
    const archiveItemId = Number(id)
    const [details, setDetails] = useState<ArchiveItemDetailDetails | null>(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        if (!Number.isInteger(archiveItemId) || archiveItemId <= 0) return

        const controller = new AbortController()

        async function loadDetails() {
            try {
                setLoading(true)
                setError(null)
                setDetails(await getArchiveItemDetails(archiveItemId, controller.signal))
            } catch (err) {
                if (!controller.signal.aborted) {
                    setError(err instanceof Error ? err.message : t('archiveDetails.loadError'))
                }
            } finally {
                if (!controller.signal.aborted) setLoading(false)
            }
        }

        void loadDetails()
        return () => controller.abort()
    }, [archiveItemId, t])

    const mediaItems = useMemo<MediaGalleryItem[]>(() => {
        if (!details) return []

        return details.media.map(({ media, asset }) => ({
            mediaAssetId: asset.id,
            title: asset.fileName ?? t('archiveDetails.mediaItem'),
            caption: (language === 'en' ? media.captionEn : media.captionBg)
                ?? media.captionBg
                ?? media.captionEn,
            mimeType: asset.mimeType,
        }))
    }, [details, language, t])

    if (!Number.isInteger(archiveItemId) || archiveItemId <= 0) {
        return <Box sx={{ px: { xs: 2, md: 5 }, py: 4 }}><Alert severity="error">{t('archiveDetails.invalidId')}</Alert></Box>
    }

    if (loading) {
        return <Box sx={{ px: { xs: 2, md: 5 }, py: 4 }}><PageLoading message={t('archiveDetails.loading')} /></Box>
    }

    if (error || !details) {
        return <Box sx={{ px: { xs: 2, md: 5 }, py: 4 }}><Alert severity="error">{error ?? t('archiveDetails.loadError')}</Alert></Box>
    }

    const item = details.archiveItem
    const title = (language === 'en' ? item.titleEn : item.titleBg)
        ?? item.titleBg
        ?? item.titleEn
        ?? t('archiveDetails.untitled')
    const description = (language === 'en' ? item.descriptionEn : item.descriptionBg)
        ?? item.descriptionBg
        ?? item.descriptionEn

    return (
        <Box sx={{ maxWidth: 1120, mx: 'auto', px: { xs: 2, md: 5 }, py: { xs: 3, md: 5 } }}>
            <Stack spacing={4}>
                <Box component="header">
                    <Button component={Link} to="/archive/embroideries" startIcon={<ArrowBackIcon />} sx={{ mb: 2 }}>
                        {t('archiveDetails.back')}
                    </Button>
                    <Typography variant="h3" component="h1" sx={{ fontWeight: 800 }}>
                        {title}
                    </Typography>
                    {description && (
                        <Typography variant="h6" color="text.secondary" sx={{ mt: 1.5, maxWidth: 860, fontWeight: 400 }}>
                            {description}
                        </Typography>
                    )}
                    <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap', mt: 2 }}>
                        <Chip label={t(`archiveDetails.types.${item.archiveType}`)} variant="outlined" />
                        <Chip label={t(`archiveDetails.trust.${item.trustedLevel}`)} color="primary" variant="outlined" />
                        {item.periodText && <Chip label={item.periodText} variant="outlined" />}
                    </Box>
                </Box>

                <Divider />

                {(item.originText || item.currentLocation || item.inventoryNumber) && (
                    <Box component="section">
                        <Typography variant="h5" sx={{ fontWeight: 800, mb: 1.5 }}>
                            {t('archiveDetails.record')}
                        </Typography>
                        <Stack spacing={0.75}>
                            {item.originText && <Typography><strong>{t('archiveDetails.origin')}:</strong> {item.originText}</Typography>}
                            {item.currentLocation && <Typography><strong>{t('archiveDetails.location')}:</strong> {item.currentLocation}</Typography>}
                            {item.inventoryNumber && <Typography><strong>{t('archiveDetails.inventory')}:</strong> {item.inventoryNumber}</Typography>}
                        </Stack>
                    </Box>
                )}

                {(item.ontologyRegionalEmbroideryLocalName || item.ontologyRegionLocalName) && (
                    <Box component="section">
                        <Typography variant="h5" sx={{ fontWeight: 800, mb: 1.5 }}>
                            {t('archiveDetails.classification')}
                        </Typography>
                        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                            {item.ontologyRegionalEmbroideryLocalName && (
                                <Button
                                    component={Link}
                                    to={conceptPath('REGIONAL_EMBROIDERY', item.ontologyRegionalEmbroideryLocalName)}
                                    variant="outlined"
                                    endIcon={<ArrowForwardIcon />}
                                >
                                    {t('entityTypes.REGIONAL_EMBROIDERY')}
                                </Button>
                            )}
                            {item.ontologyRegionLocalName && (
                                <Button
                                    component={Link}
                                    to={conceptPath('REGION', item.ontologyRegionLocalName)}
                                    variant="outlined"
                                    endIcon={<ArrowForwardIcon />}
                                >
                                    {t('entityTypes.REGION')}
                                </Button>
                            )}
                        </Box>
                    </Box>
                )}

                {details.features.length > 0 && (
                    <Box component="section">
                        <Typography variant="h5" sx={{ fontWeight: 800, mb: 1.5 }}>
                            {t('archiveDetails.observedFeatures')}
                        </Typography>
                        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                            {details.features.map((feature) => (
                                <Button
                                    key={feature.id}
                                    component={Link}
                                    to={conceptPath(feature.featureType, feature.ontologyLocalName)}
                                    variant="outlined"
                                    endIcon={<ArrowForwardIcon />}
                                    sx={{ textTransform: 'none' }}
                                >
                                    {feature.ontologyLocalName}
                                </Button>
                            ))}
                        </Box>
                    </Box>
                )}

                <MediaGallery title={t('archiveDetails.media')} items={mediaItems} />

                <Box component="section">
                    <Typography variant="h5" sx={{ fontWeight: 800, mb: 1.5 }}>
                        {t('entityDetails.sources')}
                    </Typography>
                    <SourceCitation source={details.source} />
                </Box>
            </Stack>
        </Box>
    )
}

export default ArchiveItemDetailPage

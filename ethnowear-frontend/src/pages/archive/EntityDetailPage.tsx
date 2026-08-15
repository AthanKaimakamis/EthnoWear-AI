import { useEffect, useMemo, useState } from 'react'
import {
    Alert,
    Box,
    Breadcrumbs,
    Button,
    Chip,
    Divider,
    Grid,
    Link as MuiLink,
    Pagination,
    Stack,
    Typography,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import { Link, useParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { getEntityDetails } from '../../api/CatalogueApi'
import {
    conceptCollectionPath,
    featureTypeFromRouteSegment,
} from '../../app/archiveRoutes'
import ArchiveEvidenceCard from '../../components/archive/ArchiveEvidenceCard'
import MediaGallery, { type MediaGalleryItem } from '../../components/archive/MediaGallery'
import RelatedEntitySection from '../../components/archive/RelatedEntitySection'
import SourceCitation from '../../components/archive/SourceCitation'
import PageLoading from '../../components/loading/PageLoading'
import type {
    EntityDetailDetails,
    EntityLinkDetails,
    OntologyFeatureType,
} from '../../types/catalogue'
import type { Language } from '../../types/reference'

function EntityDetailPage() {
    const { entityType: entityTypeSegment, localName } = useParams()
    const { t, i18n } = useTranslation()
    const language: Language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
    const entityType = featureTypeFromRouteSegment(entityTypeSegment)
    const [details, setDetails] = useState<EntityDetailDetails | null>(null)
    const [evidencePage, setEvidencePage] = useState(1)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        if (!entityType || !localName) {
            setError(t('entityDetails.invalidRoute'))
            setLoading(false)
            return
        }

        const controller = new AbortController()

        async function loadDetails() {
            try {
                setLoading(true)
                setError(null)
                const response = await getEntityDetails(
                    entityType,
                    localName,
                    language,
                    { page: evidencePage - 1, size: 12, sort: 'id,desc' },
                    controller.signal,
                )
                setDetails(response)
            } catch (err) {
                if (!controller.signal.aborted) {
                    setError(err instanceof Error ? err.message : t('entityDetails.loadError'))
                }
            } finally {
                if (!controller.signal.aborted) setLoading(false)
            }
        }

        void loadDetails()
        return () => controller.abort()
    }, [entityType, evidencePage, language, localName, t])

    const mediaItems = useMemo<MediaGalleryItem[]>(() => {
        if (!details) return []

        const items = new Map<number, MediaGalleryItem>()
        details.content.mediaAnnotations.forEach((media) => {
            if (items.has(media.mediaAssetId)) return
            const caption = (language === 'en' ? media.captionEn : media.captionBg)
                ?? media.captionBg
                ?? media.captionEn

            items.set(media.mediaAssetId, {
                mediaAssetId: media.mediaAssetId,
                title: media.fileName ?? t('archiveDetails.mediaItem'),
                caption,
                mimeType: media.mimeType,
            })
        })
        return [...items.values()]
    }, [details, language, t])

    if (loading && !details) {
        return <Box sx={{ px: { xs: 2, md: 5 }, py: 4 }}><PageLoading message={t('entityDetails.loading')} /></Box>
    }

    if (error || !details || !entityType) {
        return <Box sx={{ px: { xs: 2, md: 5 }, py: 4 }}><Alert severity="error">{error ?? t('entityDetails.loadError')}</Alert></Box>
    }

    const ontology = details.ontology
    const relatedEntries = Object.entries(ontology.relatedEntities) as [
        OntologyFeatureType,
        EntityLinkDetails[],
    ][]

    return (
        <Box sx={{ maxWidth: 1240, mx: 'auto', px: { xs: 2, md: 5 }, py: { xs: 3, md: 5 } }}>
            <Stack spacing={4}>
                <Breadcrumbs aria-label={t('entityDetails.breadcrumbs')}>
                    <MuiLink component={Link} to={conceptCollectionPath(entityType)} color="inherit">
                        {t(`entityTypes.${entityType}`)}
                    </MuiLink>
                    <Typography color="text.primary">{ontology.label}</Typography>
                </Breadcrumbs>

                <Box component="header">
                    <Button
                        component={Link}
                        to={conceptCollectionPath(entityType)}
                        startIcon={<ArrowBackIcon />}
                        sx={{ mb: 2 }}
                    >
                        {t('entityDetails.back')}
                    </Button>
                    <Typography variant="h3" component="h1" sx={{ fontWeight: 800 }}>
                        {ontology.label}
                    </Typography>
                    {ontology.comment && (
                        <Typography variant="h6" color="text.secondary" sx={{ mt: 1.5, maxWidth: 900, fontWeight: 400 }}>
                            {ontology.comment}
                        </Typography>
                    )}
                    <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap', mt: 2 }}>
                        {ontology.categories.map((category) => (
                            <Chip key={category.localName} label={category.label} variant="outlined" />
                        ))}
                    </Box>
                </Box>

                <Divider />

                {relatedEntries.map(([type, items]) => (
                    <RelatedEntitySection
                        key={type}
                        title={t('entityDetails.related', { type: t(`entityTypes.${type}`) })}
                        entityType={type}
                        items={items}
                    />
                ))}

                <MediaGallery title={t('entityDetails.media')} items={mediaItems} />

                {details.evidence.content.length > 0 && (
                    <Box component="section">
                        <Stack spacing={2}>
                            <Box>
                                <Typography variant="h5" sx={{ fontWeight: 800 }}>
                                    {t('entityDetails.archiveEvidence')}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    {t('entityDetails.archiveEvidenceCount', { count: details.evidence.totalElements })}
                                </Typography>
                            </Box>

                            <Grid container spacing={2.5}>
                                {details.evidence.content.map((item) => (
                                    <Grid key={item.archiveItemId} size={{ xs: 12, sm: 6, lg: 4 }}>
                                        <ArchiveEvidenceCard item={item} language={language} />
                                    </Grid>
                                ))}
                            </Grid>

                            {details.evidence.totalPages > 1 && (
                                <Pagination
                                    count={details.evidence.totalPages}
                                    page={evidencePage}
                                    onChange={(_, page) => setEvidencePage(page)}
                                    color="primary"
                                    sx={{ alignSelf: 'center' }}
                                />
                            )}
                        </Stack>
                    </Box>
                )}

                {details.content.sources.length > 0 && (
                    <Box component="section">
                        <Stack spacing={2}>
                            <Typography variant="h5" sx={{ fontWeight: 800 }}>
                                {t('entityDetails.sources')}
                            </Typography>
                            {details.content.sources.map((source) => (
                                <SourceCitation key={source.sourceReferenceId} source={source} />
                            ))}
                        </Stack>
                    </Box>
                )}
            </Stack>
        </Box>
    )
}

export default EntityDetailPage

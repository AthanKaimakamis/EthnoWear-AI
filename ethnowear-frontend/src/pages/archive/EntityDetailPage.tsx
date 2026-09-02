import { useMemo, useState } from 'react'
import {
    Alert,
    Box,
    Breadcrumbs,
    Button,
    Chip,
    Grid,
    Link as MuiLink,
    LinearProgress,
    Pagination,
    Stack,
    Typography,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import { Link, useNavigate, useParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { entityDetailsQueryOptions } from '../../api/PublicQueryOptions'
import {
    conceptCollectionPath,
    featureTypeFromRouteSegment,
} from '../../app/archiveRoutes'
import ArchiveEvidenceCard from '../../components/archive/ArchiveEvidenceCard'
import ArchiveWorkspaceDialog from '../../components/archive/ArchiveWorkspaceDialog'
import MediaGallery, { type MediaGalleryItem } from '../../components/archive/MediaGallery'
import RelatedEntitySection from '../../components/archive/RelatedEntitySection'
import SourceCitation from '../../components/archive/SourceCitation'
import PageLoading from '../../components/loading/PageLoading'
import type {
    EntityLinkDetails,
    OntologyFeatureType,
} from '../../types/catalogue'
import type { Language } from '../../types/reference'

function EntityDetailPage() {
    const { entityType: entityTypeSegment, localName } = useParams()
    const navigate = useNavigate()
    const { t, i18n } = useTranslation()
    const language: Language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
    const entityType = featureTypeFromRouteSegment(entityTypeSegment)
    const [evidencePage, setEvidencePage] = useState(1)
    const detailsOptions = entityDetailsQueryOptions(
        entityType,
        localName,
        language,
        { page: evidencePage - 1, size: 12, sort: 'id,desc' },
    )
    const detailsQuery = useQuery({
        ...detailsOptions,
        placeholderData: (previousData, previousQuery) => {
            const previousKey = previousQuery?.queryKey
            const sameEntity = previousKey?.[3] === entityType
                && previousKey?.[4] === localName
                && previousKey?.[5] === language
            return sameEntity ? previousData : undefined
        },
    })
    const details = detailsQuery.data
    const error = detailsQuery.error instanceof Error
        ? detailsQuery.error.message
        : detailsQuery.error ? t('entityDetails.loadError') : null

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

    if (!entityType || !localName) {
        return <Box sx={{ px: { xs: 2, md: 5 }, py: 4 }}><Alert severity="error">{t('entityDetails.invalidRoute')}</Alert></Box>
    }

    if (detailsQuery.isPending) {
        return <Box sx={{ px: { xs: 2, md: 5 }, py: 4 }}><PageLoading message={t('entityDetails.loading')} /></Box>
    }

    if (error || !details) {
        return <Box sx={{ px: { xs: 2, md: 5 }, py: 4 }}><Alert severity="error">{error ?? t('entityDetails.loadError')}</Alert></Box>
    }

    const ontology = details.ontology
    const relatedEntries = Object.entries(ontology.relatedEntities) as [
        OntologyFeatureType,
        EntityLinkDetails[],
    ][]
    const visibleRelatedEntries = relatedEntries.filter(([, items]) => items.length > 0)
    const hasSidebar = visibleRelatedEntries.length > 0 || details.content.sources.length > 0

    const close = () => navigateBackOr(navigate, conceptCollectionPath(entityType))

    return (
        <ArchiveWorkspaceDialog onClose={close} labelledBy="archive-concept-title">
            <Stack component="article" spacing={0}>
                {detailsQuery.isFetching && <LinearProgress aria-label={t('entityDetails.loading')} />}
                <Box component="header" sx={{ borderTop: 4, borderColor: 'primary.main', bgcolor: '#F1F3F1', px: { xs: 2, md: 4 }, py: { xs: 2.5, md: 3.5 } }}>
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between', pr: 5 }}>
                        <Breadcrumbs aria-label={t('entityDetails.breadcrumbs')}>
                            <MuiLink component={Link} to={conceptCollectionPath(entityType)} color="inherit">
                                {t(`entityTypes.${entityType}`)}
                            </MuiLink>
                            <Typography color="text.primary">{ontology.label}</Typography>
                        </Breadcrumbs>
                        <Button onClick={close} startIcon={<ArrowBackIcon />} sx={{ alignSelf: { xs: 'flex-start', sm: 'center' } }}>
                            {t('entityDetails.back')}
                        </Button>
                    </Stack>
                    <Typography id="archive-concept-title" component="h1" sx={{ mt: 3, color: '#171917', fontWeight: 900, pr: 6, fontSize: { xs: '2rem', md: '2.75rem' }, lineHeight: 1.08 }}>
                        {ontology.label}
                    </Typography>
                    {ontology.comment && (
                        <Typography color="text.secondary" sx={{ mt: 1.5, maxWidth: 920, fontSize: '1.05rem', lineHeight: 1.65 }}>
                            {ontology.comment}
                        </Typography>
                    )}
                    <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap', mt: 2 }}>
                        {ontology.categories.map((category) => (
                            <Chip key={category.localName} label={category.label} variant="outlined" />
                        ))}
                    </Box>
                </Box>

                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: hasSidebar ? 'minmax(0, 2fr) minmax(280px, .8fr)' : '1fr' }, gap: { xs: 4, lg: 6 }, px: { xs: 1, md: 4 }, py: { xs: 4, md: 5 } }}>
                    <Stack spacing={5} sx={{ minWidth: 0 }}>
                        <MediaGallery title={t('entityDetails.media')} items={mediaItems} />

                        {details.evidence.content.length > 0 && (
                            <Box component="section">
                                <Stack spacing={2.5}>
                                    <Box sx={{ pb: 1.5, borderBottom: 1, borderColor: 'divider' }}>
                                        <Typography variant="h4" sx={{ fontWeight: 850, fontSize: { xs: '1.5rem', md: '1.8rem' } }}>
                                            {t('entityDetails.archiveEvidence')}
                                        </Typography>
                                        <Typography color="text.secondary" sx={{ mt: .5 }}>
                                            {t('entityDetails.archiveEvidenceCount', { count: details.evidence.totalElements })}
                                        </Typography>
                                    </Box>

                                    <Grid container spacing={2.5}>
                                        {details.evidence.content.map((item) => (
                                            <Grid key={item.archiveItemId} size={{ xs: 12, sm: details.evidence.content.length === 1 ? 12 : 6 }}>
                                                <ArchiveEvidenceCard item={item} language={language} featured={details.evidence.content.length === 1} />
                                            </Grid>
                                        ))}
                                    </Grid>

                                    {details.evidence.totalPages > 1 && (
                                        <Pagination count={details.evidence.totalPages} page={evidencePage} onChange={(_, page) => setEvidencePage(page)} color="primary" sx={{ alignSelf: 'center' }} />
                                    )}
                                </Stack>
                            </Box>
                        )}
                    </Stack>

                    {hasSidebar && (
                        <Stack component="aside" spacing={4} sx={{ minWidth: 0, borderLeft: { lg: 1 }, borderColor: 'divider', pl: { lg: 4 }, alignSelf: 'start' }}>
                            {visibleRelatedEntries.map(([type, items]) => (
                                <RelatedEntitySection key={type} title={t('entityDetails.related', { type: t(`entityTypes.${type}`) })} entityType={type} items={items} />
                            ))}

                            {details.content.sources.length > 0 && (
                                <Box component="section">
                                    <Stack spacing={2}>
                                        <Typography variant="h5" sx={{ fontWeight: 800 }}>{t('entityDetails.sources')}</Typography>
                                        {details.content.sources.map((source) => <SourceCitation key={source.sourceReferenceId} source={source} />)}
                                    </Stack>
                                </Box>
                            )}
                        </Stack>
                    )}
                </Box>
            </Stack>
        </ArchiveWorkspaceDialog>
    )
}

export default EntityDetailPage

function navigateBackOr(navigate: ReturnType<typeof useNavigate>, fallback: string) {
    if ((window.history.state?.idx ?? 0) > 0) navigate(-1)
    else navigate(fallback)
}

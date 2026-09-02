import { useMemo, type ReactNode } from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    Divider,
    Link as MuiLink,
    Stack,
    Typography,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import ArrowForwardIcon from '@mui/icons-material/ArrowForward'
import CalendarMonthOutlinedIcon from '@mui/icons-material/CalendarMonthOutlined'
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined'
import LocationOnOutlinedIcon from '@mui/icons-material/LocationOnOutlined'
import PlaceOutlinedIcon from '@mui/icons-material/PlaceOutlined'
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined'
import { Link, useNavigate, useParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { archiveMediaUrl } from '../../api/PublicArchiveApi'
import {
    archiveItemDetailsQueryOptions,
    referenceDataQueryOptions,
} from '../../api/PublicQueryOptions'
import { conceptPath } from '../../app/archiveRoutes'
import MediaGallery, { type MediaGalleryItem } from '../../components/archive/MediaGallery'
import ArchiveWorkspaceDialog from '../../components/archive/ArchiveWorkspaceDialog'
import SourceCitation from '../../components/archive/SourceCitation'
import { PreviewableImage } from '../../components/common/ImageViewerDialog'
import PageLoading from '../../components/loading/PageLoading'
import type { ArchiveItemFeatureDetails, ArchiveItemMediaContentDetails } from '../../types/archive'
import type { OntologyFeatureType } from '../../types/catalogue'
import type { Language } from '../../types/reference'

const FEATURE_ORDER: OntologyFeatureType[] = ['MOTIF', 'ORNAMENT', 'TECHNIQUE', 'COLOR']

function firstText(...values: (string | null | undefined)[]) {
    return values.find(value => value?.trim())?.trim() ?? null
}

function humanizeLocalName(value: string) {
    return value.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ')
}

function ArchiveItemDetailPage() {
    const { id } = useParams()
    const navigate = useNavigate()
    const { t, i18n } = useTranslation()
    const language: Language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
    const archiveItemId = Number(id)
    const detailsQuery = useQuery(archiveItemDetailsQueryOptions(archiveItemId))
    const referenceQuery = useQuery(referenceDataQueryOptions(language))
    const details = detailsQuery.data
    const error = detailsQuery.error instanceof Error
        ? detailsQuery.error.message
        : detailsQuery.error ? t('archiveDetails.loadError') : null

    const labels = useMemo(() => {
        const reference = referenceQuery.data
        if (!reference) return new Map<string, string>()

        return new Map([
            ...reference.regions,
            ...reference.regionalEmbroideryTypes, ...reference.regionalMotifTypes,
            ...reference.motifs,
            ...reference.ornaments,
            ...reference.techniques,
            ...reference.colors,
        ].map(resource => [resource.localName, resource.label]))
    }, [referenceQuery.data])

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

    const primaryMedia = useMemo(() => {
        if (!details) return null
        return details.media.find(({ media }) => media.role === 'PRIMARY') ?? details.media[0] ?? null
    }, [details])

    const additionalMedia = useMemo(() => {
        if (!primaryMedia) return mediaItems
        return mediaItems.filter(item => item.mediaAssetId !== primaryMedia.asset.id)
    }, [mediaItems, primaryMedia])

    const groupedFeatures = useMemo(() => {
        const grouped = new Map<OntologyFeatureType, ArchiveItemFeatureDetails[]>()
        details?.features.forEach(feature => {
            grouped.set(feature.featureType, [...(grouped.get(feature.featureType) ?? []), feature])
        })
        return FEATURE_ORDER
            .map(type => [type, grouped.get(type) ?? []] as const)
            .filter(([, features]) => features.length > 0)
    }, [details])

    if (!Number.isInteger(archiveItemId) || archiveItemId <= 0) {
        return <Box sx={{ px: { xs: 2, md: 5 }, py: 4 }}><Alert severity="error">{t('archiveDetails.invalidId')}</Alert></Box>
    }

    if (detailsQuery.isPending) {
        return <Box sx={{ px: { xs: 2, md: 5 }, py: 4 }}><PageLoading message={t('archiveDetails.loading')} /></Box>
    }

    if (error || !details) {
        return <Box sx={{ px: { xs: 2, md: 5 }, py: 4 }}><Alert severity="error">{error ?? t('archiveDetails.loadError')}</Alert></Box>
    }

    const item = details.archiveItem
    const title = firstText(language === 'en' ? item.titleEn : item.titleBg, item.titleBg, item.titleEn)
        ?? t('archiveDetails.untitled')
    const description = firstText(
        language === 'en' ? item.descriptionEn : item.descriptionBg,
        item.descriptionBg,
        item.descriptionEn,
    )
    const labelFor = (localName: string) => labels.get(localName) ?? humanizeLocalName(localName)

    const close = () => navigateBackOr(navigate, '/archive/embroideries')

    return (
        <ArchiveWorkspaceDialog onClose={close} labelledBy="archive-item-title">
            <Box component="article">
            <Button onClick={close} startIcon={<ArrowBackIcon />} sx={{ mb: { xs: 2, md: 3 } }}>
                {t('archiveDetails.back')}
            </Button>

            <Box
                component="header"
                sx={{
                    display: 'grid',
                    gridTemplateColumns: { xs: '1fr', md: 'minmax(0, 1.25fr) minmax(340px, .75fr)' },
                    gap: { xs: 3, md: 5 },
                    alignItems: 'start',
                }}
            >
                <PrimaryMedia media={primaryMedia} language={language} fallbackTitle={title} />

                <Stack spacing={2.5} sx={{ minWidth: 0, pt: { md: 1 } }}>
                    <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap' }}>
                        <Chip label={t(`archiveDetails.types.${item.archiveType}`)} variant="outlined" />
                        <Chip label={t(`archiveDetails.trust.${item.trustedLevel}`)} color="primary" variant="outlined" />
                    </Box>

                    <Box>
                        <Typography
                            variant="h3"
                            component="h1"
                            id="archive-item-title"
                            sx={{ color: '#171917', fontWeight: 800, fontSize: { xs: '1.75rem', sm: '2.25rem' }, lineHeight: 1.12, m: 0, pr: 6 }}
                        >
                            {title}
                        </Typography>
                        {description && (
                            <Typography color="text.secondary" sx={{ mt: 2, fontSize: '1.05rem', lineHeight: 1.65 }}>
                                {description}
                            </Typography>
                        )}
                    </Box>

                    {(item.periodText || item.originText || item.currentLocation || item.inventoryNumber) && (
                        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 2, pt: 1 }}>
                            <RecordFact icon={<CalendarMonthOutlinedIcon />} label={t('archiveDetails.period')} value={item.periodText} />
                            <RecordFact icon={<PlaceOutlinedIcon />} label={t('archiveDetails.origin')} value={item.originText} />
                            <RecordFact icon={<LocationOnOutlinedIcon />} label={t('archiveDetails.location')} value={item.currentLocation} />
                            <RecordFact icon={<Inventory2OutlinedIcon />} label={t('archiveDetails.inventory')} value={item.inventoryNumber} />
                        </Box>
                    )}
                </Stack>
            </Box>

            <Divider sx={{ my: { xs: 4, md: 5 } }} />

            <Stack spacing={{ xs: 4, md: 5 }}>
                {(item.ontologyRegionalEmbroideryLocalName || item.ontologyRegionalMotifLocalName || item.ontologyRegionLocalName) && (
                    <Box component="section">
                        <SectionHeading
                            title={t('archiveDetails.classification')}
                            description={t('archiveDetails.classificationDescription')}
                        />
                        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 2, maxWidth: 900 }}>
                            {item.ontologyRegionalEmbroideryLocalName && (
                                <ConceptRelationship
                                    type={t('archiveDetails.regionalStyle')}
                                    label={labelFor(item.ontologyRegionalEmbroideryLocalName)}
                                    to={conceptPath('REGIONAL_EMBROIDERY', item.ontologyRegionalEmbroideryLocalName)}
                                />
                            )}
                            {item.ontologyRegionalMotifLocalName && (
                                <ConceptRelationship
                                    type={t('archiveDetails.regionalMotif')}
                                    label={labelFor(item.ontologyRegionalMotifLocalName)}
                                    to={conceptPath('REGIONAL_MOTIF', item.ontologyRegionalMotifLocalName)}
                                />
                            )}
                            {item.ontologyRegionLocalName && (
                                <ConceptRelationship
                                    type={t('archiveDetails.associatedRegion')}
                                    label={labelFor(item.ontologyRegionLocalName)}
                                    to={conceptPath('REGION', item.ontologyRegionLocalName)}
                                />
                            )}
                        </Box>
                    </Box>
                )}

                {groupedFeatures.length > 0 && (
                    <Box component="section">
                        <SectionHeading
                            title={t('archiveDetails.observedFeatures')}
                            description={t('archiveDetails.observedFeaturesDescription')}
                        />
                        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, minmax(0, 1fr))' }, columnGap: 5, rowGap: 3 }}>
                            {groupedFeatures.map(([type, features]) => (
                                <Box key={type}>
                                    <Typography variant="overline" color="text.secondary" sx={{ fontWeight: 800 }}>
                                        {t(`archiveDetails.featureGroups.${type}`)}
                                    </Typography>
                                    <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: 0.75 }}>
                                        {features.map(feature => (
                                            <Chip
                                                key={feature.id}
                                                component={Link}
                                                clickable
                                                to={conceptPath(feature.featureType, feature.ontologyLocalName)}
                                                label={labelFor(feature.ontologyLocalName)}
                                                variant="outlined"
                                                deleteIcon={<ArrowForwardIcon />}
                                                onDelete={() => undefined}
                                                sx={{ height: 34, fontSize: '.8rem', textTransform: 'none', px: 0.25 }}
                                            />
                                        ))}
                                    </Box>
                                </Box>
                            ))}
                        </Box>
                    </Box>
                )}

                <MediaGallery title={t('archiveDetails.additionalMedia')} items={additionalMedia} />

                <Box component="section">
                    <SectionHeading
                        title={t('entityDetails.sources')}
                        description={t('archiveDetails.sourcesDescription')}
                    />
                    <SourceCitation source={details.source} />
                </Box>
            </Stack>
            </Box>
        </ArchiveWorkspaceDialog>
    )
}

function navigateBackOr(navigate: ReturnType<typeof useNavigate>, fallback: string) {
    if ((window.history.state?.idx ?? 0) > 0) navigate(-1)
    else navigate(fallback)
}

function PrimaryMedia({
    media,
    language,
    fallbackTitle,
}: {
    media: ArchiveItemMediaContentDetails | null
    language: Language
    fallbackTitle: string
}) {
    const { t } = useTranslation()
    const caption = media
        ? firstText(language === 'en' ? media.media.captionEn : media.media.captionBg, media.media.captionBg, media.media.captionEn)
        : null

    return (
        <Box>
            <Box
                sx={{
                    aspectRatio: '4 / 3',
                    maxHeight: 620,
                    bgcolor: '#ECEFED',
                    border: 1,
                    borderColor: 'divider',
                    overflow: 'hidden',
                    display: 'grid',
                    placeItems: 'center',
                }}
            >
                {!media ? (
                    <Typography color="text.secondary">{t('archiveDetails.noMedia')}</Typography>
                ) : media.asset.mimeType?.startsWith('image/') ? (
                    <PreviewableImage
                        src={archiveMediaUrl(media.asset.id)}
                        alt={fallbackTitle}
                        caption={caption}
                        buttonSx={{ width: '100%', height: '100%' }}
                        imageSx={{ width: '100%', height: '100%', objectFit: 'contain' }}
                    />
                ) : (
                    <Button
                        component="a"
                        href={archiveMediaUrl(media.asset.id)}
                        target="_blank"
                        rel="noreferrer"
                        startIcon={<PictureAsPdfOutlinedIcon />}
                    >
                        {t('archiveDetails.openDocument')}
                    </Button>
                )}
            </Box>
            {caption && <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>{caption}</Typography>}
        </Box>
    )
}

function RecordFact({ icon, label, value }: { icon: ReactNode, label: string, value: string | null }) {
    if (!value) return null
    return (
        <Stack direction="row" spacing={1.25} sx={{ alignItems: 'flex-start' }}>
            <Box sx={{ color: 'primary.main', display: 'flex', mt: 0.25 }}>{icon}</Box>
            <Box sx={{ minWidth: 0 }}>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{label}</Typography>
                <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{value}</Typography>
            </Box>
        </Stack>
    )
}

function SectionHeading({ title, description }: { title: string, description: string }) {
    return (
        <Box sx={{ mb: 2 }}>
            <Typography variant="h5" sx={{ fontWeight: 800 }}>{title}</Typography>
            <Typography color="text.secondary" sx={{ mt: 0.5 }}>{description}</Typography>
        </Box>
    )
}

function ConceptRelationship({ type, label, to }: { type: string, label: string, to: string }) {
    return (
        <Box sx={{ borderLeft: 3, borderColor: 'primary.main', pl: 2, py: 0.75 }}>
            <Typography variant="caption" color="text.secondary">{type}</Typography>
            <MuiLink
                component={Link}
                to={to}
                underline="hover"
                color="text.primary"
                sx={{ display: 'flex', alignItems: 'center', gap: 0.75, width: 'fit-content', fontSize: '1.05rem', fontWeight: 700 }}
            >
                {label}
                <ArrowForwardIcon fontSize="small" color="primary" />
            </MuiLink>
        </Box>
    )
}

export default ArchiveItemDetailPage

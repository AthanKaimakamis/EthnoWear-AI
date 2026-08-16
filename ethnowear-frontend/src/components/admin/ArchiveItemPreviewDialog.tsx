import { useEffect, useMemo, useState } from 'react'
import {
    Alert, Box, Button, Chip, Divider, Stack, Typography,
} from '@mui/material'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import OpenInNewOutlinedIcon from '@mui/icons-material/OpenInNewOutlined'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { getAdminArchiveItemDetail } from '../../api/ArchiveAdminApi'
import { getFullReference } from '../../api/ReferenceApi'
import MediaGallery, { type MediaGalleryItem } from '../archive/MediaGallery'
import SourceCitation from '../archive/SourceCitation'
import PageLoading from '../loading/PageLoading'
import AdminModal from './AdminModal'
import ArchiveStatusChip from './ArchiveStatusChip'
import TrustedLevelChip from './TrustedLevelChip'
import { publicationErrorMessages } from './archiveWorkflow'
import type { ArchiveItemDetailDetails } from '../../types/archive'
import type { ReferenceResource } from '../../types/reference'

type Props = {
    itemId: number
    onClose: () => void
    onEdit: () => void
}

export default function ArchiveItemPreviewDialog({ itemId, onClose, onEdit }: Props) {
    const { t, i18n } = useTranslation()
    const language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
    const [details, setDetails] = useState<ArchiveItemDetailDetails | null>(null)
    const [labels, setLabels] = useState<Map<string, string>>(new Map())
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        const controller = new AbortController()
        Promise.all([getAdminArchiveItemDetail(itemId, controller.signal), getFullReference(language)])
            .then(([itemDetails, reference]) => {
                setDetails(itemDetails)
                const resources: ReferenceResource[] = [
                    ...reference.regions, ...reference.regionalEmbroideryTypes, ...reference.techniques,
                    ...reference.ornaments, ...reference.motifs, ...reference.colors,
                ]
                setLabels(new Map(resources.map(resource => [resource.localName, resource.label])))
            })
            .catch(caught => {
                if (!(caught instanceof DOMException && caught.name === 'AbortError')) {
                    setError(publicationErrorMessages(caught, t('publication.errors.preview'), t).join(' '))
                }
            })
            .finally(() => setLoading(false))
        return () => controller.abort()
    }, [itemId, language, t])

    const mediaItems = useMemo<MediaGalleryItem[]>(() => details?.media.map(({ media, asset }) => ({
        mediaAssetId: asset.id,
        title: asset.fileName ?? t('archiveDetails.mediaItem'),
        caption: (language === 'en' ? media.captionEn : media.captionBg) ?? media.captionBg ?? media.captionEn,
        mimeType: asset.mimeType,
    })) ?? [], [details, language, t])

    const item = details?.archiveItem
    const title = item
        ? (language === 'en' ? item.titleEn : item.titleBg) ?? item.titleBg ?? item.titleEn ?? t('archiveDetails.untitled')
        : t('curator.archive.preview')
    const description = item
        ? (language === 'en' ? item.descriptionEn : item.descriptionBg) ?? item.descriptionBg ?? item.descriptionEn
        : null

    return (
        <AdminModal
            open
            title={title}
            onClose={onClose}
            maxWidth="lg"
            actions={
                <>
                    {item?.publicationStatus === 'PUBLISHED' && (
                        <Button component={Link} to={`/archive/items/${item.id}`} startIcon={<OpenInNewOutlinedIcon />}>
                            {t('publication.actions.open-public')}
                        </Button>
                    )}
                    <Button variant="contained" startIcon={<EditOutlinedIcon />} onClick={onEdit}>{t('admin.edit')}</Button>
                </>
            }
        >
            <Box sx={{ minHeight: 360 }}>
                {loading && <PageLoading message={t('archiveDetails.loading')} />}
                {error && <Alert severity="error">{error}</Alert>}
                {item && details && (
                    <Stack spacing={3}>
                        {description && <Typography color="text.secondary">{description}</Typography>}
                        <Box sx={{ display: 'flex', gap: .75, flexWrap: 'wrap' }}>
                            <Chip label={t(`archiveDetails.types.${item.archiveType}`)} variant="outlined" />
                            <ArchiveStatusChip value={item.publicationStatus} />
                            <TrustedLevelChip value={item.trustedLevel} />
                            {item.periodText && <Chip label={item.periodText} variant="outlined" />}
                        </Box>
                        <Divider />
                        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
                            <Detail label={t('archiveDetails.origin')} value={item.originText} />
                            <Detail label={t('archiveDetails.location')} value={item.currentLocation} />
                            <Detail label={t('archiveDetails.inventory')} value={item.inventoryNumber} />
                            <Detail label={t('curator.fields.region')} value={labels.get(item.ontologyRegionLocalName ?? '')} />
                            <Detail label={t('curator.fields.embroidery')} value={labels.get(item.ontologyRegionalEmbroideryLocalName ?? '')} />
                        </Box>
                        {details.features.length > 0 && <Box><Typography variant="h6" sx={{ mb: 1 }}>{t('archiveDetails.observedFeatures')}</Typography><Box sx={{ display: 'flex', gap: .75, flexWrap: 'wrap' }}>{details.features.map(feature => <Chip key={feature.id} label={labels.get(feature.ontologyLocalName) ?? feature.ontologyLocalName} variant="outlined" />)}</Box></Box>}
                        <MediaGallery title={t('archiveDetails.media')} items={mediaItems} />
                        <Box><Typography variant="h6" sx={{ mb: 1 }}>{t('entityDetails.sources')}</Typography><SourceCitation source={details.source} /></Box>
                    </Stack>
                )}
            </Box>
        </AdminModal>
    )
}

function Detail({ label, value }: { label: string, value: string | null | undefined }) {
    if (!value) return null
    return <Box><Typography variant="caption" color="text.secondary">{label}</Typography><Typography>{value}</Typography></Box>
}

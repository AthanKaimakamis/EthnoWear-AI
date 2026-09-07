import { Box, Card, CardActionArea, CardContent, Chip, Stack, Typography } from '@mui/material'
import CollectionsOutlinedIcon from '@mui/icons-material/CollectionsOutlined'
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined'
import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getEntityDetails } from '../../../api/CatalogueApi'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { conceptPath } from '../../../app/archiveRoutes'
import { archiveMediaUrl } from '../../../api/PublicArchiveApi'
import type { OntologyFeatureType } from '../../../types/catalogue'
import type { ReferenceResource } from '../../../types/reference'

type Props = {
    item: ReferenceResource
    entityType: OntologyFeatureType
}

export default function CatalogueConceptCard({ item, entityType }: Props) {
    const { t, i18n } = useTranslation()
    const cardRef = useRef<HTMLDivElement>(null)
    const [visible, setVisible] = useState(false)
    const [failed, setFailed] = useState<string[]>([])
    useEffect(() => {
        const observer = new IntersectionObserver(entries => {
            if (entries.some(entry => entry.isIntersecting)) { setVisible(true); observer.disconnect() }
        }, { rootMargin: '200px' })
        if (cardRef.current) observer.observe(cardRef.current)
        return () => observer.disconnect()
    }, [])
    const previews = useQuery({
        queryKey: ['archive', 'concept-card-previews', entityType, item.localName, i18n.resolvedLanguage],
        enabled: visible && (item.evidenceCount ?? 0) > 1,
        queryFn: ({ signal }) => getEntityDetails(entityType, item.localName, i18n.resolvedLanguage, { size: 4 }, signal),
        staleTime: 300_000,
    })
    const imageUrls = [...new Set([
        item.representativeMediaAssetId ? archiveMediaUrl(item.representativeMediaAssetId) : item.imageUrl,
        ...(previews.data?.evidence.content ?? []).map(evidence => evidence.previewMedia && ['IMAGE', 'SCAN', 'THUMBNAIL'].includes(evidence.previewMedia.mediaType) ? archiveMediaUrl(evidence.previewMedia.mediaAssetId) : null),
    ].filter((url): url is string => Boolean(url)))].filter(url => !failed.includes(url))
    const shown = imageUrls.slice(0, 3)

    return (
        <Card ref={cardRef} sx={{ height: '100%', minWidth: 0, display: 'flex', flexDirection: 'column', borderRadius: 1, overflow: 'hidden', border: 1, borderColor: 'divider' }}>
            <CardActionArea component={Link} to={conceptPath(entityType, item.localName)} sx={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'stretch' }}>
                <Box sx={{ width: '100%', aspectRatio: '3 / 2', flexShrink: 0, position: 'relative', display: 'grid', gridTemplateColumns: shown.length > 1 ? '1.6fr 1fr' : '1fr', gridTemplateRows: shown.length === 3 ? '1fr 1fr' : '1fr', gap: '3px', bgcolor: '#eef0f2', overflow: 'hidden', borderBottom: 1, borderColor: 'divider' }}>
                    {shown.length === 0 ? <Box sx={{ display: 'grid', placeItems: 'center', color: '#929a9e' }}><ImageOutlinedIcon sx={{ fontSize: 48 }} /></Box> : shown.map((url, index) => <Box component="img" key={url} src={url} alt={item.label} loading="lazy" onError={() => setFailed(current => [...current, url])} sx={{ width: '100%', height: '100%', minHeight: 0, minWidth: 0, objectFit: 'cover', gridRow: shown.length === 3 && index === 0 ? '1 / 3' : undefined }} />)}
                    {shown.length > 1 && <Box sx={{ position: 'absolute', right: 8, bottom: 8, display: 'flex', alignItems: 'center', gap: .5, px: 1, py: .5, bgcolor: 'rgba(0,0,0,.72)', color: '#fff', borderRadius: 1 }}><CollectionsOutlinedIcon sx={{ fontSize: 16 }} /><Typography variant="caption">{imageUrls.length > 3 ? `+${imageUrls.length - 3}` : imageUrls.length}</Typography></Box>}
                </Box>
                <CardContent sx={{ flexGrow: 1 }}>
                    <Stack spacing={1}>
                        <Typography variant="h6" sx={{ fontWeight: 700, fontSize: 18, lineHeight: 1.35, minHeight: '2.7em', overflowWrap: 'anywhere' }}>{item.label || item.localName}</Typography>
                        {item.comment && <Typography variant="body2" color="text.secondary" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{item.comment}</Typography>}
                        <Chip
                            size="small"
                            variant="outlined"
                            label={t('archiveReference.evidenceCount', { count: item.evidenceCount ?? 0 })}
                            sx={{ alignSelf: 'flex-start', maxWidth: '100%', height: 'auto', '& .MuiChip-label': { whiteSpace: 'normal', py: .5 } }}
                        />
                    </Stack>
                </CardContent>
            </CardActionArea>
        </Card>
    )
}

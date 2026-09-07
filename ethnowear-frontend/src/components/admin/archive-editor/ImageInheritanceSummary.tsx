import { Alert, Stack, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { mediaEntityLinksApi } from '../../../api/ArchiveAdminApi'
import type { InheritedObservation, MediaEntityLinkDetails } from '../../../types/archive'
import type { ReferenceData } from '../../../types/reference'
import type { MediaDraft } from './ArchiveEditorSections'
import InheritedObservations from '../../archive/InheritedObservations'

export function inheritImageObservations(media: MediaDraft[], links: MediaEntityLinkDetails[]): InheritedObservation[] {
    const assets = new Map(media.map(value => [value.asset.id, value.asset]))
    const grouped = new Map<string, InheritedObservation>()
    for (const link of links) {
        const asset = assets.get(link.mediaAssetId)
        if (!asset || !['ORNAMENT', 'TECHNIQUE', 'COLOR'].includes(link.entityType)) continue
        const key = `${link.entityType}:${link.ontologyIri}`
        const observation = grouped.get(key) ?? { featureType: link.entityType as InheritedObservation['featureType'], ontologyIri: link.ontologyIri, ontologyLocalName: link.ontologyLocalName, origins: [] }
        if (!observation.origins.some(origin => origin.mediaAssetId === asset.id)) observation.origins.push({ mediaAssetId: asset.id, fileName: asset.fileName, sourceReferenceId: asset.sourceReferenceId })
        grouped.set(key, observation)
    }
    return [...grouped.values()]
}

export default function ImageInheritanceSummary({ media, reference, citationLabel }: {
    media: MediaDraft[]; reference: ReferenceData; citationLabel: (id: number) => string
}) {
    const { i18n } = useTranslation()
    const en = i18n.resolvedLanguage === 'en'
    const query = useQuery({ queryKey: ['admin', 'media-entity-links', 'inheritance'], enabled: media.length > 0,
        queryFn: async ({ signal }) => {
            const links: MediaEntityLinkDetails[] = []
            for (let page = 0; ; page++) {
                const result = await mediaEntityLinksApi.findAll({ page, size: 100, sort: 'id,asc' }, signal)
                links.push(...result.content)
                if (result.content.length < 100 || page + 1 >= result.totalPages) return links
            }
        },
    })
    if (!media.length) return null
    const labels = new Map([...reference.ornaments, ...reference.techniques, ...reference.colors].map(value => [value.localName, value.label]))
    return <Stack spacing={1.5}>
        {query.isError && <Alert severity="error">{en ? 'Image observations could not be loaded.' : 'Наблюденията от изображенията не могат да бъдат заредени.'}</Alert>}
        <InheritedObservations observations={inheritImageObservations(media, query.data ?? [])} labels={labels} />
        {media.filter(value => value.asset.sourceReferenceId).map(value => <Typography key={value.asset.id} variant="body2" color="text.secondary">{value.asset.fileName}: {citationLabel(value.asset.sourceReferenceId!)}</Typography>)}
    </Stack>
}

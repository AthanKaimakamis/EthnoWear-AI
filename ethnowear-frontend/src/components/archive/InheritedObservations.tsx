import { Box, Chip, Stack, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { conceptPath } from '../../app/archiveRoutes'
import type { InheritedObservation } from '../../types/archive'

export default function InheritedObservations({ observations, labels }: {
    observations: InheritedObservation[]
    labels: Map<string, string>
}) {
    const { i18n } = useTranslation()
    const en = i18n.resolvedLanguage === 'en'
    if (!observations.length) return null
    return <Box component="section">
        <Typography variant="h6" sx={{ mb: 1 }}>{en ? 'From linked images' : 'От свързаните изображения'}</Typography>
        <Stack spacing={1}>
            {observations.map(observation => <Box key={`${observation.featureType}:${observation.ontologyIri}`}>
                <Chip component={Link} clickable to={conceptPath(observation.featureType, observation.ontologyLocalName)}
                    label={labels.get(observation.ontologyLocalName) ?? (en ? 'Ontology observation' : 'Онтологично наблюдение')} variant="outlined" size="small" />
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    {observation.origins.map(origin => origin.fileName ?? (en ? 'Image' : 'Изображение')).join(', ')}
                </Typography>
            </Box>)}
        </Stack>
    </Box>
}

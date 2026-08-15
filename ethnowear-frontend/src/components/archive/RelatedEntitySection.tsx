import { Box, Button, Stack, Typography } from '@mui/material'
import ArrowForwardIcon from '@mui/icons-material/ArrowForward'
import { Link } from 'react-router'
import { conceptPath } from '../../app/archiveRoutes'
import type { EntityLinkDetails, OntologyFeatureType } from '../../types/catalogue'

type Props = {
    title: string
    entityType: OntologyFeatureType
    items: EntityLinkDetails[]
}

function RelatedEntitySection({ title, entityType, items }: Props) {
    if (items.length === 0) return null

    return (
        <Box component="section">
            <Stack spacing={1.5}>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>
                    {title}
                </Typography>

                <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                    {items.map((item) => (
                        <Button
                            key={`${item.entityType}:${item.localName}`}
                            component={Link}
                            to={conceptPath(entityType, item.localName)}
                            variant="outlined"
                            endIcon={<ArrowForwardIcon fontSize="small" />}
                            sx={{ textTransform: 'none' }}
                        >
                            {item.label}
                        </Button>
                    ))}
                </Box>
            </Stack>
        </Box>
    )
}

export default RelatedEntitySection

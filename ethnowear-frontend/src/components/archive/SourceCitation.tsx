import { Box, Link, Stack, Typography } from '@mui/material'
import MenuBookOutlinedIcon from '@mui/icons-material/MenuBookOutlined'
import type { EntitySourceCitationDetails } from '../../types/archive'

type Props = {
    source: EntitySourceCitationDetails | null
}

function SourceCitation({ source }: Props) {
    if (!source) return null
    const locator = [
        source.chapter,
        source.sectionTitle,
        source.pageFrom === null
            ? null
            : source.pageTo && source.pageTo !== source.pageFrom
                ? `${source.pageFrom}-${source.pageTo}`
                : String(source.pageFrom),
        source.figureNumber,
    ].filter(Boolean).join(', ')

    const href = source.referenceUrl ?? source.url

    return (
        <Box sx={{ borderLeft: 3, borderColor: 'primary.main', pl: 2, py: 0.5 }}>
            <Stack direction="row" spacing={1.25} sx={{ alignItems: 'flex-start' }}>
                <MenuBookOutlinedIcon color="primary" fontSize="small" sx={{ mt: 0.25 }} />
                <Box>
                    <Typography sx={{ fontWeight: 700 }}>
                        {href ? (
                            <Link href={href} target="_blank" rel="noreferrer" color="inherit">
                                {source.title}
                            </Link>
                        ) : source.title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        {[source.author, source.publisher, source.year, locator].filter(Boolean).join(' · ')}
                    </Typography>
                    {source.note && <Typography variant="body2" sx={{ mt: 0.5 }}>{source.note}</Typography>}
                </Box>
            </Stack>
        </Box>
    )
}

export default SourceCitation

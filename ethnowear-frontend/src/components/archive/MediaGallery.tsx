import { Box, Card, CardContent, CardMedia, Stack, Typography } from '@mui/material'
import { archiveMediaUrl } from '../../api/PublicArchiveApi'

export type MediaGalleryItem = {
    mediaAssetId: number
    title: string
    caption: string | null
    mimeType: string | null
}

type Props = {
    title: string
    items: MediaGalleryItem[]
}

function MediaGallery({ title, items }: Props) {
    if (items.length === 0) return null

    return (
        <Box component="section">
            <Stack spacing={1.5}>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>
                    {title}
                </Typography>

                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(3, minmax(0, 1fr))' },
                        gap: 2,
                    }}
                >
                    {items.map((item) => (
                        <Card key={item.mediaAssetId} sx={{ overflow: 'hidden' }}>
                            {item.mimeType?.startsWith('image/') ? (
                                <CardMedia
                                    component="img"
                                    image={archiveMediaUrl(item.mediaAssetId)}
                                    alt={item.caption ?? item.title}
                                    sx={{ height: 220, objectFit: 'cover' }}
                                />
                            ) : (
                                <Box
                                    component="a"
                                    href={archiveMediaUrl(item.mediaAssetId)}
                                    target="_blank"
                                    rel="noreferrer"
                                    sx={{ display: 'grid', placeItems: 'center', height: 160, color: 'primary.main' }}
                                >
                                    {item.title}
                                </Box>
                            )}
                            {item.caption && (
                                <CardContent>
                                    <Typography variant="body2">{item.caption}</Typography>
                                </CardContent>
                            )}
                        </Card>
                    ))}
                </Box>
            </Stack>
        </Box>
    )
}

export default MediaGallery

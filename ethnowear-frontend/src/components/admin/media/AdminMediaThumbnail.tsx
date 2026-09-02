import { useState } from 'react'
import { Box, Skeleton, type SxProps, type Theme } from '@mui/material'
import ImageNotSupportedOutlinedIcon from '@mui/icons-material/ImageNotSupportedOutlined'
import { useAdminMediaContent } from './useAdminMediaContent'

type Props = {
    mediaAssetId: number | null | undefined
    alt?: string
    objectFit?: 'cover' | 'contain'
    sx?: SxProps<Theme>
}

export default function AdminMediaThumbnail({ mediaAssetId, alt = '', objectFit = 'cover', sx }: Props) {
    if (!mediaAssetId) return <MediaFallback sx={sx} />
    return <LoadedAdminMediaThumbnail mediaAssetId={mediaAssetId} alt={alt} objectFit={objectFit} sx={sx} />
}

function LoadedAdminMediaThumbnail({ mediaAssetId, alt, objectFit, sx }: { mediaAssetId: number, alt: string, objectFit: 'cover' | 'contain', sx?: SxProps<Theme> }) {
    const content = useAdminMediaContent(mediaAssetId)
    const [failed, setFailed] = useState(false)

    if (failed || content.isError) return <MediaFallback sx={sx} />
    return <Box sx={{ position: 'relative', overflow: 'hidden', bgcolor: 'grey.100', display: 'grid', placeItems: 'center', ...sx }}>
        {content.isPending && <Skeleton variant="rectangular" animation="wave" sx={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }} />}
        {content.url && <Box component="img" src={content.url} alt={alt} onError={() => setFailed(true)} sx={{ width: '100%', height: '100%', objectFit, display: 'block' }} />}
    </Box>
}

function MediaFallback({ sx }: Pick<Props, 'sx'>) {
    return <Box sx={{ bgcolor: 'grey.100', display: 'grid', placeItems: 'center', ...sx }}><ImageNotSupportedOutlinedIcon color="action" /></Box>
}

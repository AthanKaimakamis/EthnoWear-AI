import { useEffect, useState, type ComponentProps } from 'react'
import {
    Box,
    ButtonBase,
    Dialog,
    DialogContent,
    DialogTitle,
    IconButton,
    Stack,
    Tooltip,
    Typography,
} from '@mui/material'
import type { SxProps, Theme } from '@mui/material/styles'
import CloseIcon from '@mui/icons-material/Close'
import FitScreenIcon from '@mui/icons-material/FitScreen'
import OpenInNewIcon from '@mui/icons-material/OpenInNew'
import ZoomInIcon from '@mui/icons-material/ZoomIn'
import ZoomOutIcon from '@mui/icons-material/ZoomOut'
import { useTranslation } from 'react-i18next'

type Props = {
    open: boolean
    src: string
    alt: string
    caption?: string | null
    onClose: () => void
}

type PreviewableImageProps = {
    src: string
    alt: string
    caption?: string | null
    imageSx?: SxProps<Theme>
    buttonSx?: SxProps<Theme>
    loading?: 'eager' | 'lazy'
}

const MIN_ZOOM = 0.5
const MAX_ZOOM = 3
const ZOOM_STEP = 0.25

export default function ImageViewerDialog({ open, src, alt, caption, onClose }: Props) {
    const { t } = useTranslation()
    const [zoom, setZoom] = useState(1)

    useEffect(() => {
        if (open) setZoom(1)
    }, [open, src])

    return (
        <Dialog
            open={open}
            onClose={onClose}
            fullWidth
            maxWidth="xl"
            slotProps={{ paper: { sx: { height: { xs: '100%', sm: '92vh' }, maxHeight: { xs: '100%', sm: '92vh' }, m: { xs: 0, sm: 2 } } } }}
        >
            <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 1.25, pr: 1 }}>
                <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Typography component="span" sx={{ display: 'block', fontWeight: 800 }} noWrap>{alt}</Typography>
                    {caption && <Typography component="span" variant="body2" color="text.secondary" noWrap>{caption}</Typography>}
                </Box>
                <Stack
                    direction="row"
                    sx={{ border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden', flexShrink: 0, '& .MuiIconButton-root': { borderRadius: 0 } }}
                >
                    <ViewerButton label={t('imageViewer.zoomOut')} disabled={zoom <= MIN_ZOOM} onClick={() => setZoom(value => Math.max(MIN_ZOOM, value - ZOOM_STEP))}>
                        <ZoomOutIcon />
                    </ViewerButton>
                    <Typography sx={{ minWidth: 62, display: 'grid', placeItems: 'center', borderInline: 1, borderColor: 'divider', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
                        {Math.round(zoom * 100)}%
                    </Typography>
                    <ViewerButton label={t('imageViewer.zoomIn')} disabled={zoom >= MAX_ZOOM} onClick={() => setZoom(value => Math.min(MAX_ZOOM, value + ZOOM_STEP))}>
                        <ZoomInIcon />
                    </ViewerButton>
                    <ViewerButton label={t('imageViewer.fit')} disabled={zoom === 1} onClick={() => setZoom(1)}>
                        <FitScreenIcon />
                    </ViewerButton>
                    <Tooltip title={t('imageViewer.openOriginal')}>
                        <IconButton
                            component="a"
                            href={src}
                            target="_blank"
                            rel="noreferrer"
                            aria-label={t('imageViewer.openOriginal')}
                        >
                            <OpenInNewIcon />
                        </IconButton>
                    </Tooltip>
                </Stack>
                <Tooltip title={t('imageViewer.close')}>
                    <IconButton onClick={onClose} aria-label={t('imageViewer.close')}><CloseIcon /></IconButton>
                </Tooltip>
            </DialogTitle>
            <DialogContent dividers sx={{ p: 0, overflow: 'auto', bgcolor: '#E6E8E7' }}>
                <Box sx={{ width: `${zoom * 100}%`, minWidth: '100%', minHeight: '100%', mx: 'auto', display: 'grid', placeItems: 'center', p: { xs: 1, sm: 2 } }}>
                    <Box component="img" src={src} alt={alt} sx={{ display: 'block', width: '100%', height: 'auto', maxHeight: zoom === 1 ? 'calc(92vh - 92px)' : 'none', objectFit: 'contain' }} />
                </Box>
            </DialogContent>
        </Dialog>
    )
}

export function PreviewableImage({
    src,
    alt,
    caption,
    imageSx,
    buttonSx,
    loading = 'eager',
}: PreviewableImageProps) {
    const { t } = useTranslation()
    const [open, setOpen] = useState(false)

    return (
        <>
            <ButtonBase
                onClick={() => setOpen(true)}
                aria-label={t('imageViewer.open', { title: alt })}
                sx={{ display: 'block', cursor: 'zoom-in', ...buttonSx }}
            >
                <Box component="img" src={src} alt={alt} loading={loading} sx={{ display: 'block', ...imageSx }} />
            </ButtonBase>
            <ImageViewerDialog open={open} src={src} alt={alt} caption={caption} onClose={() => setOpen(false)} />
        </>
    )
}

type ViewerButtonProps = ComponentProps<typeof IconButton> & {
    label: string
}

function ViewerButton({ label, ...props }: ViewerButtonProps) {
    return <Tooltip title={label}><span><IconButton aria-label={label} {...props} /></span></Tooltip>
}

import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import {
    Alert, Box, Button, Divider, IconButton, LinearProgress, Paper, Stack, Tooltip, Typography,
} from '@mui/material'
import NavigateBeforeIcon from '@mui/icons-material/NavigateBefore'
import NavigateNextIcon from '@mui/icons-material/NavigateNext'
import ZoomInIcon from '@mui/icons-material/ZoomIn'
import ZoomOutIcon from '@mui/icons-material/ZoomOut'
import FitScreenIcon from '@mui/icons-material/FitScreen'
import RotateLeftIcon from '@mui/icons-material/RotateLeft'
import RotateRightIcon from '@mui/icons-material/RotateRight'
import DownloadIcon from '@mui/icons-material/Download'
import OpenInNewIcon from '@mui/icons-material/OpenInNew'
import ViewSidebarOutlinedIcon from '@mui/icons-material/ViewSidebarOutlined'
import { useTranslation } from 'react-i18next'
import { Document, Page, Thumbnail, pdfjs } from 'react-pdf'
import 'react-pdf/dist/Page/AnnotationLayer.css'
import 'react-pdf/dist/Page/TextLayer.css'
import AdminModal from './AdminModal'

pdfjs.GlobalWorkerOptions.workerSrc = new URL(
    'pdfjs-dist/build/pdf.worker.min.mjs',
    import.meta.url,
).toString()

export type PdfViewerContentProps = {
    source: File | string
    title: string
    onClose: () => void
    downloadName?: string
}

const MIN_ZOOM = .5
const MAX_ZOOM = 2.5
const ZOOM_STEP = .25

export default function PdfViewerContent({ source, title, onClose, downloadName }: PdfViewerContentProps) {
    const { t } = useTranslation()
    const [pageNumber, setPageNumber] = useState(1)
    const [pageCount, setPageCount] = useState(0)
    const [zoom, setZoom] = useState(1)
    const [rotation, setRotation] = useState(0)
    const [loadFailed, setLoadFailed] = useState(false)
    const [viewportWidth, setViewportWidth] = useState(() => typeof window === 'undefined' ? 900 : window.innerWidth)
    const [thumbnailsOpen, setThumbnailsOpen] = useState(() => typeof window !== 'undefined' && window.innerWidth >= 900)
    const thumbnailsRef = useRef<HTMLDivElement>(null)

    const objectUrl = useMemo(() => source instanceof File ? URL.createObjectURL(source) : null, [source])

    useEffect(() => () => {
        if (objectUrl) URL.revokeObjectURL(objectUrl)
    }, [objectUrl])

    useEffect(() => {
        const resize = () => setViewportWidth(window.innerWidth)
        window.addEventListener('resize', resize)
        return () => window.removeEventListener('resize', resize)
    }, [])

    const resolvedSource = source instanceof File ? objectUrl : source
    const basePageWidth = Math.max(280, Math.min(900, viewportWidth - (viewportWidth < 600 ? 56 : 120)))
    function changeZoom(next: number) {
        setZoom(Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, next)))
    }

    function changePage(next: number) {
        setPageNumber(Math.min(pageCount, Math.max(1, next)))
    }

    useEffect(() => {
        if (!thumbnailsOpen) return
        const selected = thumbnailsRef.current?.querySelector<HTMLElement>(`[data-pdf-thumbnail="${pageNumber}"]`)
        selected?.scrollIntoView?.({ block: 'nearest' })
    }, [pageNumber, thumbnailsOpen])

    return (
        <AdminModal
            open
            onClose={onClose}
            maxWidth="xl"
            title={title}
            description={t('pdfViewer.description')}
            actions={<Button onClick={onClose}>{t('curator.actions.close')}</Button>}
        >
            <Box
                tabIndex={0}
                onKeyDown={event => {
                    if (event.key === 'ArrowLeft') changePage(pageNumber - 1)
                    if (event.key === 'ArrowRight') changePage(pageNumber + 1)
                }}
                sx={{ height: { xs: '70vh', md: '76vh' }, minHeight: 420, display: 'flex', flexDirection: 'column', outline: 0 }}
            >
                <Paper square variant="outlined" sx={{ position: 'sticky', top: 0, zIndex: 2, borderTop: 0, borderLeft: 0, borderRight: 0, bgcolor: 'background.paper' }}>
                    <Stack direction="row" sx={{ minHeight: 52, px: 1, alignItems: 'center', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
                        <Stack direction="row" sx={{ alignItems: 'center' }}>
                            <ViewerButton label={t('pdfViewer.toggleThumbnails')} onClick={() => setThumbnailsOpen(value => !value)}><ViewSidebarOutlinedIcon /></ViewerButton>
                            <Divider orientation="vertical" flexItem sx={{ mx: .5 }} />
                            <ViewerButton label={t('pdfViewer.previous')} disabled={pageNumber <= 1} onClick={() => changePage(pageNumber - 1)}><NavigateBeforeIcon /></ViewerButton>
                            <Typography variant="body2" sx={{ minWidth: 92, textAlign: 'center', fontVariantNumeric: 'tabular-nums' }}>
                                {pageCount ? t('pdfViewer.pageCount', { page: pageNumber, count: pageCount }) : t('pdfViewer.loading')}
                            </Typography>
                            <ViewerButton label={t('pdfViewer.next')} disabled={!pageCount || pageNumber >= pageCount} onClick={() => changePage(pageNumber + 1)}><NavigateNextIcon /></ViewerButton>
                        </Stack>

                        <Stack direction="row" sx={{ alignItems: 'center' }}>
                            <ViewerButton label={t('pdfViewer.zoomOut')} disabled={zoom <= MIN_ZOOM} onClick={() => changeZoom(zoom - ZOOM_STEP)}><ZoomOutIcon /></ViewerButton>
                            <Typography variant="body2" sx={{ minWidth: 52, textAlign: 'center', fontVariantNumeric: 'tabular-nums' }}>{Math.round(zoom * 100)}%</Typography>
                            <ViewerButton label={t('pdfViewer.zoomIn')} disabled={zoom >= MAX_ZOOM} onClick={() => changeZoom(zoom + ZOOM_STEP)}><ZoomInIcon /></ViewerButton>
                            <ViewerButton label={t('pdfViewer.fit')} disabled={zoom === 1} onClick={() => setZoom(1)}><FitScreenIcon /></ViewerButton>
                            <Divider orientation="vertical" flexItem sx={{ mx: .5 }} />
                            <ViewerButton label={t('pdfViewer.rotateLeft')} onClick={() => setRotation(value => (value + 270) % 360)}><RotateLeftIcon /></ViewerButton>
                            <ViewerButton label={t('pdfViewer.rotateRight')} onClick={() => setRotation(value => (value + 90) % 360)}><RotateRightIcon /></ViewerButton>
                        </Stack>

                        <Stack direction="row" sx={{ alignItems: 'center' }}>
                            {resolvedSource && <ViewerLink label={t('pdfViewer.download')} href={resolvedSource} download={downloadName ?? title}><DownloadIcon /></ViewerLink>}
                            {resolvedSource && <ViewerLink label={t('pdfViewer.open')} href={resolvedSource}><OpenInNewIcon /></ViewerLink>}
                        </Stack>
                    </Stack>
                </Paper>

                <Box sx={{ flex: 1, minHeight: 0, overflow: 'hidden', bgcolor: '#E8E9EB', '& > .react-pdf__Document': { height: '100%' } }}>
                    {loadFailed && <Alert severity="error" sx={{ maxWidth: 720, mx: 'auto' }}>{t('pdfViewer.loadFailed')}</Alert>}
                    {resolvedSource && !loadFailed && <Document
                        file={resolvedSource}
                        loading={<LinearProgress sx={{ maxWidth: 720, mx: 'auto', mt: 3 }} />}
                        onLoadSuccess={({ numPages }) => { setPageCount(numPages); setPageNumber(current => Math.min(current, numPages)) }}
                        onLoadError={() => setLoadFailed(true)}
                    >
                        <Box sx={{ height: '100%', display: 'flex', minWidth: 0 }}>
                            {thumbnailsOpen && <Box ref={thumbnailsRef} component="nav" aria-label={t('pdfViewer.thumbnails')} sx={{ width: { xs: 128, sm: 168 }, flexShrink: 0, overflowY: 'auto', bgcolor: 'background.paper', borderRight: 1, borderColor: 'divider', p: 1 }}>
                                <Stack spacing={1}>
                                    {Array.from({ length: pageCount }, (_, index) => {
                                        const thumbnailPage = index + 1
                                        return <LazyThumbnail key={thumbnailPage} pageNumber={thumbnailPage} selected={thumbnailPage === pageNumber} onSelect={changePage} />
                                    })}
                                </Stack>
                            </Box>}
                            <Box sx={{ flex: 1, minWidth: 0, overflow: 'auto', p: { xs: 1, md: 2 } }}>
                                <Box sx={{ width: 'max-content', minWidth: '100%', display: 'flex', justifyContent: 'center' }}>
                                    <Page
                                        pageNumber={pageNumber}
                                        width={basePageWidth * zoom}
                                        rotate={rotation}
                                        loading={<LinearProgress sx={{ width: Math.min(basePageWidth, 720), mt: 3 }} />}
                                        canvasBackground="white"
                                    />
                                </Box>
                            </Box>
                        </Box>
                    </Document>}
                </Box>
            </Box>
        </AdminModal>
    )
}

function LazyThumbnail({ pageNumber, selected, onSelect }: { pageNumber: number; selected: boolean; onSelect: (page: number) => void }) {
    const { t } = useTranslation()
    const containerRef = useRef<HTMLDivElement>(null)
    const [visible, setVisible] = useState(() => typeof IntersectionObserver === 'undefined')

    useEffect(() => {
        if (visible || !containerRef.current || typeof IntersectionObserver === 'undefined') return
        const observer = new IntersectionObserver(entries => {
            if (entries.some(entry => entry.isIntersecting)) {
                setVisible(true)
                observer.disconnect()
            }
        }, { rootMargin: '240px 0px' })
        observer.observe(containerRef.current)
        return () => observer.disconnect()
    }, [visible])

    const label = t('pdfViewer.thumbnailPage', { page: pageNumber })
    return <Box
        ref={containerRef}
        role="button"
        tabIndex={0}
        aria-label={label}
        aria-current={selected ? 'page' : undefined}
        data-pdf-thumbnail={pageNumber}
        onClick={() => onSelect(pageNumber)}
        onKeyDown={event => {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault()
                onSelect(pageNumber)
            }
        }}
        sx={{ minHeight: 142, p: .75, display: 'grid', placeItems: 'center', cursor: 'pointer', border: 2, borderColor: selected ? 'primary.main' : 'transparent', bgcolor: selected ? 'action.selected' : 'transparent', '&:hover': { bgcolor: 'action.hover' }, '&:focus-visible': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: -2 }, '& canvas': { maxWidth: '100%', height: 'auto !important' } }}
    >
        {visible ? <Stack spacing={.5} sx={{ alignItems: 'center', maxWidth: '100%' }}><Thumbnail pageNumber={pageNumber} width={112} /><Typography variant="caption">{pageNumber}</Typography></Stack> : <Box sx={{ width: 96, height: 128, bgcolor: 'action.hover' }} />}
    </Box>
}

function ViewerButton({ label, disabled = false, onClick, children }: { label: string; disabled?: boolean; onClick: () => void; children: ReactNode }) {
    return <Tooltip title={label}><span><IconButton aria-label={label} disabled={disabled} onClick={onClick}>{children}</IconButton></span></Tooltip>
}

function ViewerLink({ label, href, download, children }: { label: string; href: string; download?: string; children: ReactNode }) {
    return <Tooltip title={label}><IconButton component="a" aria-label={label} href={href} download={download} target={download ? undefined : '_blank'} rel={download ? undefined : 'noreferrer'}>{children}</IconButton></Tooltip>
}

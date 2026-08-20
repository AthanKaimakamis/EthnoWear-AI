import { lazy, Suspense } from 'react'
import { Box, CircularProgress } from '@mui/material'
import type { PdfViewerContentProps } from './PdfViewerContent'

const PdfViewerContent = lazy(() => import('./PdfViewerContent'))

type Props = PdfViewerContentProps & {
    open: boolean
}

export default function PdfViewerDialog({ open, ...props }: Props) {
    if (!open) return null

    return (
        <Suspense fallback={<Box sx={{ position: 'fixed', inset: 0, zIndex: theme => theme.zIndex.modal + 1, display: 'grid', placeItems: 'center', bgcolor: 'rgba(0, 0, 0, .28)' }}><CircularProgress /></Box>}>
            <PdfViewerContent {...props} />
        </Suspense>
    )
}

import { useEffect, useRef } from 'react'
import { Box, CircularProgress } from '@mui/material'

type Props = {
    enabled: boolean
    loading: boolean
    onLoadMore: () => void
}

export default function InfiniteScrollTrigger({ enabled, loading, onLoadMore }: Props) {
    const triggerRef = useRef<HTMLDivElement | null>(null)

    useEffect(() => {
        const target = triggerRef.current
        if (!target || !enabled) return
        const observer = new IntersectionObserver(entries => {
            if (entries.some(entry => entry.isIntersecting)) onLoadMore()
        }, { rootMargin: '320px' })
        observer.observe(target)
        return () => observer.disconnect()
    }, [enabled, onLoadMore])

    return <Box ref={triggerRef} sx={{ minHeight: 48, display: 'grid', placeItems: 'center' }}>
        {loading && <CircularProgress size={24} />}
    </Box>
}

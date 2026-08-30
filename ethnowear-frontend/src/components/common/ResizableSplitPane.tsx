import { useRef, useState, type PointerEvent, type ReactNode } from 'react'
import { Box } from '@mui/material'

type Props = {
    first: ReactNode
    second: ReactNode
    label: string
    initialPercent?: number
    minPercent?: number
    maxPercent?: number
}

export default function ResizableSplitPane({
    first,
    second,
    label,
    initialPercent = 40,
    minPercent = 28,
    maxPercent = 58,
}: Props) {
    const rootRef = useRef<HTMLDivElement>(null)
    const [firstPercent, setFirstPercent] = useState(initialPercent)

    function clamp(value: number) {
        return Math.min(maxPercent, Math.max(minPercent, value))
    }

    function startResize(event: PointerEvent<HTMLDivElement>) {
        const root = rootRef.current
        if (!root) return
        event.preventDefault()
        event.currentTarget.setPointerCapture(event.pointerId)

        function resize(clientX: number) {
            const bounds = root!.getBoundingClientRect()
            if (bounds.width <= 0) return
            setFirstPercent(clamp(((clientX - bounds.left) / bounds.width) * 100))
        }

        function onMove(moveEvent: globalThis.PointerEvent) {
            resize(moveEvent.clientX)
        }

        function stopResize() {
            window.removeEventListener('pointermove', onMove)
            window.removeEventListener('pointerup', stopResize)
        }

        window.addEventListener('pointermove', onMove)
        window.addEventListener('pointerup', stopResize)
    }

    function resizeWithKeyboard(direction: number) {
        setFirstPercent(value => clamp(value + direction * 2))
    }

    return <Box
        ref={rootRef}
        sx={{
            minHeight: 0,
            display: 'grid',
            gridTemplateColumns: {
                xs: 'minmax(0, 1fr)',
                md: `minmax(280px, ${firstPercent}fr) 8px minmax(520px, ${100 - firstPercent}fr)`,
            },
            gap: { xs: 1, md: 0 },
        }}
    >
        <Box sx={{ minWidth: 0, minHeight: 0 }}>{first}</Box>
        <Box
            role="separator"
            aria-label={label}
            aria-orientation="vertical"
            aria-valuemin={minPercent}
            aria-valuemax={maxPercent}
            aria-valuenow={Math.round(firstPercent)}
            tabIndex={0}
            onPointerDown={startResize}
            onKeyDown={event => {
                if (event.key === 'ArrowLeft') { event.preventDefault(); resizeWithKeyboard(-1) }
                if (event.key === 'ArrowRight') { event.preventDefault(); resizeWithKeyboard(1) }
            }}
            sx={{
                display: { xs: 'none', md: 'block' },
                position: 'relative',
                cursor: 'col-resize',
                touchAction: 'none',
                outline: 0,
                '&::after': {
                    content: '""',
                    position: 'absolute',
                    top: 0,
                    bottom: 0,
                    left: '3px',
                    width: '2px',
                    bgcolor: 'divider',
                    transition: theme => theme.transitions.create(['background-color', 'width']),
                },
                '&:hover::after, &:focus-visible::after': {
                    left: '2px',
                    width: '4px',
                    bgcolor: 'primary.main',
                },
            }}
        />
        <Box sx={{ minWidth: 0, minHeight: 0 }}>{second}</Box>
    </Box>
}

import { useMemo, useRef, useState, type ReactNode, type Ref, type UIEvent } from 'react'
import { Box, Button, Chip, IconButton, Paper, Stack, Tooltip, Typography } from '@mui/material'
import CompareArrowsOutlinedIcon from '@mui/icons-material/CompareArrowsOutlined'
import ContentCopyOutlinedIcon from '@mui/icons-material/ContentCopyOutlined'
import DoneOutlinedIcon from '@mui/icons-material/DoneOutlined'
import { diffWordsWithSpace, type Change } from 'diff'

type Props = {
    original: string
    suggested: string
    originalTitle: string
    suggestedTitle: string
    synchronizedLabel: string
    changesLabel: (count: number) => string
    fillAvailable?: boolean
    layout?: 'split' | 'unified'
    copySuggestedLabel: string
    copiedSuggestedLabel: string
    applySuggestedLabel?: string
    onApplySuggested?: () => void
}

export default function SynchronizedTextDiff({ original, suggested, originalTitle, suggestedTitle, synchronizedLabel, changesLabel, fillAvailable = false, layout = 'split', copySuggestedLabel, copiedSuggestedLabel, applySuggestedLabel, onApplySuggested }: Props) {
    const leftRef = useRef<HTMLDivElement>(null)
    const rightRef = useRef<HTMLDivElement>(null)
    const pendingProgrammaticScroll = useRef<{
        element: HTMLDivElement
        top: number
    } | null>(null)
    const [copied, setCopied] = useState(false)
    const changes = useMemo(() => diffWordsWithSpace(original, suggested), [original, suggested])
    const changedParts = changes.filter(change => change.added || change.removed).length

    function synchronize(source: HTMLDivElement, target: HTMLDivElement | null) {
        const pending = pendingProgrammaticScroll.current
        if (pending?.element === source) {
            pendingProgrammaticScroll.current = null
            if (Math.abs(source.scrollTop - pending.top) < 1) return
        }

        if (!target) return

        const sourceRange = Math.max(0, source.scrollHeight - source.clientHeight)
        const targetRange = Math.max(0, target.scrollHeight - target.clientHeight)
        const ratio = sourceRange > 0 ? source.scrollTop / sourceRange : 0
        const nextTop = Math.min(targetRange, Math.max(0, ratio * targetRange))

        if (Math.abs(target.scrollTop - nextTop) < 1) return

        pendingProgrammaticScroll.current = { element: target, top: nextTop }
        target.scrollTop = nextTop
    }

    async function copySuggested() {
        await navigator.clipboard.writeText(suggested)
        setCopied(true)
        window.setTimeout(() => setCopied(false), 1800)
    }

    const suggestedActions = <Stack direction="row" spacing={.5} sx={{ alignItems: 'center' }}>
        {onApplySuggested && applySuggestedLabel && <Button size="small" variant="outlined" onClick={onApplySuggested}>{applySuggestedLabel}</Button>}
        <Tooltip title={copied ? copiedSuggestedLabel : copySuggestedLabel}>
            <IconButton size="small" aria-label={copied ? copiedSuggestedLabel : copySuggestedLabel} onClick={() => void copySuggested()}>
                {copied ? <DoneOutlinedIcon fontSize="small" color="success" /> : <ContentCopyOutlinedIcon fontSize="small" />}
            </IconButton>
        </Tooltip>
    </Stack>

    return <Paper variant="outlined" sx={{ minHeight: 0, height: fillAvailable ? '100%' : undefined, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <Stack direction="row" spacing={1} sx={{ px: 1.5, py: 1, alignItems: 'center', justifyContent: 'space-between', borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <CompareArrowsOutlinedIcon color="action" fontSize="small" />
                <Typography variant="body2" sx={{ fontWeight: 700 }}>{synchronizedLabel}</Typography>
            </Stack>
            <Chip size="small" label={changesLabel(changedParts)} />
        </Stack>
        {layout === 'unified'
            ? <UnifiedDiff title={suggestedTitle} changes={changes} actions={suggestedActions} />
            : <Box sx={{ flex: fillAvailable ? 1 : undefined, minHeight: fillAvailable ? 0 : 360, display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, minmax(0, 1fr))' }, gridTemplateRows: { xs: 'repeat(2, minmax(0, 1fr))', md: 'minmax(0, 1fr)' }, height: fillAvailable ? undefined : { xs: '70vh', md: '52vh' }, maxHeight: fillAvailable ? undefined : 640, overflow: 'hidden' }}>
                <DiffPane ref={leftRef} title={originalTitle} changes={changes} side="original" onScroll={event => synchronize(event.currentTarget, rightRef.current)} />
                <DiffPane ref={rightRef} title={suggestedTitle} changes={changes} side="suggested" actions={suggestedActions} onScroll={event => synchronize(event.currentTarget, leftRef.current)} />
            </Box>}
    </Paper>
}

function UnifiedDiff({ title, changes, actions }: { title: string, changes: Change[], actions: ReactNode }) {
    return <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <Stack direction="row" sx={{ px: 1.5, py: .5, alignItems: 'center', justifyContent: 'space-between', bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider' }}>
            <Typography variant="subtitle2">{title}</Typography>
            {actions}
        </Stack>
        <Box role="region" aria-label={title} sx={{ flex: 1, minHeight: 0, p: 2, overflow: 'auto', overscrollBehavior: 'contain', whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: '.9rem', lineHeight: 1.75, bgcolor: 'background.paper' }}>
            {changes.map((change, index) => <Box
                key={index}
                component="span"
                sx={change.removed
                    ? { bgcolor: 'rgba(211, 47, 47, .16)', color: 'error.dark', textDecoration: 'line-through' }
                    : change.added
                        ? { bgcolor: 'rgba(46, 125, 50, .18)', color: 'success.dark', textDecoration: 'none' }
                        : undefined}
            >{change.value}</Box>)}
        </Box>
    </Box>
}

function DiffPane({ ref, title, changes, side, actions, onScroll }: {
    ref: Ref<HTMLDivElement>
    title: string
    changes: Change[]
    side: 'original' | 'suggested'
    actions?: ReactNode
    onScroll: (event: UIEvent<HTMLDivElement>) => void
}) {
    return <Box sx={{ minWidth: 0, minHeight: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden', borderLeft: { md: side === 'suggested' ? 1 : 0 }, borderTop: { xs: side === 'suggested' ? 1 : 0, md: 0 }, borderColor: 'divider' }}>
        <Stack direction="row" sx={{ position: 'sticky', top: 0, zIndex: 1, px: 1.5, py: .5, alignItems: 'center', justifyContent: 'space-between', bgcolor: side === 'original' ? 'rgba(211, 47, 47, .06)' : 'rgba(46, 125, 50, .06)', borderBottom: 1, borderColor: 'divider' }}>
            <Typography variant="subtitle2">{title}</Typography>
            {actions}
        </Stack>
        <Box ref={ref} role="region" aria-label={title} onScroll={onScroll} sx={{ flex: 1, minHeight: 0, p: 1.5, overflow: 'auto', overscrollBehavior: 'contain', whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: '.875rem', lineHeight: 1.65, bgcolor: 'background.paper' }}>
            {changes.map((change, index) => {
                if (side === 'original' && change.added) return null
                if (side === 'suggested' && change.removed) return null
                const changed = side === 'original' ? change.removed : change.added
                return <Box key={index} component="span" sx={changed ? { bgcolor: side === 'original' ? 'rgba(211, 47, 47, .18)' : 'rgba(46, 125, 50, .20)', color: side === 'original' ? 'error.dark' : 'success.dark', textDecoration: side === 'original' ? 'line-through' : 'none' } : undefined}>{change.value}</Box>
            })}
        </Box>
    </Box>
}

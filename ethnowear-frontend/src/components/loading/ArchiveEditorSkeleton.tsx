import { Box, Paper, Skeleton, Stack } from '@mui/material'

export default function ArchiveEditorSkeleton() {
    return (
        <Paper variant="outlined" aria-busy="true" aria-label="Loading archive editor">
            <Stack
                direction="row"
                spacing={1.5}
                sx={{ px: 2, py: 1.25, borderBottom: 1, borderColor: 'divider', overflow: 'hidden' }}
            >
                {[92, 118, 76, 86, 112, 78].map((width, index) => (
                    <Skeleton key={index} variant="rounded" width={width} height={32} sx={{ flexShrink: 0 }} />
                ))}
            </Stack>
            <Box sx={{ p: { xs: 2, md: 3 } }}>
                <Stack spacing={2.5}>
                    <Skeleton variant="text" width={180} height={32} />
                    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, minmax(0, 1fr))' }, gap: 2 }}>
                        {[0, 1, 2, 3].map(field => (
                            <Stack key={field} spacing={0.75}>
                                <Skeleton variant="text" width="32%" height={20} />
                                <Skeleton variant="rounded" height={56} />
                            </Stack>
                        ))}
                    </Box>
                    <Stack spacing={0.75}>
                        <Skeleton variant="text" width="18%" height={20} />
                        <Skeleton variant="rounded" height={96} />
                    </Stack>
                </Stack>
            </Box>
        </Paper>
    )
}

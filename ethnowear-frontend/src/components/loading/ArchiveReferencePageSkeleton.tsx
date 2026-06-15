import { Box, Paper, Skeleton, Stack } from '@mui/material'
import EmbroideryCardSkeleton from './EmbroideryCardSkeleton'
import FilterPanelSkeleton from './FilterPanelSkeleton'

function ArchiveReferencePageSkeleton() {
    return (
        <Box
            sx={{
                display: 'grid',
                gridTemplateColumns: {
                    xs: 'minmax(0, 1fr)',
                    md: '300px minmax(0, 1fr)',
                },
                gap: 3,
                px: { xs: 2, md: 5 },
                py: 3,
                alignItems: 'start',
            }}
        >
            <FilterPanelSkeleton />

            <Stack spacing={4} sx={{ minWidth: 0 }}>
                <Stack spacing={2}>
                    <Box>
                        <Skeleton variant="text" width="45%" height={48} />
                        <Skeleton variant="text" width="65%" height={28} />
                    </Box>

                    <Skeleton variant="rounded" height={56} />
                </Stack>

                {[1, 2].map((section) => (
                    <Paper
                        key={section}
                        elevation={0}
                        sx={{
                            bgcolor: 'transparent',
                        }}
                    >
                        <Stack spacing={1.5}>
                            <Box>
                                <Skeleton variant="text" width="35%" height={36} />
                                <Skeleton variant="text" width="22%" height={24} />
                            </Box>

                            <Box
                                sx={{
                                    display: 'grid',
                                    gridTemplateColumns: {
                                        xs: '1fr',
                                        sm: 'repeat(2, minmax(0, 1fr))',
                                        lg: 'repeat(3, minmax(0, 1fr))',
                                    },
                                    gap: 3,
                                }}
                            >
                                {[1, 2, 3].map((card) => (
                                    <EmbroideryCardSkeleton key={card} />
                                ))}
                            </Box>
                        </Stack>
                    </Paper>
                ))}
            </Stack>
        </Box>
    )
}

export default ArchiveReferencePageSkeleton

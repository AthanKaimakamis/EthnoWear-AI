import { Box, Grid, Skeleton, Stack } from '@mui/material'
import FilterPanelSkeleton from './FilterPanelSkeleton'
import EmbroideryGridSkeleton from './EmroideryGridSkeleton'

function EmbroideryPageSkeleton() {
    return (
        <Box sx={{ px: { xs: 2, md: 4 }, py: 3 }}>
            <Grid container spacing={3}>
                <Grid size={{ xs: 12, md: 3 }}>
                    <FilterPanelSkeleton />
                </Grid>

                <Grid size={{ xs: 12, md: 9 }}>
                    <Stack spacing={3}>
                        <Skeleton variant="text" width="55%" height={44} />
                        <EmbroideryGridSkeleton />
                    </Stack>
                </Grid>
            </Grid>
        </Box>
    )
}

export default EmbroideryPageSkeleton
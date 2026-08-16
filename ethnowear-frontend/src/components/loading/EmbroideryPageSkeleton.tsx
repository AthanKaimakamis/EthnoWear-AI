import { Box, Grid, Skeleton, Stack } from '@mui/material'
import FilterPanelSkeleton from './FilterPanelSkeleton'
import EmbroideryCardSkeleton from './EmbroideryCardSkeleton'
import ArchiveBrowseLayout from '../archive/browse/ArchiveBrowseLayout'

function EmbroideryPageSkeleton() {
    return (
        <ArchiveBrowseLayout filters={<FilterPanelSkeleton />}>
            <Stack spacing={4} aria-busy="true">
                <Box>
                    <Skeleton variant="text" width="55%" height={48} />
                    <Skeleton variant="text" width="72%" height={24} />
                </Box>
                {[0, 1].map(section => (
                    <Stack key={section} spacing={2}>
                        <Stack direction="row" sx={{ alignItems: 'flex-end', justifyContent: 'space-between' }}>
                            <Box sx={{ width: '45%' }}>
                                <Skeleton variant="text" width="100%" height={36} />
                                <Skeleton variant="text" width="35%" height={20} />
                            </Box>
                            <Skeleton variant="rounded" width={132} height={36} />
                        </Stack>
                        <Grid container spacing={2.5}>
                            {Array.from({ length: 4 }).map((_, card) => (
                                <Grid key={card} size={{ xs: 12, sm: 6, xl: 3 }}>
                                    <EmbroideryCardSkeleton />
                                </Grid>
                            ))}
                        </Grid>
                    </Stack>
                ))}
            </Stack>
        </ArchiveBrowseLayout>
    )
}

export default EmbroideryPageSkeleton

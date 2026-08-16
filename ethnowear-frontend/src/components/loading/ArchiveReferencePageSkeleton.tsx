import { Box, Divider, Paper, Skeleton, Stack } from '@mui/material'
import CatalogueConceptCardSkeleton from './CatalogueConceptCardSkeleton'
import ArchiveBrowseLayout from '../archive/browse/ArchiveBrowseLayout'

function CatalogueFilterPanelSkeleton() {
    return (
        <Paper sx={{ p: 2, bgcolor: '#EEF1F1', border: 1, borderColor: 'divider' }}>
            <Stack spacing={1.5}>
                <Stack spacing={0.25}>
                    <Skeleton variant="text" width="70%" height={32} />
                    <Skeleton variant="text" width="84%" height={20} />
                </Stack>
                <Divider />
                {[0, 1].map(section => (
                    <Stack key={section} spacing={1} sx={{ py: 0.5 }}>
                        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                            <Skeleton variant="text" width="46%" height={24} />
                            <Skeleton variant="circular" width={24} height={24} />
                        </Stack>
                        <Skeleton variant="rounded" height={40} />
                        <Skeleton variant="text" width="86%" height={24} />
                        <Skeleton variant="text" width="72%" height={24} />
                    </Stack>
                ))}
                <Skeleton variant="rounded" height={40} />
            </Stack>
        </Paper>
    )
}

function ArchiveReferencePageSkeleton() {
    return (
        <ArchiveBrowseLayout filters={<CatalogueFilterPanelSkeleton />}>
            <Stack spacing={3} sx={{ minWidth: 0 }} aria-busy="true">
                <Stack spacing={2}>
                    <Box>
                        <Skeleton variant="text" width="45%" height={48} />
                        <Skeleton variant="text" width="65%" height={28} />
                    </Box>

                    <Skeleton variant="rounded" height={56} />
                </Stack>

                <Stack spacing={5}>
                    {[1, 2].map(section => (
                        <Stack key={section} spacing={1.5}>
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
                                    <CatalogueConceptCardSkeleton key={card} />
                                ))}
                            </Box>
                        </Stack>
                    ))}
                </Stack>
            </Stack>
        </ArchiveBrowseLayout>
    )
}

export default ArchiveReferencePageSkeleton

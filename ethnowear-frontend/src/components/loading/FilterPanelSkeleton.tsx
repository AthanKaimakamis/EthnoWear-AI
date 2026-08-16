import { Divider, Paper, Skeleton, Stack } from '@mui/material'

function FilterPanelSkeleton() {
    return (
        <Paper sx={{ p: 2, bgcolor: '#EEF1F1', border: 1, borderColor: 'divider' }}>
            <Stack spacing={1.5}>
                <Stack spacing={0.25}>
                    <Skeleton variant="text" width="70%" height={32} />
                    <Skeleton variant="text" width="85%" height={20} />
                </Stack>
                <Divider />
                <Stack spacing={0.75}>
                    <Skeleton variant="text" width="45%" height={18} />
                    <Skeleton variant="rounded" height={36} />
                </Stack>
                {[1, 2, 3].map(section => (
                    <Stack key={section} spacing={1} sx={{ py: 0.5 }}>
                        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                            <Skeleton variant="text" width="48%" height={24} />
                            <Skeleton variant="circular" width={24} height={24} />
                        </Stack>
                        <Skeleton variant="rounded" height={40} />
                        <Skeleton variant="text" width="88%" height={24} />
                        <Skeleton variant="text" width="76%" height={24} />
                    </Stack>
                ))}
                <Skeleton variant="rounded" height={40} />
            </Stack>
        </Paper>
    )
}

export default FilterPanelSkeleton

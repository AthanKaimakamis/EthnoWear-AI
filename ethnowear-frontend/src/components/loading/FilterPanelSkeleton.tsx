import {Paper, Skeleton, Stack} from '@mui/material';

function FilterPanelSkeleton(){
    return (
        <Paper sx={{ p:2}}>
            <Stack spacing={2}>
                <Skeleton variant="text" width={'70%'} height={32} />

                {[1, 2, 3, 4].map((section) => (
                    <Stack key={section} spacing={1}>
                        <Skeleton variant={'text'} width={36} />
                        <Skeleton variant={'rounded'} width={36} />
                        <Skeleton variant={'rounded'} width={36} />
                    </Stack>
                ))}

                <Skeleton variant={'rounded'} height={40} />
            </Stack>
        </Paper>
    )
}

export default FilterPanelSkeleton
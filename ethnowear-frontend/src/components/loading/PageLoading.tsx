import {LinearProgress, Stack, Typography} from "@mui/material";


type PageLoadingProps = {
    message?: string
}

function PageLoading({ message = 'Loading...' }: PageLoadingProps) {
    return (
        <Stack spacing={2}>
            <LinearProgress />
            <Typography color="text.secondary">
                {message}
            </Typography>
        </Stack>
    )
}

export default PageLoading
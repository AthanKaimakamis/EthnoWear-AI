import {Card, CardContent, Skeleton, Stack} from "@mui/material";


function EmbroideryCardSkeleton() {
    return (
        <Card>
            <Skeleton
                variant="rectangular"
                height={130}
                animation="wave"
            />

            <CardContent>
                <Stack spacing={1}>
                    <Skeleton variant="text" width="80%" height={28}/>
                    <Skeleton variant="text" width="100%"/>
                    <Skeleton variant="text" width="70%"/>

                    <Stack direction="row" spacing={1}>
                        <Skeleton variant="rounded" width={90} height={24}/>
                        <Skeleton variant="rounded" width={120} height={24}/>
                    </Stack>
                </Stack>
            </CardContent>
        </Card>
    )
}

export default EmbroideryCardSkeleton
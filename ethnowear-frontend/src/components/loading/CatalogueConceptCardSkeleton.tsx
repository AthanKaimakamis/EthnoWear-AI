import { Card, CardContent, Skeleton, Stack } from '@mui/material'

export default function CatalogueConceptCardSkeleton() {
    return (
        <Card sx={{ height: '100%', overflow: 'hidden', border: 1, borderColor: 'divider' }}>
            <Skeleton variant="rectangular" height={150} animation="wave" />
            <CardContent>
                <Stack spacing={1}>
                    <Skeleton variant="text" width="72%" height={28} />
                    <Skeleton variant="text" width="100%" />
                    <Skeleton variant="text" width="64%" />
                </Stack>
            </CardContent>
        </Card>
    )
}

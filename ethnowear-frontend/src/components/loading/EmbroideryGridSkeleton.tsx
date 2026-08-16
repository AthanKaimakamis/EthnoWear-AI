import { Grid } from '@mui/material'
import EmbroideryCardSkeleton from './EmbroideryCardSkeleton'

type Props = {
    count?: number
}

export default function EmbroideryGridSkeleton({ count = 6 }: Props) {
    return (
        <Grid container spacing={3}>
            {Array.from({ length: count }).map((_, index) => (
                <Grid key={index} size={{ xs: 12, sm: 6, xl: 3 }}>
                    <EmbroideryCardSkeleton />
                </Grid>
            ))}
        </Grid>
    )
}

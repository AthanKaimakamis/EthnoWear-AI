import {Grid} from "@mui/material";
import EmbroideryCardSkeleton from "./EmbroideryCardSkeleton.tsx";


type EmbroideryGridSkeletonProps = {
    count? : number
}

function EmbroideryGridSkeleton({count = 6}: EmbroideryGridSkeletonProps) {
    return (
        <Grid container spacing={3}>
            {Array.from({ length: count }).map((_, index) => (
                <Grid key={index} size={{ xs: 12, sm:6, lg:4 }}>
                    <EmbroideryCardSkeleton />
                </Grid>
            ))}
        </Grid>
    )
}

export default EmbroideryGridSkeleton
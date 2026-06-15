import type {ReactNode} from "react";
import {Divider, Stack, Typography} from "@mui/material";

type FormSectionProps = {
    title: string
    children?: ReactNode
}

function FormSection ({ title, children }: FormSectionProps) {
    return (
        <Stack spacing={2}>
            <Typography variant='subtitle2' sx={{ fontWeight:700 }}>
                {title}
            </Typography>
            <Divider />
            {children}
        </Stack>
    )
}

export default FormSection
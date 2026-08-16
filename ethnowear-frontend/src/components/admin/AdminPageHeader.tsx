import { Box, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'

type Props = {
    title: string
    description: string
    actions?: ReactNode
}

export default function AdminPageHeader({ title, description, actions }: Props) {
    return (
        <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', alignItems: { sm: 'flex-start' }, gap: 2 }}>
            <Box>
                <Typography component="h1" variant="h4" sx={{ color: 'text.primary', mb: .5 }}>{title}</Typography>
                <Typography color="text.secondary">{description}</Typography>
            </Box>
            {actions && <Stack direction="row" sx={{ gap: 1, flexWrap: 'wrap' }}>{actions}</Stack>}
        </Stack>
    )
}

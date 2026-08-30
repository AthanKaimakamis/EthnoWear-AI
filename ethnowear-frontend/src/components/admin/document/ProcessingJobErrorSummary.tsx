import { Alert, Box, Stack, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { processingJobErrorPresentation } from './processingJobPresentation'

type Props = {
    errorCode: string | null | undefined
    compact?: boolean
}

export default function ProcessingJobErrorSummary({ errorCode, compact = false }: Props) {
    const { t } = useTranslation()
    const presentation = processingJobErrorPresentation(t, errorCode)

    if (compact) return <Box sx={{ minWidth: 0 }}>
        <Typography variant="body2" sx={{ fontWeight: 700 }} noWrap>{presentation.title}</Typography>
        {presentation.code && <Typography variant="caption" color="text.secondary">{presentation.code}</Typography>}
    </Box>

    return <Alert severity="error" variant="outlined">
        <Stack spacing={.5}>
            <Typography sx={{ fontWeight: 700 }}>{presentation.title}</Typography>
            <Typography variant="body2">{presentation.message}</Typography>
            {presentation.guidance && <Typography variant="body2" sx={{ fontWeight: 600 }}>{presentation.guidance}</Typography>}
        </Stack>
    </Alert>
}

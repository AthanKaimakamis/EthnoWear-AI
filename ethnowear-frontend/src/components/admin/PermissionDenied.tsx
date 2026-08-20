import { Alert, AlertTitle, Box, Button } from '@mui/material'
import ArrowBackOutlinedIcon from '@mui/icons-material/ArrowBackOutlined'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

export default function PermissionDenied() {
    const { t } = useTranslation()

    return (
        <Box sx={{ maxWidth: 720, mx: 'auto', px: 2, py: 8 }}>
            <Alert severity="warning" variant="outlined">
                <AlertTitle>{t('auth.forbidden.title')}</AlertTitle>
                {t('auth.forbidden.message')}
            </Alert>
            <Button component={Link} to="/archive" startIcon={<ArrowBackOutlinedIcon />} sx={{ mt: 2 }}>
                {t('errorPage.archive')}
            </Button>
        </Box>
    )
}

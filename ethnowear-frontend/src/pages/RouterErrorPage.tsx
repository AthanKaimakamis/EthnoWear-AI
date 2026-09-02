import { Box, Button, Paper, Stack, Typography } from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlineOutlined'
import HomeOutlinedIcon from '@mui/icons-material/HomeOutlined'
import RefreshIcon from '@mui/icons-material/Refresh'
import { isRouteErrorResponse, useNavigate, useRouteError } from 'react-router'
import { useTranslation } from 'react-i18next'

function RouterErrorPage() {
    const error = useRouteError()
    const navigate = useNavigate()
    const { t } = useTranslation()
    const notFound = isRouteErrorResponse(error) && error.status === 404
    const status = isRouteErrorResponse(error) ? error.status : null

    return (
        <Box sx={{ minHeight: '100dvh', bgcolor: 'background.default', display: 'grid', gridTemplateRows: 'auto 1fr' }}>
            <Box component="header" sx={{ bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider', px: { xs: 2, sm: 4 }, py: 1.5 }}>
                <Stack direction="row" spacing={1.5} sx={{ maxWidth: 1180, mx: 'auto', alignItems: 'center' }}>
                    <Box component="img" src="/logo_v3.png" alt="" aria-hidden="true" sx={{ width: 44, height: 44, objectFit: 'contain' }} />
                    <Box>
                        <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.1 }}>EthnoWear</Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase' }}>{t('app.subtitle')}</Typography>
                    </Box>
                </Stack>
            </Box>

            <Box component="main" sx={{ display: 'grid', placeItems: 'center', px: 2, py: 6 }}>
                <Paper variant="outlined" sx={{ width: '100%', maxWidth: 620, p: { xs: 3, sm: 5 }, textAlign: 'center', bgcolor: 'background.paper' }}>
                    <Stack spacing={3} sx={{ alignItems: 'center' }}>
                        <Box sx={{ width: 72, height: 72, display: 'grid', placeItems: 'center', color: 'primary.main', bgcolor: '#F4E9EB', borderRadius: '50%' }}>
                            <ErrorOutlineIcon sx={{ fontSize: 40 }} />
                        </Box>
                        <Box>
                            {status && <Typography variant="overline" color="primary.main" sx={{ fontWeight: 800 }}>{t('errorPage.status', { status })}</Typography>}
                            <Typography variant="h3" component="h1" sx={{ color: 'text.primary', fontWeight: 800, fontSize: { xs: '2rem', sm: '2.5rem' }, mt: status ? 0.5 : 0 }}>
                                {notFound ? t('errorPage.notFoundTitle') : t('errorPage.unexpectedTitle')}
                            </Typography>
                            <Typography color="text.secondary" sx={{ mt: 1.5, maxWidth: 480 }}>
                                {notFound ? t('errorPage.notFoundMessage') : t('errorPage.unexpectedMessage')}
                            </Typography>
                        </Box>
                        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ width: { xs: '100%', sm: 'auto' } }}>
                            <Button variant="contained" startIcon={<HomeOutlinedIcon />} onClick={() => navigate('/archive/embroideries')}>
                                {t('errorPage.archive')}
                            </Button>
                            <Button variant="outlined" startIcon={<ArrowBackIcon />} onClick={() => navigate(-1)}>
                                {t('errorPage.back')}
                            </Button>
                            {!notFound && <Button variant="text" startIcon={<RefreshIcon />} onClick={() => window.location.reload()}>
                                {t('errorPage.retry')}
                            </Button>}
                        </Stack>
                    </Stack>
                </Paper>
            </Box>
        </Box>
    )
}

export default RouterErrorPage

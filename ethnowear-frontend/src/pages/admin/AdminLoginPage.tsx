import { Alert, Box, Button, IconButton, InputAdornment, Paper, Stack, TextField, Tooltip, Typography } from '@mui/material'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import LoginOutlinedIcon from '@mui/icons-material/LoginOutlined'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { ApiError } from '../../api/http'
import { useAdminAuth } from '../../app/adminAuth'

function AdminLoginPage() {
    const { t } = useTranslation()
    const { login } = useAdminAuth()
    const navigate = useNavigate()
    const location = useLocation()
    const [pending, setPending] = useState(false)
    const [showPassword, setShowPassword] = useState(false)
    const [errorKey, setErrorKey] = useState<string | null>(null)

    async function submit(event: React.SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        setPending(true)
        setErrorKey(null)

        try {
            await login(String(data.get('username') ?? ''), String(data.get('password') ?? ''))
            const from = typeof location.state === 'object' && location.state && 'from' in location.state
                ? String(location.state.from)
                : '/admin'
            navigate(from, { replace: true })
        } catch (error) {
            setErrorKey(error instanceof ApiError && error.status === 401
                ? 'admin.login.invalid'
                : 'admin.login.failed')
        } finally {
            setPending(false)
        }
    }

    return (
        <Box sx={{ minHeight: 'calc(100vh - 68px)', display: 'grid', placeItems: 'center', px: 2, py: { xs: 4, md: 7 } }}>
            <Paper component="form" onSubmit={submit} variant="outlined" sx={{
                width: '100%', maxWidth: 460, p: { xs: 3, sm: 4 }, bgcolor: 'background.paper',
                borderTop: 4, borderTopColor: 'primary.main', boxShadow: '0 14px 38px rgba(24, 28, 24, 0.12)',
            }}>
                <Stack spacing={3.5}>
                    <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                        <Box sx={{ width: 52, height: 52, flexShrink: 0, display: 'grid', placeItems: 'center', bgcolor: '#F4E9EB', color: 'primary.main', borderRadius: 1 }}>
                            <LockOutlinedIcon />
                        </Box>
                        <Box sx={{ minWidth: 0 }}>
                            <Typography variant="h4" component="h1" sx={{ color: 'text.primary', fontWeight: 800, fontSize: { xs: '1.75rem', sm: '2rem' } }}>
                                {t('admin.login.title')}
                            </Typography>
                            <Typography color="text.secondary" sx={{ mt: 0.5 }}>{t('admin.login.subtitle')}</Typography>
                        </Box>
                    </Stack>
                    {errorKey && <Alert severity="error">{t(errorKey)}</Alert>}
                    <Stack spacing={2.25}>
                        <TextField name="username" label={t('admin.login.username')} autoComplete="username" required autoFocus
                            slotProps={{ input: { sx: { bgcolor: 'background.paper' } } }} />
                        <TextField name="password" label={t('admin.login.password')} type={showPassword ? 'text' : 'password'} autoComplete="current-password" required
                            slotProps={{ input: {
                                sx: { bgcolor: 'background.paper' },
                                endAdornment: <InputAdornment position="end">
                                    <Tooltip title={showPassword ? t('admin.login.hidePassword') : t('admin.login.showPassword')}>
                                        <IconButton edge="end" onClick={() => setShowPassword(current => !current)}
                                            aria-label={showPassword ? t('admin.login.hidePassword') : t('admin.login.showPassword')}>
                                            {showPassword ? <VisibilityOffOutlinedIcon /> : <VisibilityOutlinedIcon />}
                                        </IconButton>
                                    </Tooltip>
                                </InputAdornment>,
                            } }} />
                        <Button type="submit" variant="contained" size="large" startIcon={<LoginOutlinedIcon />} disabled={pending}
                            sx={{ minHeight: 48, fontWeight: 800 }}>
                            {pending ? t('admin.login.signingIn') : t('admin.login.submit')}
                        </Button>
                    </Stack>
                </Stack>
            </Paper>
        </Box>
    )
}

export default AdminLoginPage

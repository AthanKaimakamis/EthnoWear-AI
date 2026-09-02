import { Alert, Box, Button, IconButton, InputAdornment, Stack, Tab, Tabs, TextField, Tooltip } from '@mui/material'
import LoginOutlinedIcon from '@mui/icons-material/LoginOutlined'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { ApiError } from '../../api/http'
import { useAdminAuth } from '../../app/adminAuth'
import AdminModal from './AdminModal'
import PublicAccount from '../public/PublicAccount'

type Props = { open: boolean; onClose?: () => void; onSuccess?: () => void; redirectAfterLogin?: boolean; publicLogin?: boolean; initialTab?: 'google' | 'management' }

export default function AdminLoginDialog({ open, onClose, onSuccess, redirectAfterLogin = false, publicLogin = false, initialTab }: Props) {
    const { t } = useTranslation()
    const { login } = useAdminAuth()
    const navigate = useNavigate()
    const location = useLocation()
    const [pending, setPending] = useState(false)
    const [publicPending, setPublicPending] = useState(false)
    const [tab, setTab] = useState(initialTab ?? (publicLogin ? 'google' : 'management'))
    const [showPassword, setShowPassword] = useState(false)
    const [errorKey, setErrorKey] = useState<string | null>(null)
    const formId = 'admin-login-form'

    async function submit(event: React.SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        setPending(true)
        setErrorKey(null)
        try {
            const currentUser = await login(String(data.get('username') ?? ''), String(data.get('password') ?? ''))
            if (currentUser.passwordChangeRequired) navigate('/account/password', { replace: true })
            else if (redirectAfterLogin) {
                const from = typeof location.state === 'object' && location.state && 'from' in location.state
                    ? String(location.state.from) : '/management'
                navigate(from, { replace: true })
            }
            onSuccess?.()
        } catch (error) {
            setErrorKey(error instanceof ApiError && error.status === 401 ? 'admin.login.invalid' : 'admin.login.failed')
        } finally { setPending(false) }
    }

    const managementTab = !publicLogin || tab === 'management'
    return <AdminModal open={open} onClose={onClose ?? (() => undefined)} closeDisabled={pending || publicPending || !onClose} maxWidth="xs" blurBackdrop
        title={t(publicLogin ? 'nav.login' : 'admin.login.title')} description={publicLogin ? undefined : t('admin.login.subtitle')}
        actions={managementTab ? <Button type="submit" form={formId} variant="contained" startIcon={<LoginOutlinedIcon />} disabled={pending}>{pending ? t('admin.login.signingIn') : t('admin.login.submit')}</Button>
            : <Button onClick={onClose} disabled={publicPending}>{t('publicAuth:guest')}</Button>}>
        {publicLogin && <Tabs value={tab} onChange={(_, value) => setTab(value)} variant="fullWidth" aria-label={t('nav.login')} sx={{ mb: 3 }}>
            <Tab value="google" label="Google" id="login-google-tab" aria-controls="login-google-panel" disabled={pending || publicPending} />
            <Tab value="management" label={t('nav.management')} id="login-management-tab" aria-controls="login-management-panel" disabled={pending || publicPending} />
        </Tabs>}
        {publicLogin && !managementTab && open && <Box role="tabpanel" id="login-google-panel" aria-labelledby="login-google-tab">
            <PublicAccount loginPanel onSuccess={onClose} onPendingChange={setPublicPending} />
        </Box>}
        <Box hidden={!managementTab} role={publicLogin ? 'tabpanel' : undefined} id="login-management-panel" aria-labelledby={publicLogin ? 'login-management-tab' : undefined}>
        <Box component="form" id={formId} onSubmit={submit}>
            <Stack spacing={2.25}>
                {errorKey && <Alert severity="error">{t(errorKey)}</Alert>}
                <TextField name="username" label={t('admin.login.username')} autoComplete="username" required autoFocus={!publicLogin} />
                <TextField name="password" label={t('admin.login.password')} type={showPassword ? 'text' : 'password'} autoComplete="current-password" required
                    slotProps={{ input: { endAdornment: <InputAdornment position="end"><Tooltip title={showPassword ? t('admin.login.hidePassword') : t('admin.login.showPassword')}><IconButton edge="end" onClick={() => setShowPassword(value => !value)} aria-label={showPassword ? t('admin.login.hidePassword') : t('admin.login.showPassword')}>{showPassword ? <VisibilityOffOutlinedIcon /> : <VisibilityOutlinedIcon />}</IconButton></Tooltip></InputAdornment> } }} />
            </Stack>
        </Box>
        </Box>
    </AdminModal>
}

import { Alert, Box, Button, IconButton, InputAdornment, Stack, TextField, Tooltip } from '@mui/material'
import LoginOutlinedIcon from '@mui/icons-material/LoginOutlined'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { ApiError } from '../../api/http'
import { useAdminAuth } from '../../app/adminAuth'
import AdminModal from './AdminModal'

type Props = { open: boolean; onClose?: () => void; onSuccess?: () => void; redirectAfterLogin?: boolean }

export default function AdminLoginDialog({ open, onClose, onSuccess, redirectAfterLogin = false }: Props) {
    const { t } = useTranslation()
    const { login } = useAdminAuth()
    const navigate = useNavigate()
    const location = useLocation()
    const [pending, setPending] = useState(false)
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
                    ? String(location.state.from) : '/admin'
                navigate(from, { replace: true })
            }
            onSuccess?.()
        } catch (error) {
            setErrorKey(error instanceof ApiError && error.status === 401 ? 'admin.login.invalid' : 'admin.login.failed')
        } finally { setPending(false) }
    }

    return <AdminModal open={open} onClose={onClose ?? (() => undefined)} closeDisabled={pending || !onClose} maxWidth="xs" blurBackdrop
        title={t('admin.login.title')} description={t('admin.login.subtitle')}
        actions={<Button type="submit" form={formId} variant="contained" startIcon={<LoginOutlinedIcon />} disabled={pending}>{pending ? t('admin.login.signingIn') : t('admin.login.submit')}</Button>}>
        <Box component="form" id={formId} onSubmit={submit}>
            <Stack spacing={2.25}>
                {errorKey && <Alert severity="error">{t(errorKey)}</Alert>}
                <TextField name="username" label={t('admin.login.username')} autoComplete="username" required autoFocus />
                <TextField name="password" label={t('admin.login.password')} type={showPassword ? 'text' : 'password'} autoComplete="current-password" required
                    slotProps={{ input: { endAdornment: <InputAdornment position="end"><Tooltip title={showPassword ? t('admin.login.hidePassword') : t('admin.login.showPassword')}><IconButton edge="end" onClick={() => setShowPassword(value => !value)} aria-label={showPassword ? t('admin.login.hidePassword') : t('admin.login.showPassword')}>{showPassword ? <VisibilityOffOutlinedIcon /> : <VisibilityOutlinedIcon />}</IconButton></Tooltip></InputAdornment> } }} />
            </Stack>
        </Box>
    </AdminModal>
}

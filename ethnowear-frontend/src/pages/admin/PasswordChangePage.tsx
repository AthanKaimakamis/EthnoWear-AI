import {
    Alert, Box, Button, LinearProgress, List, ListItem, ListItemIcon, ListItemText,
    Paper, Stack, TextField, Typography,
} from '@mui/material'
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlineOutlined'
import PasswordOutlinedIcon from '@mui/icons-material/PasswordOutlined'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useNavigate } from 'react-router'
import { apiErrorMessage } from '../../api/http'
import { useAdminAuth } from '../../app/adminAuth'

export default function PasswordChangePage() {
    const { t } = useTranslation()
    const { admin, authenticated, changePassword } = useAdminAuth()
    const navigate = useNavigate()
    const [pending, setPending] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [newPassword, setNewPassword] = useState('')

    const rules = useMemo(() => [
        newPassword.length >= 12 && newPassword.length <= 128,
        /[a-z]/.test(newPassword) && /[A-Z]/.test(newPassword),
        /\d/.test(newPassword),
        /[^A-Za-z0-9]/.test(newPassword),
        newPassword.toLocaleLowerCase() !== admin?.username.toLocaleLowerCase(),
    ], [admin?.username, newPassword])

    if (!authenticated) return <Navigate to="/management/login" replace />

    async function submit(event: React.SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        const currentPassword = String(data.get('currentPassword') ?? '')
        const confirmation = String(data.get('confirmation') ?? '')
        if (confirmation !== newPassword) {
            setError(t('auth.passwordChange.passwordsDiffer'))
            return
        }
        if (currentPassword === newPassword) {
            setError(t('auth.passwordChange.sameAsCurrent'))
            return
        }

        setPending(true)
        setError(null)
        try {
            await changePassword(currentPassword, newPassword)
            navigate('/management', { replace: true })
        } catch (cause) {
            setError(apiErrorMessage(cause, t('auth.passwordChange.failed')))
        } finally {
            setPending(false)
        }
    }

    return (
        <Box sx={{ minHeight: 'calc(100vh - 68px)', display: 'grid', placeItems: 'center', px: 2, py: 5 }}>
            <Paper component="form" onSubmit={submit} variant="outlined" sx={{ width: '100%', maxWidth: 560, overflow: 'hidden' }}>
                {pending && <LinearProgress />}
                <Stack spacing={3} sx={{ p: { xs: 3, sm: 4 } }}>
                    <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                        <Box sx={{ width: 52, height: 52, display: 'grid', placeItems: 'center', bgcolor: '#F4E9EB', color: 'primary.main', borderRadius: 1 }}>
                            <PasswordOutlinedIcon />
                        </Box>
                        <Box>
                            <Typography component="h1" variant="h4">{t('auth.passwordChange.title')}</Typography>
                            <Typography color="text.secondary">{t('auth.passwordChange.subtitle')}</Typography>
                        </Box>
                    </Stack>
                    {error && <Alert severity="error">{error}</Alert>}
                    <TextField name="currentPassword" type="password" autoComplete="current-password" required label={t('auth.passwordChange.current')} />
                    <TextField name="newPassword" type="password" autoComplete="new-password" required value={newPassword}
                        onChange={event => setNewPassword(event.target.value)} label={t('auth.passwordChange.new')} />
                    <TextField name="confirmation" type="password" autoComplete="new-password" required label={t('auth.passwordChange.confirm')} />
                    <List dense disablePadding>
                        {rules.map((valid, index) => (
                            <ListItem key={index} disableGutters>
                                <ListItemIcon sx={{ minWidth: 32, color: valid ? 'success.main' : 'text.disabled' }}>
                                    <CheckCircleOutlineIcon fontSize="small" />
                                </ListItemIcon>
                                <ListItemText primary={t(`auth.passwordChange.rules.${index}`)} />
                            </ListItem>
                        ))}
                    </List>
                    <Button type="submit" variant="contained" size="large" disabled={pending || rules.some(valid => !valid)}>
                        {pending ? t('forms.saving') : t('auth.passwordChange.submit')}
                    </Button>
                </Stack>
            </Paper>
        </Box>
    )
}

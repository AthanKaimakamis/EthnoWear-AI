import { useEffect, useRef, useState } from 'react'
import { Alert, Box, Button, CircularProgress, Menu, MenuItem, Stack, Tooltip, Typography } from '@mui/material'
import PersonOutlineIcon from '@mui/icons-material/AccountCircleOutlined'
import { useTranslation } from 'react-i18next'
import { initializePublicAuth, loginPublicUser, logoutPublicUser, publicAuthClient, usePublicAuth } from '../../app/publicAuthStore'
import { PublicAuthError } from '../../api/PublicAuthApi'
import { loadGoogleIdentity } from '../../api/googleIdentity'
import './publicAuthTranslations'

export function publicAuthErrorKey(error: unknown) {
    if (!(error instanceof PublicAuthError)) return 'unavailable'
    if (error.code === 'PUBLIC_ACCOUNT_DISABLED') return 'disabled'
    if (error.status === 429) return 'rateLimited'
    if (error.status === 401 || error.code === 'PUBLIC_LOGIN_CHALLENGE_INVALID') return 'restart'
    if (error.code === 'PUBLIC_ACCESS_DENIED') return 'denied'
    return 'unavailable'
}

type Props = { loginPanel?: boolean; onSuccess?: () => void; onPendingChange?: (pending: boolean) => void; onManagerLogin?: () => void }

export default function PublicAccount({ loginPanel = false, onSuccess, onPendingChange, onManagerLogin }: Props) {
    const { t, i18n } = useTranslation()
    const { config, profile, ready } = usePublicAuth()
    const [anchor, setAnchor] = useState<HTMLElement | null>(null)
    const [error, setError] = useState<unknown>(null)
    const [busy, setBusy] = useState(false)
    const [attempt, setAttempt] = useState(0)
    const [cooldown, setCooldown] = useState(0)
    const host = useRef<HTMLDivElement>(null)
    const submitting = useRef(false)
    const mounted = useRef(false)
    const success = useRef(onSuccess)
    const pendingChange = useRef(onPendingChange)
    success.current = onSuccess
    pendingChange.current = onPendingChange
    useEffect(() => {
        mounted.current = true
        return () => { mounted.current = false }
    }, [])
    useEffect(() => { void initializePublicAuth() }, [])
    useEffect(() => {
        if (!cooldown) return
        const timer = setTimeout(() => setCooldown(0), Math.max(0, cooldown - Date.now()))
        return () => clearTimeout(timer)
    }, [cooldown])
    useEffect(() => {
        if (!loginPanel || !ready || profile || !config?.enabled || !config.googleClientId) return
        let active = true
        let expiry: ReturnType<typeof setTimeout> | undefined
        setBusy(true)
        const report = (caught: unknown) => {
            if (!active) return
            setError(caught)
            setBusy(false)
            if (caught instanceof PublicAuthError && caught.status === 429) setCooldown(caught.retryAt)
        }
        void (async () => {
            try {
                const gis = await loadGoogleIdentity()
                if (!active) return
                const challenge = await publicAuthClient.challenge()
                if (!active || !host.current) return
                gis.initialize({ client_id: config.googleClientId!, nonce: challenge.nonce, auto_select: false,
                    callback: response => {
                        if (!active || submitting.current) return
                        submitting.current = true
                        pendingChange.current?.(true)
                        clearTimeout(expiry)
                        setBusy(true)
                        void loginPublicUser(response.credential).then(() => {
                            if (mounted.current) success.current?.()
                        }).catch(caught => {
                            report(caught)
                            if (active && caught instanceof PublicAuthError && (caught.status === 401 || caught.code === 'PUBLIC_LOGIN_CHALLENGE_INVALID')) {
                                setAttempt(value => value + 1)
                            }
                        }).finally(() => { submitting.current = false; if (mounted.current) { pendingChange.current?.(false); setBusy(false) } })
                    },
                })
                host.current.replaceChildren()
                gis.renderButton(host.current, { theme: 'outline', size: 'large', locale: i18n.resolvedLanguage ?? 'bg', width: Math.min(400, host.current.clientWidth || 320) })
                setBusy(false)
                expiry = setTimeout(() => {
                    host.current?.replaceChildren()
                    report(new PublicAuthError(401, 'PUBLIC_LOGIN_CHALLENGE_INVALID'))
                }, Math.max(0, Date.parse(challenge.expiresAt) - Date.now()))
            } catch (caught) { report(caught) }
        })()
        return () => { active = false; clearTimeout(expiry); window.google?.accounts.id.cancel() }
    }, [loginPanel, ready, profile, config, attempt, i18n.resolvedLanguage])

    async function logout() {
        if (submitting.current) return
        submitting.current = true
        setBusy(true)
        try { await logoutPublicUser(); window.google?.accounts.id.disableAutoSelect(); setAnchor(null); setError(null) }
        catch (caught) { setError(caught); if (caught instanceof PublicAuthError) setCooldown(caught.retryAt) }
        finally { submitting.current = false; setBusy(false) }
    }
    const message = error ? t(`publicAuth:${publicAuthErrorKey(error)}`) : null
    if (loginPanel) return <Stack spacing={2}>
        {message && <Alert severity="warning">{message}</Alert>}
        {profile ? <Typography>{t('publicAuth:signedIn', { name: profile.displayName })}</Typography>
            : ready && (!config?.enabled || !config.googleClientId) ? <Alert severity="info">{t('publicAuth:unavailable')}</Alert> : null}
        <Box sx={{ position: 'relative', width: '100%', maxWidth: 400, alignSelf: 'center', minHeight: 44 }}>
            <Box ref={host} sx={{ width: '100%', minHeight: 44, visibility: busy ? 'hidden' : 'visible', pointerEvents: busy ? 'none' : 'auto' }} />
            {!profile && (!ready || busy) && <CircularProgress size={24} aria-label={t('publicAuth:loading')} sx={{ position: 'absolute', top: 10, left: 'calc(50% - 12px)' }} />}
        </Box>
        {Boolean(error) && !busy && <Button disabled={Boolean(cooldown)} onClick={() => { setError(null); setAttempt(value => value + 1) }}>{t('publicAuth:retry')}</Button>}
    </Stack>
    if (!ready || !profile) return null
    return <>
        <Tooltip title={t('publicAuth:signedIn', { name: profile.displayName })}><Button color="inherit" startIcon={<PersonOutlineIcon />} aria-label={t('publicAuth:signedIn', { name: profile.displayName })} aria-haspopup="menu" aria-expanded={Boolean(anchor)} sx={{ minWidth: 0, maxWidth: { xs: 120, sm: 220 }, textTransform: 'none' }} onClick={event => {
            setError(null)
            setAnchor(event.currentTarget)
        }}><Typography component="span" noWrap sx={{ fontWeight: 700 }}>{profile.displayName || profile.email}</Typography></Button></Tooltip>
        <Menu anchorEl={anchor} open={Boolean(anchor) && Boolean(profile)} onClose={() => setAnchor(null)}>
            <Box sx={{ p: 2, maxWidth: 300, overflowWrap: 'anywhere' }}>
                <Typography variant="caption">{t('publicAuth:googleAccount')}</Typography>
                <Typography sx={{ fontWeight: 700 }}>{profile?.displayName}</Typography><Typography variant="body2">{profile?.email}</Typography>
                {message && <Alert severity="error">{message}</Alert>}
            </Box>
            {onManagerLogin && <MenuItem disabled={busy} onClick={() => { setAnchor(null); onManagerLogin() }}>{t('publicAuth:managerLogin')}</MenuItem>}
            <MenuItem disabled={busy || Boolean(cooldown)} onClick={() => void logout()}>{t('publicAuth:logout')}</MenuItem>
        </Menu>
    </>
}

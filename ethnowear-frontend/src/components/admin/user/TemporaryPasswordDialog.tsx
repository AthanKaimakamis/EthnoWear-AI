import { Alert, Button, Stack, TextField, Typography } from '@mui/material'
import ContentCopyOutlinedIcon from '@mui/icons-material/ContentCopyOutlined'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { TemporaryPassword } from '../../../types/userAdmin'
import { parseServerDateTime } from '../../../app/serverDateTime'
import AdminModal from '../AdminModal'

type Props = {
    open: boolean
    credentials: TemporaryPassword | null
    onClose: () => void
}

export default function TemporaryPasswordDialog({ open, credentials, onClose }: Props) {
    const { t, i18n } = useTranslation()
    const [copied, setCopied] = useState(false)

    async function copyPassword() {
        if (!credentials) return
        await navigator.clipboard.writeText(credentials.temporaryPassword)
        setCopied(true)
    }

    function close() {
        setCopied(false)
        onClose()
    }

    return (
        <AdminModal
            open={open}
            title={t('users.temporaryPassword.title')}
            description={t('users.temporaryPassword.description')}
            maxWidth="sm"
            onClose={close}
            actions={<Button variant="contained" onClick={close}>{t('users.temporaryPassword.done')}</Button>}
        >
            {credentials && (
                <Stack spacing={2.5}>
                    <Alert severity="warning">{t('users.temporaryPassword.once')}</Alert>
                    <TextField
                        value={credentials.temporaryPassword}
                        label={t('users.temporaryPassword.label')}
                        slotProps={{ input: { readOnly: true, sx: { fontFamily: 'monospace' } } }}
                    />
                    <Button variant="outlined" startIcon={<ContentCopyOutlinedIcon />} onClick={copyPassword}>
                        {copied ? t('users.temporaryPassword.copied') : t('users.temporaryPassword.copy')}
                    </Button>
                    <Typography variant="body2">
                        {t('users.temporaryPassword.expires', {
                            date: new Intl.DateTimeFormat(i18n.resolvedLanguage ?? 'bg', { dateStyle: 'medium', timeStyle: 'short' })
                                .format(parseServerDateTime(credentials.expiresAt)),
                        })}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">{t('users.temporaryPassword.changeRequired')}</Typography>
                </Stack>
            )}
        </AdminModal>
    )
}

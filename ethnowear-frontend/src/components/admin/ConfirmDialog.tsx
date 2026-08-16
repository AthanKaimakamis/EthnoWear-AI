import type { ReactNode } from 'react'
import { Button, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import AdminModal from './AdminModal'

type Props = {
    open: boolean
    title: string
    children: ReactNode
    confirmLabel?: string
    pending?: boolean
    onCancel: () => void
    onConfirm: () => void
}

export default function ConfirmDialog({
    open,
    title,
    children,
    confirmLabel,
    pending = false,
    onCancel,
    onConfirm,
}: Props) {
    const { t } = useTranslation()

    return (
        <AdminModal
            open={open}
            title={title}
            onClose={onCancel}
            closeDisabled={pending}
            maxWidth="sm"
            actions={
                <>
                    <Button onClick={onCancel} disabled={pending}>{t('admin.cancel')}</Button>
                    <Button color="error" variant="contained" onClick={onConfirm} disabled={pending}>
                        {confirmLabel ?? t('admin.delete')}
                    </Button>
                </>
            }
        >
            <Typography>{children}</Typography>
        </AdminModal>
    )
}

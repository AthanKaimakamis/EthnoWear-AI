import type { ReactNode } from 'react'
import { Box, Button, type ButtonProps } from '@mui/material'
import { useTranslation } from 'react-i18next'
import AdminModal from './AdminModal'

type Props = {
    open: boolean
    title: string
    children: ReactNode
    confirmLabel?: string
    confirmColor?: ButtonProps['color']
    pending?: boolean
    confirmDisabled?: boolean
    onCancel: () => void
    onConfirm: () => void
}

export default function ConfirmDialog({
    open,
    title,
    children,
    confirmLabel,
    confirmColor = 'error',
    pending = false,
    confirmDisabled = false,
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
                    <Button color={confirmColor} variant="contained" onClick={onConfirm} disabled={pending || confirmDisabled}>
                        {confirmLabel ?? t('admin.delete')}
                    </Button>
                </>
            }
        >
            <Box>{children}</Box>
        </AdminModal>
    )
}

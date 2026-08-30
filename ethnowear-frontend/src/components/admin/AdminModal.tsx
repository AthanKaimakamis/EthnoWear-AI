import type { ReactNode } from 'react'
import {
    Box, Dialog, DialogActions, DialogContent, DialogTitle, IconButton,
    Stack, Tooltip, Typography,
} from '@mui/material'
import type { DialogProps } from '@mui/material/Dialog'
import CloseIcon from '@mui/icons-material/Close'
import { useTranslation } from 'react-i18next'

type Props = {
    open: boolean
    title: ReactNode
    description?: ReactNode
    children: ReactNode
    actions?: ReactNode
    headerActions?: ReactNode
    onClose: () => void
    closeDisabled?: boolean
    maxWidth?: DialogProps['maxWidth']
    fullScreen?: boolean
    blurBackdrop?: boolean
}

export default function AdminModal({
    open,
    title,
    description,
    children,
    actions,
    headerActions,
    onClose,
    closeDisabled = false,
    maxWidth = 'md',
    fullScreen = false,
    blurBackdrop = false,
}: Props) {
    const { t } = useTranslation()

    return (
        <Dialog
            open={open}
            onClose={closeDisabled ? undefined : onClose}
            fullWidth
            fullScreen={fullScreen}
            maxWidth={maxWidth}
            scroll="paper"
            slotProps={{ backdrop: { sx: blurBackdrop ? { backdropFilter: 'blur(7px)' } : undefined } }}
        >
            <DialogTitle component="div" sx={{ px: 3, py: 2 }}>
                <Stack direction="row" sx={{ alignItems: 'flex-start', justifyContent: 'space-between', gap: 2 }}>
                    <Box sx={{ minWidth: 0 }}>
                        <Typography component="h2" variant="h5" sx={{ color: 'text.primary', fontWeight: 800 }}>
                            {title}
                        </Typography>
                        {description && (
                            <Typography component="div" color="text.secondary" variant="body2" sx={{ mt: .5 }}>
                                {description}
                            </Typography>
                        )}
                    </Box>
                    <Stack direction="row" spacing={.5} sx={{ alignItems: 'center', flexShrink: 0 }}>
                        {headerActions}
                        <Tooltip title={t('curator.actions.close')}>
                            <span>
                                <IconButton
                                    aria-label={t('curator.actions.close')}
                                    disabled={closeDisabled}
                                    edge="end"
                                    onClick={onClose}
                                >
                                    <CloseIcon />
                                </IconButton>
                            </span>
                        </Tooltip>
                    </Stack>
                </Stack>
            </DialogTitle>
            <DialogContent dividers sx={{ p: { xs: 2, md: 3 }, bgcolor: 'background.default' }}>
                {children}
            </DialogContent>
            {actions && (
                <DialogActions sx={{ px: 3, py: 2, gap: 1 }}>
                    {actions}
                </DialogActions>
            )}
        </Dialog>
    )
}

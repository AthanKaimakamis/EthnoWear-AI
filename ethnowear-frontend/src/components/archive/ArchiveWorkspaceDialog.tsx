import type { ReactNode } from 'react'
import { Dialog, DialogContent, IconButton } from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import { useTranslation } from 'react-i18next'

type Props = {
    children: ReactNode
    labelledBy: string
    onClose: () => void
}

export default function ArchiveWorkspaceDialog({ children, labelledBy, onClose }: Props) {
    const { t } = useTranslation()

    return (
        <Dialog
            open
            onClose={onClose}
            fullWidth
            maxWidth={false}
            scroll="paper"
            aria-labelledby={labelledBy}
            data-archive-workspace-modal
            sx={{
                '& .MuiDialog-container': {
                    boxSizing: 'border-box',
                    alignItems: 'stretch',
                    pt: { xs: '116px', md: '120px' },
                },
                '& .MuiDialog-paper': {
                    width: { xs: 'calc(100% - 16px)', md: 'min(1680px, calc(100% - 48px))' },
                    height: { xs: 'calc(100% - 16px)', md: 'calc(100% - 48px)' },
                    maxWidth: 1680,
                    maxHeight: 'none',
                    m: { xs: '8px', md: '24px' },
                    borderRadius: 1,
                    bgcolor: '#F8F9F8',
                },
            }}
            slotProps={{
                backdrop: {
                    sx: { top: { xs: '116px', md: '120px' } },
                },
            }}
        >
            <IconButton
                aria-label={t('common.close')}
                onClick={onClose}
                sx={{ position: 'absolute', right: 12, top: 12, zIndex: 2 }}
            >
                <CloseIcon />
            </IconButton>
            <DialogContent dividers sx={{ p: { xs: 2, sm: 3, md: 4 }, color: '#171917' }}>
                <div style={{ width: '100%', maxWidth: 1600, margin: '0 auto' }}>
                    {children}
                </div>
            </DialogContent>
        </Dialog>
    )
}

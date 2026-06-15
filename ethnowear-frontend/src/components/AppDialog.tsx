import type {ReactNode} from "react";
import {Dialog, DialogContent, DialogTitle, IconButton, useMediaQuery, useTheme} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";

type Props = {
    open: boolean
    title: string
    onClose: () => void
    children: ReactNode
    maxWidth?: 'sm' | 'md' | 'lg'
}

function AppDialog(props: Props) {
    const fullScreen = useMediaQuery(useTheme().breakpoints.down('sm'))

    return (
        <Dialog
            open={props.open}
            onClose={props.onClose}
            fullScreen={fullScreen}
            fullWidth
            maxWidth={props.maxWidth ?? 'sm'}>
            <DialogTitle
                sx={{
                    color: 'text.primary',
                    fontWeight: 800,
                }}
            >
                {props.title}
                <IconButton
                    aria-label="Close"
                    onClick={props.onClose}
                    sx={{
                        position: 'absolute',
                        right: 12,
                        top: 12,
                        color: 'text.secondary',
                }}>
                    <CloseIcon />
                </IconButton>
            </DialogTitle>
            <DialogContent dividers sx={{ pt: 3 }}>
                {props.children}
            </DialogContent>
        </Dialog>
    )
}

export default AppDialog

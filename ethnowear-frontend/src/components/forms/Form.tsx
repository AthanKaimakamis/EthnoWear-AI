import { Alert, Box, Button, Paper, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { FormState } from './formTypes.ts'

type FormProps = {
    title: string
    subtitle?: string
    state: FormState
    isPending: boolean
    formAction: (formData: FormData) => void
    submitLabel?: string
    pendingLabel?: string
    variant?: 'page' | 'dialog'
    children?: ReactNode
}

function Form({
    title,
    subtitle,
    state,
    isPending,
    formAction,
    submitLabel,
    pendingLabel,
    variant = 'page',
    children,
}: FormProps) {
    const { t } = useTranslation()

    const content = (
        <Stack spacing={3}>
            <Box>
                <Typography variant={variant === 'page' ? 'h4' : 'h6'} gutterBottom>
                    {title}
                </Typography>

                {subtitle && (
                    <Typography color="text.secondary">
                        {subtitle}
                    </Typography>
                )}
            </Box>

            {state.status === 'success' && state.message && (
                <Alert severity="success">
                    {state.message}
                </Alert>
            )}

            {state.status === 'error' && state.message && (
                <Alert severity="error">
                    {state.message}
                </Alert>
            )}

            <form action={formAction}>
                <Stack spacing={2}>
                    {children}

                    <Button
                        type="submit"
                        variant="contained"
                        disabled={isPending}
                    >
                        {isPending
                            ? pendingLabel ?? t('forms.saving')
                            : submitLabel ?? t('forms.save')}
                    </Button>
                </Stack>
            </form>
        </Stack>
    )

    if (variant === 'dialog') {
        return content
    }

    return <Paper sx={{ p: 3, maxWidth: 700 }}>{content}</Paper>
}

export default Form

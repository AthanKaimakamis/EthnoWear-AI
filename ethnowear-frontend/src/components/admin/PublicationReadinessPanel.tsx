import { Alert, AlertTitle, Button, CircularProgress, Stack, Typography } from '@mui/material'
import ArrowForwardOutlinedIcon from '@mui/icons-material/ArrowForwardOutlined'
import { useTranslation } from 'react-i18next'
import type {
    PublicationReadinessDetails,
    PublicationReadinessRequirement,
} from '../../types/archive'

type Props = {
    readiness: PublicationReadinessDetails | null
    loading: boolean
    errorMessages?: string[]
    onOpenRequirement: (requirement: PublicationReadinessRequirement) => void
}

export default function PublicationReadinessPanel({
    readiness,
    loading,
    errorMessages = [],
    onOpenRequirement,
}: Props) {
    const { t } = useTranslation()

    if (loading) {
        return (
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', py: 2 }}>
                <CircularProgress size={20} />
                <Typography color="text.secondary">{t('publication.readiness.loading')}</Typography>
            </Stack>
        )
    }

    if (errorMessages.length > 0) {
        return (
            <Alert severity="error">
                <AlertTitle>{t('publication.readiness.loadFailed')}</AlertTitle>
                {errorMessages.map(message => <Typography key={message} variant="body2">{message}</Typography>)}
            </Alert>
        )
    }

    if (!readiness) return <Alert severity="info">{t('publication.readiness.saveFirst')}</Alert>

    return (
        <Stack spacing={1.5}>
            <Alert severity={readiness.ready ? 'success' : 'warning'}>
                <AlertTitle>
                    {readiness.ready
                        ? t('publication.readiness.ready')
                        : t('publication.readiness.blocked')}
                </AlertTitle>
                {t('publication.readiness.backendAuthority')}
            </Alert>
            {readiness.requirements.map(requirement => (
                <Alert
                    key={`${requirement.key}:${requirement.field ?? ''}`}
                    severity={requirement.satisfied
                        ? 'success'
                        : requirement.severity === 'ERROR'
                            ? 'error'
                            : requirement.severity === 'WARNING'
                                ? 'warning'
                                : 'info'}
                    action={!requirement.satisfied && (
                        <Button
                            color="inherit"
                            size="small"
                            endIcon={<ArrowForwardOutlinedIcon />}
                            onClick={() => onOpenRequirement(requirement)}
                        >
                            {t('publication.readiness.fix')}
                        </Button>
                    )}
                >
                    <AlertTitle>{t(`publication.requirements.${requirement.key}`, requirement.key)}</AlertTitle>
                    {requirement.message ?? t(`publication.requirementMessages.${requirement.key}`)}
                </Alert>
            ))}
        </Stack>
    )
}

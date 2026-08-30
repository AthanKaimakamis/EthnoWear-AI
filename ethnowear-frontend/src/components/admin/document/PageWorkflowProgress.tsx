import { Box, LinearProgress, Stack, Step, StepLabel, Stepper, Tooltip, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { DocumentPageWorkflowProgress, DocumentPageWorkflowStep } from '../../../types/document'

type Props = {
    progress?: DocumentPageWorkflowProgress
    loading?: boolean
    compact?: boolean
}

export default function PageWorkflowProgress({ progress, loading = false, compact = false }: Props) {
    const { t } = useTranslation()
    if (loading || !progress) return <LinearProgress sx={{ width: compact ? 84 : '100%' }} />
    const visibleProgress = withoutOptionalVisionStep(progress)

    if (compact) {
        const content = <Stack spacing={.5} sx={{ minWidth: 88 }}>
            <Typography variant="body2" sx={{ fontWeight: 700 }}>{visibleProgress.completedSteps}/{visibleProgress.totalSteps}</Typography>
            <LinearProgress variant="determinate" value={visibleProgress.totalSteps ? visibleProgress.completedSteps / visibleProgress.totalSteps * 100 : 0} />
        </Stack>
        return <Tooltip title={<Stack>{visibleProgress.steps.map(step => <Typography key={step.step} variant="caption">{t(`documents.workflow.step.${step.step}`)}: {t(`documents.workflow.status.${step.status}`)}</Typography>)}</Stack>}>{content}</Tooltip>
    }

    return <Box>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>{t('documents.workflow.title', { completed: visibleProgress.completedSteps, total: visibleProgress.totalSteps })}</Typography>
        <Stepper activeStep={activeStep(visibleProgress.steps)} alternativeLabel sx={{ '& .MuiStepLabel-label': { mt: 1 } }}>
            {visibleProgress.steps.map(step => <Step key={step.step} completed={step.status === 'COMPLETED'}>
                <StepLabel error={step.status === 'FAILED'} optional={<Typography variant="caption">{t(`documents.workflow.status.${step.status}`)}</Typography>}>
                    {t(`documents.workflow.step.${step.step}`)}
                </StepLabel>
            </Step>)}
        </Stepper>
    </Box>
}

function withoutOptionalVisionStep(progress: DocumentPageWorkflowProgress): DocumentPageWorkflowProgress {
    const visionStep = progress.steps.find(step => step.step === 'VISION_OCR_ASSESSMENT')
    if (!visionStep) return progress

    return {
        ...progress,
        steps: progress.steps.filter(step => step.step !== 'VISION_OCR_ASSESSMENT'),
        completedSteps: Math.max(0, progress.completedSteps - (visionStep.status === 'COMPLETED' ? 1 : 0)),
        totalSteps: Math.max(0, progress.totalSteps - 1),
    }
}

function activeStep(steps: DocumentPageWorkflowStep[]) {
    const index = steps.findIndex(step => step.status !== 'COMPLETED')
    return index < 0 ? steps.length : index
}

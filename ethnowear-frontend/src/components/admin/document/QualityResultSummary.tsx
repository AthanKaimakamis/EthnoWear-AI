import { Box, Chip, LinearProgress, Paper, Stack, Typography } from '@mui/material'
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlineOutlined'
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlineOutlined'
import { useTranslation } from 'react-i18next'
import { apiEnumLabel } from '../../../app/apiEnumLabels'
import type { DocumentPageQualitySummary } from '../../../types/document'

type Props = {
    quality: DocumentPageQualitySummary
    compact?: boolean
}

export default function QualityResultSummary({ quality, compact = false }: Props) {
    const { t } = useTranslation()
    const percentage = quality.percentage == null
        ? null
        : Math.max(0, Math.min(100, Number(quality.percentage)))
    const content = <Stack spacing={compact ? .5 : 1.25}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between', minWidth: 0 }}>
            <Chip size="small" variant="outlined" label={apiEnumLabel(t, 'qualityStatus', quality.qualityLevel)} />
            <Typography variant={compact ? 'body2' : 'h6'} sx={{ fontWeight: 800, fontVariantNumeric: 'tabular-nums' }}>
                {percentage == null ? '—' : `${Math.round(percentage)}%`}
            </Typography>
        </Stack>
        {percentage != null && <LinearProgress variant="determinate" value={percentage} color={percentage >= 80 ? 'success' : percentage >= 60 ? 'warning' : 'error'} />}
        {!compact && <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap' }}>
            <Stack direction="row" spacing={.5} sx={{ alignItems: 'center' }}><CheckCircleOutlineIcon color="success" fontSize="small" /><Typography variant="body2">{t('documents.quality.passed', { count: quality.passedChecks })}</Typography></Stack>
            <Stack direction="row" spacing={.5} sx={{ alignItems: 'center' }}><ErrorOutlineIcon color={quality.failedChecks > 0 ? 'error' : 'disabled'} fontSize="small" /><Typography variant="body2">{t('documents.quality.failed', { count: quality.failedChecks })}</Typography></Stack>
        </Stack>}
    </Stack>

    return compact ? <Box sx={{ minWidth: 130 }}>{content}</Box> : <Paper variant="outlined" sx={{ p: 2 }}>{content}</Paper>
}

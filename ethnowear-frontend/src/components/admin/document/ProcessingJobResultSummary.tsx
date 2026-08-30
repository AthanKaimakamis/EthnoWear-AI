import { Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { ProcessingJobResult } from '../../../types/document'
import QualityResultSummary from './QualityResultSummary'

type Props = {
    result?: ProcessingJobResult
    compact?: boolean
}

export default function ProcessingJobResultSummary({ result, compact = false }: Props) {
    const { t } = useTranslation()
    if (result?.qualityAssessment) return <QualityResultSummary quality={result.qualityAssessment} compact={compact} />
    if (result?.ocrResultId) return <Typography variant="body2">{t('curator.processing.ocrResult')}</Typography>
    if (result?.producedMediaAssetIds.length) return <Typography variant="body2">{t('curator.processing.mediaResult', { count: result.producedMediaAssetIds.length })}</Typography>
    return <Typography variant="body2" color="text.secondary">{t('curator.processing.noResult')}</Typography>
}

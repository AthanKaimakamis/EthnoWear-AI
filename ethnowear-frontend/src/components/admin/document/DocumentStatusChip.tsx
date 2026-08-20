import { Chip } from '@mui/material'
import type { ChipProps } from '@mui/material/Chip'
import { useTranslation } from 'react-i18next'

type StatusKind = 'processing' | 'review' | 'trust' | 'indexing' | 'job' | 'provenance'

const colors: Record<StatusKind, Record<string, ChipProps['color']>> = {
    processing: { COMPLETED: 'success', FAILED: 'error', CANCELLED: 'default', PROCESSING: 'info', PENDING: 'warning', UPLOADED: 'default' },
    review: { APPROVED: 'success', REJECTED: 'error', REVIEW_REQUIRED: 'warning', IN_REVIEW: 'info', NOT_READY: 'default' },
    trust: { VERIFIED: 'success', TRUSTED: 'success', UNTRUSTED: 'error', PARTIAL: 'warning', UNKNOWN: 'default' },
    indexing: { INDEXED: 'success', FAILED: 'error', OUTDATED: 'warning', PENDING: 'info', NOT_ELIGIBLE: 'default' },
    job: { SUCCEEDED: 'success', FAILED: 'error', DEAD: 'error', TIMED_OUT: 'error', RUNNING: 'info', CLAIMED: 'info', QUEUED: 'warning', RETRY_WAIT: 'warning', CANCEL_REQUESTED: 'warning', CANCELLED: 'default' },
    provenance: { KNOWN_SOURCE: 'success', PARTIAL_SOURCE: 'warning', UNKNOWN_SOURCE: 'default' },
}

type Props = { kind: StatusKind; value: string; size?: ChipProps['size'] }

export default function DocumentStatusChip({ kind, value, size = 'small' }: Props) {
    const { t } = useTranslation()
    return <Chip size={size} color={colors[kind][value] ?? 'default'} variant="outlined" label={t(`documents.status.${kind}.${value}`, { defaultValue: value })} />
}

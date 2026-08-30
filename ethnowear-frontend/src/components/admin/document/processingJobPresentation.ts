import type { TFunction } from 'i18next'
import type { DocumentJobStatus, ProcessingJobCapabilities } from '../../../types/document'

const knownErrorCodes = new Set([
    'OCR_LAYOUT_INVALID',
    'VISION_RESPONSE_TRUNCATED',
    'VISION_RESPONSE_JSON_INVALID',
    'VISION_RESPONSE_SCHEMA_INVALID',
    'VISION_ISSUE_EXCERPT_UNGROUNDED',
    'VISION_NUMBER_GROUNDING_INVALID',
    'VISION_SUGGESTION_EXPANSION_INVALID',
    'VISION_SUGGESTION_OVERLAP_INVALID',
    'VISION_CONTRACT_INVALID',
])

export type ProcessingJobErrorPresentation = {
    code: string | null
    title: string
    message: string
    guidance: string | null
}

export function processingJobErrorPresentation(t: TFunction, errorCode: string | null | undefined): ProcessingJobErrorPresentation {
    const code = errorCode?.trim() || null
    const key = code && knownErrorCodes.has(code) ? code : 'UNKNOWN'

    return {
        code,
        title: t(`documents.processingErrors.${key}.title`),
        message: t(`documents.processingErrors.${key}.message`),
        guidance: key === 'OCR_LAYOUT_INVALID' ? t(`documents.processingErrors.${key}.guidance`) : null,
    }
}

type ActionableJob = {
    status: DocumentJobStatus | 'WAITING_RETRY'
    capabilities: ProcessingJobCapabilities
}

type ReplaceableJob = ActionableJob & {
    versionToken: string | null
}

export function isAutomaticRetryPending(status: ActionableJob['status']) {
    return status === 'RETRY_WAIT' || status === 'WAITING_RETRY'
}

export function canManuallyRetryJob(job: ActionableJob) {
    return job.status === 'FAILED' && job.capabilities.retryable
}

export function canCreateReplacementJob(job: ReplaceableJob) {
    if (!job.capabilities.cloneable || !job.versionToken || isAutomaticRetryPending(job.status)) return false
    if (job.status === 'CLAIMED' || job.status === 'RUNNING' || job.status === 'QUEUED' || job.status === 'CANCEL_REQUESTED') return false
    if (job.status === 'FAILED') return !job.capabilities.retryable
    return job.status === 'SUCCEEDED' || job.status === 'CANCELLED' || job.status === 'TIMED_OUT' || job.status === 'DEAD'
}

import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Box, Button, Chip, CircularProgress, Divider, LinearProgress, Paper, Stack, TextField, Typography } from '@mui/material'
import CheckOutlinedIcon from '@mui/icons-material/CheckOutlined'
import CloseOutlinedIcon from '@mui/icons-material/CloseOutlined'
import PsychologyOutlinedIcon from '@mui/icons-material/PsychologyOutlined'
import ReplayOutlinedIcon from '@mui/icons-material/ReplayOutlined'
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined'
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
    applyPageTextSuggestionIssue,
    createReplacementProcessingJob,
    documentQueryKeys,
    getCurrentPageOcr,
    getCurrentPageTextSuggestion,
    listPageQualityHistory,
    listPageTextSuggestionHistory,
    listProcessingJobs,
    processingQueryKeys,
    queuePageVisionAssessment,
    retryProcessingJob,
} from '../../../api/DocumentAdminApi'
import { ApiError, apiErrorMessage } from '../../../api/http'
import type {
    DocumentJobStatus,
    DocumentPageQualityAssessment,
    DocumentPageTextSuggestion,
    DocumentPageWorkflowProgress,
    ProcessingJobSummary,
    TextSuggestionIssue,
} from '../../../types/document'
import ProcessingJobErrorSummary from './ProcessingJobErrorSummary'
import ConfirmDialog from '../ConfirmDialog'
import SynchronizedTextDiff from './SynchronizedTextDiff'
import { canCreateReplacementJob, canManuallyRetryJob, isAutomaticRetryPending } from './processingJobPresentation'
import { buildIssueComparison, buildIssueDisplayComparison, type IssueComparison as IssueComparisonData } from './visionSuggestion'

type Props = {
    documentId: number
    pageId: number
    workflow?: DocumentPageWorkflowProgress
    visionQuality?: DocumentPageQualityAssessment
    canProcess: boolean
    canApplySuggestions: boolean
    hasUnsavedChanges: boolean
    currentText: string
    editableText: string
    onEditableTextChange: (value: string) => void
    onOpenTextEditor: (startOffset: number | null, endOffset: number | null, originalText: string | null) => void
}

type ActiveJobHint = { id: number; status: DocumentJobStatus }
type PendingIssue = { issue: TextSuggestionIssue; index: number }

const HISTORY_PAGE_SIZE = 20
const ACTIVE_JOB_STATUSES = new Set<DocumentJobStatus>(['QUEUED', 'CLAIMED', 'RETRY_WAIT', 'RUNNING', 'CANCEL_REQUESTED'])
const VISION_JOB_QUERY = {
    size: HISTORY_PAGE_SIZE,
    sort: 'createdAt,desc',
    jobType: 'VISION_OCR_ASSESSMENT' as const,
}

export default function VisionAssessmentPanel({ documentId, pageId, workflow, visionQuality, canProcess, canApplySuggestions, hasUnsavedChanges, currentText, editableText, onEditableTextChange, onOpenTextEditor }: Props) {
    const { t } = useTranslation()
    const queryClient = useQueryClient()
    const requestInFlight = useRef(false)
    const previousLatestStatus = useRef<string | null>(null)
    const [pending, setPending] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [activeJobHint, setActiveJobHint] = useState<ActiveJobHint | null>(null)
    const [ignoredIssues, setIgnoredIssues] = useState<Set<string>>(new Set())
    const [appliedIssues, setAppliedIssues] = useState<Set<string>>(new Set())
    const [pendingIssue, setPendingIssue] = useState<PendingIssue | null>(null)
    const [applyingIssue, setApplyingIssue] = useState(false)

    const currentOcrQuery = useQuery({
        queryKey: documentQueryKeys.pageCurrentOcr(documentId, pageId),
        queryFn: ({ signal }) => getCurrentPageOcr(documentId, pageId, signal),
    })

    const suggestionQuery = useQuery({
        queryKey: documentQueryKeys.pageSuggestion(documentId, pageId),
        queryFn: ({ signal }) => getCurrentPageTextSuggestion(pageId, signal),
    })
    const qualityHistoryQuery = useInfiniteQuery({
        queryKey: documentQueryKeys.pageQualityHistory(documentId, pageId, { page: 0, size: HISTORY_PAGE_SIZE }),
        queryFn: ({ pageParam, signal }) => listPageQualityHistory(documentId, pageId, { page: pageParam, size: HISTORY_PAGE_SIZE }, signal),
        initialPageParam: 0,
        getNextPageParam: page => page.last ? undefined : page.number + 1,
    })
    const jobsQuery = useInfiniteQuery({
        queryKey: processingQueryKeys.jobs({ ...VISION_JOB_QUERY, page: 0, documentId, documentPageId: pageId }),
        queryFn: ({ pageParam, signal }) => listProcessingJobs({ ...VISION_JOB_QUERY, page: pageParam, documentId, documentPageId: pageId }, signal),
        initialPageParam: 0,
        getNextPageParam: page => page.last ? undefined : page.number + 1,
        refetchInterval: query => {
            const pages = query.state.data?.pages ?? []
            return activeJobHint || pages.some(page => page.content.some(job => isActiveJobStatus(job.status))) ? 4_000 : false
        },
    })

    const jobs = useMemo(() => jobsQuery.data?.pages.flatMap(page => page.content) ?? [], [jobsQuery.data])
    const qualityAssessments = useMemo(() => qualityHistoryQuery.data?.pages.flatMap(page => page.content) ?? [], [qualityHistoryQuery.data])
    const qualityByJob = useMemo(() => new Map(qualityAssessments.flatMap(item => item.processingJobId === null ? [] : [[item.processingJobId, item] as const])), [qualityAssessments])
    const visionStep = workflow?.steps.find(step => step.step === 'VISION_OCR_ASSESSMENT')
    const latestJob = jobs[0]
    const listedActiveJob = jobs.find(job => isActiveJobStatus(job.status))
    const workflowActive = visionStep?.jobStatus ? isActiveJobStatus(visionStep.jobStatus) : false
    const activeJob = listedActiveJob ?? activeJobHint
    const visionActive = jobsQuery.isSuccess ? Boolean(listedActiveJob || activeJobHint) : Boolean(activeJob || workflowActive)
    const suggestion = suggestionQuery.data
    const failureCode = latestJob?.status === 'FAILED' ? latestJob.error?.code ?? null : null
    const latestQuality = latestJob ? qualityByJob.get(latestJob.id) ?? visionQuality : visionQuality
    const rejectedSignal = latestQuality?.signals.find(signal => signal.signalType === 'VISION_SUGGESTION_REJECTED')
    const automaticRetryPending = latestJob ? isAutomaticRetryPending(latestJob.status) : false
    const canRetryFailure = latestJob ? canManuallyRetryJob(latestJob) : false
    const canReplaceFailure = latestJob ? canCreateReplacementJob(latestJob) : false
    const structuredSuggestion = suggestion ? isStructuredVisionSuggestion(suggestion) : false
    const suggestionStale = Boolean(suggestion && currentOcrQuery.data && suggestion.documentPageOcrResultId !== currentOcrQuery.data.id)

    useEffect(() => {
        setIgnoredIssues(new Set())
        setAppliedIssues(new Set())
        setPendingIssue(null)
    }, [pageId, suggestion?.id])

    async function applyCorrection() {
        if (!pendingIssue || !suggestion || applyingIssue || hasUnsavedChanges) return
        const target = pendingIssue
        setApplyingIssue(true)
        setError(null)
        try {
            const review = await applyPageTextSuggestionIssue(pageId, suggestion.id, target.index, suggestion.editableTextHash)
            if (review.correctedTextSnapshot !== null) onEditableTextChange(review.correctedTextSnapshot)
            setAppliedIssues(current => new Set(current).add(issueKey(target.issue, target.index)))
            setPendingIssue(null)
            await refreshVisionData()
        } catch (caught) {
            if (caught instanceof ApiError && caught.status === 409) {
                setError(t('documents.vision.applyErrors.stale'))
                setPendingIssue(null)
                await refreshVisionData()
            } else if (caught instanceof ApiError && caught.status === 400) {
                setError(t('documents.vision.applyErrors.invalid'))
            } else if (caught instanceof ApiError && caught.status === 404) {
                setError(t('documents.vision.applyErrors.missing'))
                setPendingIssue(null)
                await refreshVisionData()
            } else setError(apiErrorMessage(caught, t('documents.vision.applyErrors.failed')))
        } finally {
            setApplyingIssue(false)
        }
    }

    async function refreshVisionData() {
        await Promise.all([
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.page(documentId, pageId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.pageWorkflow(documentId, pageId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.pageSuggestion(documentId, pageId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.pagesRoot(documentId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.progress(documentId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.indexing(documentId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.chunkEligibility(documentId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.generatedChunksRoot(documentId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.jobsRoot(documentId) }),
            queryClient.invalidateQueries({ queryKey: processingQueryKeys.all }),
        ])
    }

    useEffect(() => {
        if (!activeJobHint) return
        const hintedJob = jobs.find(job => job.id === activeJobHint.id)
        if (hintedJob && !isActiveJobStatus(hintedJob.status)) setActiveJobHint(null)
    }, [activeJobHint, jobs])

    useEffect(() => {
        if (!latestJob) return
        const current = `${latestJob.id}:${latestJob.status}`
        if (previousLatestStatus.current && previousLatestStatus.current !== current) void refreshVisionData()
        previousLatestStatus.current = current
    }, [latestJob?.id, latestJob?.status])

    async function requestAssessment(mode: 'request' | 'retry' | 'replace' = 'request') {
        if (requestInFlight.current || visionActive) return
        requestInFlight.current = true
        setPending(true)
        setError(null)
        try {
            const job = mode === 'retry' && latestJob
                ? await retryProcessingJob(latestJob.id)
                : mode === 'replace' && latestJob?.versionToken
                    ? await createReplacementProcessingJob(latestJob.id, latestJob.versionToken)
                    : await queuePageVisionAssessment(pageId)
            setActiveJobHint({ id: job.id, status: job.status })
            await refreshVisionData()
        } catch (caught) {
            const conflict = activeVisionConflict(caught)
            if (conflict) {
                setActiveJobHint(conflict)
                await refreshVisionData()
            } else setError(apiErrorMessage(caught, t('documents.vision.actionFailed')))
        } finally {
            requestInFlight.current = false
            setPending(false)
        }
    }

    return <Stack spacing={1.5}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
            <Box>
                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>{t('documents.vision.title')}</Typography>
                <Typography variant="body2" color="text.secondary">{t('documents.vision.advisory')}</Typography>
            </Box>
            {canProcess && <Button
                size="small"
                variant="outlined"
                startIcon={pending ? <CircularProgress size={16} /> : <PsychologyOutlinedIcon />}
                disabled={pending || visionActive}
                onClick={() => void requestAssessment('request')}
            >
                {t('documents.vision.request')}
            </Button>}
        </Stack>

        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
        {visionActive && <Alert severity="info" icon={<PsychologyOutlinedIcon />}>
            <Stack spacing={1}>
                <Typography>{t('documents.vision.processing')}</Typography>
                <LinearProgress />
            </Stack>
        </Alert>}
        {automaticRetryPending && <Alert severity="info"><Typography sx={{ fontWeight: 700 }}>{t('curator.processing.automaticRetry.title')}</Typography><Typography variant="body2">{t('curator.processing.automaticRetry.description')}</Typography></Alert>}
        {failureCode && !automaticRetryPending && <Stack spacing={1}>
            <ProcessingJobErrorSummary errorCode={failureCode} />
            {canProcess && <Stack direction="row" spacing={1}>
                {canRetryFailure && <Button size="small" variant="outlined" startIcon={<ReplayOutlinedIcon />} disabled={pending || visionActive} onClick={() => void requestAssessment('retry')}>{t('curator.processing.retryJob')}</Button>}
                {canReplaceFailure && <Button size="small" variant="outlined" startIcon={<ReplayOutlinedIcon />} disabled={pending || visionActive} onClick={() => void requestAssessment('replace')}>{t('curator.processing.createReplacement')}</Button>}
            </Stack>}
        </Stack>}
        {rejectedSignal && <Alert severity="warning" icon={<WarningAmberOutlinedIcon />}>
            <Typography sx={{ fontWeight: 700 }}>{t('documents.vision.rejected.title')}</Typography>
            <Typography variant="body2">{t('documents.vision.rejected.description')}</Typography>
        </Alert>}
        {suggestionStale && <Alert severity="warning">{t('documents.vision.staleSnapshot')}</Alert>}

        {suggestionQuery.isPending && <LinearProgress />}
        {!suggestionQuery.isPending && !suggestion && !visionActive && !failureCode && jobs.length === 0 && <Alert severity="info">{t('documents.vision.notRequested')}</Alert>}
        {suggestion && <Stack spacing={1.5}>
            <Alert severity="info">{t('documents.vision.guidanceOnly')}</Alert>
            {suggestion.requiresHumanAttention && <Chip size="small" color="warning" label={t('documents.vision.attention')} sx={{ alignSelf: 'flex-start' }} />}
            {structuredSuggestion ? <>
                {suggestion.issues.length === 0 && <Typography color="text.secondary">{t('documents.vision.noIssues')}</Typography>}
                {suggestion.issues.map((issue, index) => {
                    const key = issueKey(issue, index)
                    if (ignoredIssues.has(key)) return <Paper key={key} variant="outlined" sx={{ p: 1.5, bgcolor: 'action.hover' }}>
                        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
                            <Typography variant="body2" color="text.secondary">{t('documents.vision.ignoredIssue', { number: index + 1 })}</Typography>
                            <Button size="small" onClick={() => setIgnoredIssues(current => without(current, key))}>{t('documents.vision.undoIgnore')}</Button>
                        </Stack>
                    </Paper>
                    const placement = buildIssueComparison(issue, currentText)
                    const comparison = buildIssueDisplayComparison(issue, currentText)
                    const placementValid = Boolean(placement)
                    return <IssueComparisonView
                        key={key}
                        issue={issue}
                        index={index}
                        comparison={comparison}
                        placementValid={placementValid}
                        stale={suggestionStale || currentOcrQuery.isPending || currentOcrQuery.isError}
                        applied={appliedIssues.has(key)}
                        hasUnsavedChanges={hasUnsavedChanges}
                        canApplySuggestions={canApplySuggestions}
                        applying={applyingIssue && pendingIssue?.index === index}
                        onApply={() => setPendingIssue({ issue, index })}
                        onApplyAdjusted={replacement => {
                            if (!placement || hasUnsavedChanges) return
                            const updated = replaceIssueInDraft(editableText, issue, placement, replacement)
                            if (updated === null) return
                            onEditableTextChange(updated)
                            setAppliedIssues(current => new Set(current).add(key))
                        }}
                        onOpenEditor={() => onOpenTextEditor(issue.startOffset, issue.endOffset, issue.originalText)}
                        onIgnore={() => setIgnoredIssues(current => new Set(current).add(key))}
                    />
                })}
            </> : <Paper variant="outlined" sx={{ p: 2 }}>
                <Typography sx={{ fontWeight: 700 }}>{t('documents.vision.legacyTitle')}</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>{t('documents.vision.legacyDescription')}</Typography>
                <SynchronizedTextDiff
                    original={currentText}
                    suggested={suggestion.suggestedText}
                    originalTitle={t('documents.vision.currentText')}
                    suggestedTitle={t('documents.vision.legacySuggestion')}
                    synchronizedLabel={t('documents.vision.synchronizedScroll')}
                    changesLabel={count => t('documents.vision.changedParts', { count })}
                    copySuggestedLabel={t('documents.pageReview.copySuggested')}
                    copiedSuggestedLabel={t('documents.pageReview.copiedSuggested')}
                    applySuggestedLabel={t('documents.vision.applyCorrection')}
                    onApplySuggested={() => onEditableTextChange(suggestion.suggestedText)}
                />
            </Paper>}
            {suggestion.uncertainPassages.length > 0 && <Paper variant="outlined" sx={{ p: 2 }}>
                <Typography sx={{ fontWeight: 700, mb: 1 }}>{t('documents.vision.uncertain')}</Typography>
                <Stack spacing={1.5}>{suggestion.uncertainPassages.map((passage, index) => <Box key={index}>
                    <Typography component="blockquote" sx={{ m: 0, whiteSpace: 'pre-wrap' }}>{passage.excerpt}</Typography>
                    <Typography variant="body2" color="text.secondary">{passage.reason}{passage.confidence !== null ? ` · ${t('documents.vision.confidence', { value: Math.round(passage.confidence * 100) })}` : ''}</Typography>
                </Box>)}</Stack>
            </Paper>}
        </Stack>}

        <ConfirmDialog
            open={pendingIssue !== null}
            title={t('documents.vision.applyConfirm.title')}
            confirmLabel={t('documents.vision.applyConfirm.confirm')}
            confirmColor="primary"
            pending={applyingIssue}
            onCancel={() => setPendingIssue(null)}
            onConfirm={() => void applyCorrection()}
        >{t('documents.vision.applyConfirm.description')}</ConfirmDialog>
    </Stack>
}

export function VisionAssessmentHistoryPanel({ documentId, pageId }: { documentId: number; pageId: number }) {
    const { t } = useTranslation()
    const jobsQuery = useInfiniteQuery({
        queryKey: processingQueryKeys.jobs({ ...VISION_JOB_QUERY, page: 0, documentId, documentPageId: pageId }),
        queryFn: ({ pageParam, signal }) => listProcessingJobs({ ...VISION_JOB_QUERY, page: pageParam, documentId, documentPageId: pageId }, signal),
        initialPageParam: 0,
        getNextPageParam: page => page.last ? undefined : page.number + 1,
    })
    const suggestionHistoryQuery = useInfiniteQuery({
        queryKey: documentQueryKeys.pageSuggestionHistory(documentId, pageId, { page: 0, size: HISTORY_PAGE_SIZE }),
        queryFn: ({ pageParam, signal }) => listPageTextSuggestionHistory(pageId, { page: pageParam, size: HISTORY_PAGE_SIZE }, signal),
        initialPageParam: 0,
        getNextPageParam: page => page.last ? undefined : page.number + 1,
    })
    const qualityHistoryQuery = useInfiniteQuery({
        queryKey: documentQueryKeys.pageQualityHistory(documentId, pageId, { page: 0, size: HISTORY_PAGE_SIZE }),
        queryFn: ({ pageParam, signal }) => listPageQualityHistory(documentId, pageId, { page: pageParam, size: HISTORY_PAGE_SIZE }, signal),
        initialPageParam: 0,
        getNextPageParam: page => page.last ? undefined : page.number + 1,
    })
    const jobs = jobsQuery.data?.pages.flatMap(page => page.content) ?? []
    const suggestions = suggestionHistoryQuery.data?.pages.flatMap(page => page.content) ?? []
    const qualityAssessments = qualityHistoryQuery.data?.pages.flatMap(page => page.content) ?? []
    const suggestionByJob = new Map(suggestions.map(item => [item.processingJobId, item]))
    const qualityByJob = new Map(qualityAssessments.flatMap(item => item.processingJobId === null ? [] : [[item.processingJobId, item] as const]))
    const loading = jobsQuery.isPending || suggestionHistoryQuery.isPending || qualityHistoryQuery.isPending
    const failed = jobsQuery.isError || suggestionHistoryQuery.isError || qualityHistoryQuery.isError
    const [showOlder, setShowOlder] = useState(false)

    if (loading) return <Box sx={{ minHeight: 180, display: 'grid', placeItems: 'center' }}><CircularProgress size={28} /></Box>
    if (failed) return <Alert severity="error">{t('documents.pageReview.history.loadFailed')}</Alert>
    if (jobs.length === 0) return <Typography color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>{t('documents.pageReview.history.vision.empty')}</Typography>

    return <Stack spacing={1}>
        {jobs.slice(0, 1).map(job => <AttemptCard
            key={job.id}
            job={job}
            suggestion={suggestionByJob.get(job.id)}
            rejected={job.error?.code === 'VISION_SUGGESTION_REJECTED' || Boolean(qualityByJob.get(job.id)?.signals.some(signal => signal.signalType === 'VISION_SUGGESTION_REJECTED'))}
            latest
        />)}
        {jobs.length > 1 && <Button size="small" onClick={() => setShowOlder(value => !value)} sx={{ alignSelf: 'flex-start' }}>{t(showOlder ? 'documents.vision.hideOlderAttempts' : 'documents.vision.showOlderAttempts', { count: jobs.length - 1 })}</Button>}
        {showOlder && jobs.slice(1).map(job => <AttemptCard
            key={job.id}
            job={job}
            suggestion={suggestionByJob.get(job.id)}
            rejected={job.error?.code === 'VISION_SUGGESTION_REJECTED' || Boolean(qualityByJob.get(job.id)?.signals.some(signal => signal.signalType === 'VISION_SUGGESTION_REJECTED'))}
        />)}
        {showOlder && (jobsQuery.hasNextPage || suggestionHistoryQuery.hasNextPage || qualityHistoryQuery.hasNextPage) && <Button
            size="small"
            sx={{ alignSelf: 'flex-start' }}
            disabled={jobsQuery.isFetchingNextPage || suggestionHistoryQuery.isFetchingNextPage || qualityHistoryQuery.isFetchingNextPage}
            onClick={() => void Promise.all([
                jobsQuery.hasNextPage ? jobsQuery.fetchNextPage() : Promise.resolve(),
                suggestionHistoryQuery.hasNextPage ? suggestionHistoryQuery.fetchNextPage() : Promise.resolve(),
                qualityHistoryQuery.hasNextPage ? qualityHistoryQuery.fetchNextPage() : Promise.resolve(),
            ])}
        >{t('documents.vision.loadMoreHistory')}</Button>}
    </Stack>
}

function AttemptCard({ job, suggestion, rejected, latest = false }: { job: ProcessingJobSummary; suggestion?: DocumentPageTextSuggestion | null; rejected: boolean; latest?: boolean }) {
    const { t, i18n } = useTranslation()
    const structuredSuggestion = suggestion ? isStructuredVisionSuggestion(suggestion) : false
    const outcome = rejected
        ? t('documents.vision.rejectedResult')
        : suggestion?.applied
            ? t('documents.vision.accepted')
            : suggestion && structuredSuggestion && suggestion.issues.length > 0
                ? t('documents.vision.issuesOnly', { count: suggestion.issues.length })
                : suggestion && structuredSuggestion
                    ? t('documents.vision.completedWithoutIssues')
                    : suggestion
                        ? t('documents.vision.legacyResult')
                        : job.status === 'FAILED' || job.status === 'DEAD' || job.status === 'TIMED_OUT'
                            ? t('documents.vision.failedResult')
                            : null

    return <Paper variant="outlined" sx={{ p: 1.5, bgcolor: latest ? 'action.hover' : 'background.paper' }}>
        <Stack spacing={1}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
                {latest && <Typography variant="overline" color="primary" sx={{ fontWeight: 700 }}>{t('documents.vision.latestAttempt')}</Typography>}
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <Chip size="small" label={t(`documents.status.job.${job.status}`)} />
                    <Typography variant="caption" color="text.secondary">{new Date(job.timestamps.createdAt).toLocaleString(i18n.language)}</Typography>
                </Stack>
            </Stack>
            {outcome && <Typography variant="body2">{outcome}</Typography>}
            {job.error?.code && <ProcessingJobErrorSummary errorCode={job.error.code} compact />}
        </Stack>
    </Paper>
}

function IssueComparisonView({ issue, index, comparison, placementValid, stale, applied, hasUnsavedChanges, canApplySuggestions, applying, onApply, onApplyAdjusted, onOpenEditor, onIgnore }: {
    issue: TextSuggestionIssue
    index: number
    comparison: IssueComparisonData | null
    placementValid: boolean
    stale: boolean
    applied: boolean
    hasUnsavedChanges: boolean
    canApplySuggestions: boolean
    applying: boolean
    onApply: () => void
    onApplyAdjusted: (replacement: string) => void
    onOpenEditor: () => void
    onIgnore: () => void
}) {
    const { t } = useTranslation()
    const [replacement, setReplacement] = useState(issue.suggestedText ?? '')
    useEffect(() => setReplacement(issue.suggestedText ?? ''), [issue.suggestedText])
    const reason = localizedIssueReason(issue, t)
    const exactActionable = Boolean(issue.safelyApplicable && issue.suggestedText !== null && comparison && placementValid)
    const adjustable = Boolean(issue.suggestedText !== null && !exactActionable)
    const canApply = Boolean(canApplySuggestions && exactActionable && !stale && !applied && !hasUnsavedChanges && !applying)
    const canApplyAdjusted = Boolean(canApplySuggestions && adjustable && placementValid && replacement.trim() && !stale && !applied && !hasUnsavedChanges)

    return <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={1.5}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
                <Typography sx={{ fontWeight: 700 }}>{t('documents.vision.issueTitle', { number: index + 1 })}</Typography>
                {issue.confidence !== null && <Chip size="small" label={t('documents.vision.confidence', { value: Math.round(issue.confidence * 100) })} />}
            </Stack>
            <Box><Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>{t('documents.vision.reason')}</Typography><Typography variant="body2">{reason}</Typography></Box>
            {issue.suggestedText !== null && comparison && <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'repeat(2, minmax(0, 1fr))' }, gap: 1.5 }}>
                <HighlightedText title={t('documents.vision.before')} excerpt={exactActionable ? { prefix: '', changed: comparison.before.changed, suffix: '' } : comparison.before} />
                {exactActionable
                    ? <HighlightedText title={t('documents.vision.after')} excerpt={{ prefix: '', changed: comparison.after.changed, suffix: '' }} replacement />
                    : <Stack spacing={1}>
                        <TextField fullWidth multiline minRows={3} label={t('documents.vision.adjustedReplacement')} value={replacement} onChange={event => setReplacement(event.target.value)} slotProps={{ htmlInput: { style: { fontFamily: 'monospace' } } }} />
                        <HighlightedText title={t('documents.vision.previewResult')} excerpt={{ prefix: comparison.before.prefix, changed: replacement, suffix: comparison.before.suffix }} replacement />
                    </Stack>}
            </Box>}
            {issue.suggestedText !== null && !comparison && <Alert severity="info">{t('documents.vision.placementUnknown')}</Alert>}
            {issue.suggestedText !== null && comparison && !placementValid && !stale && !applied && <Alert severity="info">{t('documents.vision.placementUnknown')}</Alert>}
            {issue.suggestedText === null && <Stack spacing={1}>
                {comparison && <HighlightedText title={t('documents.vision.originalExcerpt')} excerpt={comparison.before} />}
                <Alert severity="warning">{t('documents.vision.noReliableReplacement')}</Alert>
            </Stack>}
            {stale && issue.suggestedText !== null && <Alert severity="warning">{t('documents.vision.staleIssue')}</Alert>}
            {hasUnsavedChanges && issue.suggestedText !== null && !applied && <Alert severity="info">{t('documents.vision.saveDraftFirst')}</Alert>}
            {applied && <Alert severity="success" icon={<CheckOutlinedIcon />}>{t('documents.vision.appliedToDraft')}</Alert>}
            <Divider />
            <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
                <Button size="small" color="inherit" startIcon={<CloseOutlinedIcon />} onClick={onIgnore}>{t('documents.vision.ignore')}</Button>
                {issue.suggestedText === null && <Button size="small" variant="outlined" onClick={onOpenEditor}>{t('documents.vision.openInEditor')}</Button>}
                {canApplySuggestions && issue.suggestedText !== null && <Button
                    size="small"
                    variant="contained"
                    startIcon={applying ? <CircularProgress size={16} color="inherit" /> : <CheckOutlinedIcon />}
                    disabled={exactActionable ? !canApply : !canApplyAdjusted}
                    onClick={exactActionable ? onApply : () => onApplyAdjusted(replacement)}
                >
                    {t(exactActionable ? 'documents.vision.applyCorrection' : 'documents.vision.applyAdjustedToDraft')}
                </Button>}
            </Stack>
        </Stack>
    </Paper>
}

function HighlightedText({ title, excerpt, replacement = false }: { title: string; excerpt: { prefix: string; changed: string; suffix: string }; replacement?: boolean }) {
    return <Box sx={{ minWidth: 0 }}>
        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>{title}</Typography>
        <Box sx={{ mt: .5, p: 1.5, minHeight: 72, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', fontFamily: 'monospace', bgcolor: replacement ? 'rgba(46, 125, 50, .05)' : 'grey.50', border: 1, borderColor: replacement ? 'success.light' : 'divider' }}>
            {excerpt.prefix}<Box component="mark" sx={{ px: .25, bgcolor: replacement ? 'success.light' : 'warning.light', color: 'text.primary', fontWeight: 700, fontFamily: 'inherit' }}>{excerpt.changed}</Box>{excerpt.suffix}
        </Box>
    </Box>
}

function replaceIssueInDraft(editableText: string, issue: TextSuggestionIssue, comparison: IssueComparisonData, replacement: string) {
    if (comparison.startOffset < 0 || comparison.endOffset <= comparison.startOffset || issue.originalText === null) return null
    if (editableText.slice(comparison.startOffset, comparison.endOffset) !== issue.originalText) return null
    return editableText.slice(0, comparison.startOffset) + replacement + editableText.slice(comparison.endOffset)
}

function isActiveJobStatus(status: DocumentJobStatus) {
    return ACTIVE_JOB_STATUSES.has(status)
}

function activeVisionConflict(error: unknown): ActiveJobHint | null {
    if (!(error instanceof ApiError) || error.status !== 409 || !error.details || typeof error.details !== 'object') return null
    const details = error.details as Record<string, unknown>
    if (details.code !== 'VISION_JOB_ALREADY_ACTIVE' || !details.activeJob || typeof details.activeJob !== 'object') return null
    const activeJob = details.activeJob as Record<string, unknown>
    if (typeof activeJob.id !== 'number' || typeof activeJob.status !== 'string' || !ACTIVE_JOB_STATUSES.has(activeJob.status as DocumentJobStatus)) return null
    return { id: activeJob.id, status: activeJob.status as DocumentJobStatus }
}

function issueKey(issue: TextSuggestionIssue, index: number) {
    return `${issue.issueType}:${issue.startOffset ?? 'x'}:${issue.endOffset ?? 'x'}:${index}`
}

function without(values: Set<string>, value: string) {
    const next = new Set(values)
    next.delete(value)
    return next
}

export function isStructuredVisionSuggestion(suggestion: DocumentPageTextSuggestion) {
    if (suggestion.issues.length > 0 || suggestion.uncertainPassages.length > 0) return true
    const version = suggestion.promptVersion.trim().toLocaleLowerCase().match(/^vision-ocr-v(\d+)(?:[._-]|$)/)
    return version ? Number(version[1]) >= 2 : false
}

function localizedIssueReason(issue: TextSuggestionIssue, t: (key: string) => string) {
    if (issue.explanationBg.trim()) return issue.explanationBg
    return t('documents.vision.issueReasons.generic')
}

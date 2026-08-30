import { useEffect, useState } from 'react'
import {
    Alert, Box, Button, Chip, FormControl, InputLabel, MenuItem,
    Select, Skeleton, Stack, TextField, Typography,
} from '@mui/material'
import CheckOutlinedIcon from '@mui/icons-material/CheckOutlined'
import CloseOutlinedIcon from '@mui/icons-material/CloseOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import ImageNotSupportedOutlinedIcon from '@mui/icons-material/ImageNotSupportedOutlined'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
    approvePageFigure, getPageFigureContent, rejectPageFigure, updatePageFigure,
} from '../../../api/DocumentAdminApi'
import { sourceReferencesApi, sourcesApi } from '../../../api/ArchiveAdminApi'
import { apiErrorMessage } from '../../../api/http'
import type { SourceDetails, SourceReferenceDetails } from '../../../types/archive'
import type { DocumentPageFigure, FigureReviewState } from '../../../types/document'
import ConfirmDialog from '../ConfirmDialog'
import MediaOntologyLinksEditor from '../media/MediaOntologyLinksEditor'

type ReviewDecision = 'approve' | 'reject'

type Props = {
    figure: DocumentPageFigure
    canEdit: boolean
    canReview: boolean
    onChanged: (figure: DocumentPageFigure) => void
    onConflict: () => void
}

export default function DocumentFigureReviewEditor({ figure, canEdit, canReview, onChanged, onConflict }: Props) {
    const { t } = useTranslation()
    const [caption, setCaption] = useState(figure.correctedCaptionText ?? '')
    const [printedNumber, setPrintedNumber] = useState(figure.printedFigureNumber ?? '')
    const [sourceReferenceId, setSourceReferenceId] = useState(figure.sourceReferenceId ? String(figure.sourceReferenceId) : '')
    const [pending, setPending] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [reviewDecision, setReviewDecision] = useState<ReviewDecision | null>(null)
    const [reviewReason, setReviewReason] = useState('')
    const referencesQuery = useQuery({
        queryKey: ['admin', 'source-references', 'figure-review'],
        queryFn: ({ signal }) => Promise.all([sourceReferencesApi.findAll({ size: 1000 }, signal), sourcesApi.findAll({ size: 1000 }, signal)]),
        staleTime: 300_000,
    })

    useEffect(() => {
        setCaption(figure.correctedCaptionText ?? '')
        setPrintedNumber(figure.printedFigureNumber ?? '')
        setSourceReferenceId(figure.sourceReferenceId ? String(figure.sourceReferenceId) : '')
        setError(null)
        setReviewDecision(null)
        setReviewReason('')
    }, [figure.id, figure.version])

    const references = referencesQuery.data?.[0].content ?? []
    const sources = referencesQuery.data?.[1].content ?? []
    const sourceById = new Map(sources.map(source => [source.id, source]))
    const dirty = caption !== (figure.correctedCaptionText ?? '')
        || printedNumber !== (figure.printedFigureNumber ?? '')
        || sourceReferenceId !== (figure.sourceReferenceId ? String(figure.sourceReferenceId) : '')
    const effectiveCaption = caption.trim() || figure.rawCaptionText?.trim() || ''
    const approvalReady = Boolean(effectiveCaption && sourceReferenceId && figure.reviewState !== 'OUTDATED' && !dirty)

    async function save() {
        if (!canEdit || pending) return
        setPending(true)
        setError(null)
        try {
            const updated = await updatePageFigure(figure.documentPageId, figure.id, figure.version, {
                correctedCaptionText: caption.trim() || null,
                printedFigureNumber: printedNumber.trim() || null,
                sourceReferenceId: sourceReferenceId ? Number(sourceReferenceId) : null,
            })
            onChanged(updated)
        } catch (caught) {
            if (hasStatus(caught, 409)) onConflict()
            setError(figureError(caught, t('documents.figures.saveFailed'), t('documents.figures.concurrencyConflict')))
        } finally {
            setPending(false)
        }
    }

    async function review() {
        if (!reviewDecision || !reviewReason.trim() || pending) return
        setPending(true)
        setError(null)
        try {
            const updated = reviewDecision === 'approve'
                ? await approvePageFigure(figure.documentPageId, figure.id, figure.version, reviewReason.trim())
                : await rejectPageFigure(figure.documentPageId, figure.id, figure.version, reviewReason.trim())
            onChanged(updated)
            setReviewDecision(null)
            setReviewReason('')
        } catch (caught) {
            if (hasStatus(caught, 409)) onConflict()
            setError(figureError(caught, t('documents.figures.reviewFailed'), t('documents.figures.concurrencyConflict')))
        } finally {
            setPending(false)
        }
    }

    return <>
        <Stack spacing={1.5} sx={{ minHeight: 0 }}>
            {error && <Alert severity="error" onClose={() => setError(null)} sx={{ whiteSpace: 'pre-line' }}>{error}</Alert>}
            {figure.reviewState === 'OUTDATED' && <Alert severity="warning">{t('documents.figures.outdatedReviewBlocked')}</Alert>}
            {dirty && <Alert severity="info">{t('documents.figures.saveBeforeReview')}</Alert>}
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'minmax(220px, .8fr) minmax(300px, 1.2fr)' }, gap: 2, minHeight: 0 }}>
                <Box sx={{ minHeight: 240, maxHeight: 430, bgcolor: 'grey.100', border: 1, borderColor: 'divider', overflow: 'hidden' }}>
                    <ProtectedFigureImage pageId={figure.documentPageId} figureId={figure.id} alt={effectiveCaption || t('documents.figures.unnamed')} />
                </Box>
                <Stack spacing={1.5}>
                    <Stack direction="row" sx={{ gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
                        <FigureStateChip state={figure.reviewState} />
                        <Chip size="small" variant="outlined" label={t('documents.figures.confidence', { value: formatConfidence(figure.detectionConfidence) })} />
                    </Stack>
                    <TextField size="small" label={t('documents.figures.detectedCaption')} value={figure.rawCaptionText ?? ''} multiline minRows={2} slotProps={{ input: { readOnly: true } }} />
                    <TextField size="small" label={t('documents.figures.correctedCaption')} required value={caption} multiline minRows={3} disabled={!canEdit || pending || figure.reviewState === 'OUTDATED'} onChange={event => setCaption(event.target.value.slice(0, 2000))} />
                    <TextField size="small" label={t('documents.figures.printedNumber')} value={printedNumber} disabled={!canEdit || pending || figure.reviewState === 'OUTDATED'} onChange={event => setPrintedNumber(event.target.value.slice(0, 100))} />
                    <FormControl size="small" required disabled={!canEdit || pending || figure.reviewState === 'OUTDATED' || referencesQuery.isPending}>
                        <InputLabel id={`figure-source-reference-${figure.id}`}>{t('documents.figures.sourceReference')}</InputLabel>
                        <Select labelId={`figure-source-reference-${figure.id}`} value={sourceReferenceId} label={t('documents.figures.sourceReference')} onChange={event => setSourceReferenceId(event.target.value)}>
                            <MenuItem value=""><em>{t('documents.figures.noSourceReference')}</em></MenuItem>
                            {references.map(reference => <MenuItem key={reference.id} value={String(reference.id)}>{referenceLabel(reference, sourceById.get(reference.sourceId))}</MenuItem>)}
                        </Select>
                    </FormControl>
                    {referencesQuery.isError && <Alert severity="error">{apiErrorMessage(referencesQuery.error, t('documents.figures.referencesFailed'))}</Alert>}
                    {figure.reviewedBy && <Typography variant="body2" color="text.secondary">{t('documents.figures.reviewedBy', { reviewer: figure.reviewedBy, reason: figure.reviewReason ?? '—' })}</Typography>}
                    {!approvalReady && canReview && !dirty && figure.reviewState !== 'OUTDATED' && <Typography variant="body2" color="warning.main">{t('documents.figures.approvalRequirements')}</Typography>}
                </Stack>
            </Box>
            <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ gap: 1, justifyContent: 'flex-end' }}>
                {canEdit && <Button variant="outlined" startIcon={<EditOutlinedIcon />} disabled={!dirty || pending || figure.reviewState === 'OUTDATED'} onClick={() => void save()}>{t('forms.save')}</Button>}
                {canReview && <Button color="error" variant="outlined" startIcon={<CloseOutlinedIcon />} disabled={pending || figure.reviewState === 'OUTDATED' || dirty} onClick={() => setReviewDecision('reject')}>{t('documents.figures.reject')}</Button>}
                {canReview && <Button variant="contained" startIcon={<CheckOutlinedIcon />} disabled={pending || !approvalReady} onClick={() => setReviewDecision('approve')}>{t('documents.figures.approve')}</Button>}
            </Stack>
            <MediaOntologyLinksEditor mediaAssetId={figure.mediaAssetId} canEdit={canEdit && figure.reviewState !== 'OUTDATED'} />
        </Stack>
        <ConfirmDialog open={reviewDecision !== null} title={t(`documents.figures.${reviewDecision === 'approve' ? 'approveTitle' : 'rejectTitle'}`)} confirmLabel={t(`documents.figures.${reviewDecision === 'approve' ? 'approve' : 'reject'}`)} confirmColor={reviewDecision === 'approve' ? 'primary' : 'error'} pending={pending} confirmDisabled={!reviewReason.trim()} onCancel={() => { setReviewDecision(null); setReviewReason('') }} onConfirm={() => void review()}>
            <Stack spacing={2}>
                <Typography>{t(`documents.figures.${reviewDecision === 'approve' ? 'approveDescription' : 'rejectDescription'}`)}</Typography>
                <TextField autoFocus required multiline minRows={3} label={t('documents.figures.reviewReason')} value={reviewReason} onChange={event => setReviewReason(event.target.value.slice(0, 500))} />
            </Stack>
        </ConfirmDialog>
    </>
}

export function ProtectedFigureImage({ pageId, figureId, alt }: { pageId: number; figureId: number; alt: string }) {
    const [url, setUrl] = useState<string | null>(null)
    const query = useQuery({ queryKey: ['admin', 'document-page-figure-content', pageId, figureId], queryFn: ({ signal }) => getPageFigureContent(pageId, figureId, signal), staleTime: 60_000 })
    useEffect(() => {
        if (!query.data) return
        const next = URL.createObjectURL(query.data)
        setUrl(next)
        return () => URL.revokeObjectURL(next)
    }, [query.data])
    if (query.isPending || !url) return <Skeleton variant="rectangular" width="100%" height="100%" />
    if (query.isError) return <Box sx={{ width: '100%', height: '100%', display: 'grid', placeItems: 'center' }}><ImageNotSupportedOutlinedIcon color="disabled" sx={{ fontSize: 48 }} /></Box>
    return <Box component="img" src={url} alt={alt} sx={{ width: '100%', height: '100%', objectFit: 'contain', display: 'block' }} />
}

export function FigureStateChip({ state }: { state: FigureReviewState }) {
    const { t } = useTranslation()
    const color = state === 'APPROVED' ? 'success' : state === 'REJECTED' ? 'error' : state === 'OUTDATED' ? 'warning' : 'default'
    return <Chip size="small" color={color} variant={state === 'PENDING' ? 'outlined' : 'filled'} label={t(`documents.figures.states.${state}`)} />
}

export function formatConfidence(value: number | null) {
    if (value == null) return '—'
    const percentage = value <= 1 ? value * 100 : value
    return `${Math.round(percentage)}%`
}

function referenceLabel(reference: SourceReferenceDetails, source?: SourceDetails) {
    return [source?.title, reference.chapter, reference.pageFrom ? `с. ${reference.pageFrom}` : null, reference.locator].filter(Boolean).join(' · ') || `#${reference.id}`
}

function figureError(error: unknown, fallback: string, conflict: string) {
    return hasStatus(error, 409) ? conflict : apiErrorMessage(error, fallback)
}

function hasStatus(error: unknown, status: number) {
    return typeof error === 'object' && error !== null && 'status' in error && error.status === status
}

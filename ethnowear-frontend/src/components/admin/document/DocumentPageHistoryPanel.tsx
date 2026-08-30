import { Alert, Box, CircularProgress, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { documentQueryKeys, listPageOcrHistory, listPageProvenanceHistory, listPageReviewHistory } from '../../../api/DocumentAdminApi'
import { apiErrorMessage } from '../../../api/http'
import { apiEnumLabel } from '../../../app/apiEnumLabels'

export type PageHistoryKind = 'ocr' | 'reviews' | 'provenance'

type Props = {
    documentId: number
    pageId: number
    kind: PageHistoryKind
}

const pageSize = 10

export default function DocumentPageHistoryPanel({ documentId, pageId, kind }: Props) {
    const { t, i18n } = useTranslation()
    const [page, setPage] = useState(0)
    const request = { page, size: pageSize, sort: 'createdAt,desc' }
    const ocrQuery = useQuery({ queryKey: [...documentQueryKeys.pageOcrHistory(documentId, pageId), request], queryFn: ({ signal }) => listPageOcrHistory(documentId, pageId, request, signal), enabled: kind === 'ocr' })
    const reviewQuery = useQuery({ queryKey: [...documentQueryKeys.pageReviewHistory(documentId, pageId), request], queryFn: ({ signal }) => listPageReviewHistory(documentId, pageId, request, signal), enabled: kind === 'reviews' })
    const provenanceQuery = useQuery({ queryKey: [...documentQueryKeys.pageProvenanceHistory(documentId, pageId), request], queryFn: ({ signal }) => listPageProvenanceHistory(documentId, pageId, request, signal), enabled: kind === 'provenance' })
    const query = kind === 'ocr' ? ocrQuery : kind === 'reviews' ? reviewQuery : provenanceQuery
    const formatDate = (value: string) => new Intl.DateTimeFormat(i18n.resolvedLanguage ?? 'bg', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))

    if (query.isPending) return <Box sx={{ minHeight: 240, display: 'grid', placeItems: 'center' }}><CircularProgress size={28} /></Box>
    if (query.isError) return <Alert severity="error" action={<Typography component="button" onClick={() => void query.refetch()}>{t('common.retry')}</Typography>}>{apiErrorMessage(query.error, t('documents.pageReview.history.loadFailed'))}</Alert>
    if (query.data.content.length === 0) return <Typography color="text.secondary" sx={{ py: 5, textAlign: 'center' }}>{t(`documents.pageReview.history.${kind}.empty`)}</Typography>

    return <Stack sx={{ minWidth: 0 }}>
        <TableContainer component={Paper} variant="outlined"><Table size="small">
            <TableHead><TableRow>{kind === 'ocr' ? <>
                <TableCell>{t('documents.pageReview.history.current')}</TableCell><TableCell>{t('documents.pageReview.history.engine')}</TableCell><TableCell>{t('documents.pageReview.history.confidence')}</TableCell><TableCell>{t('documents.pageReview.history.created')}</TableCell>
            </> : kind === 'reviews' ? <>
                <TableCell>{t('documents.pageReview.history.action')}</TableCell><TableCell>{t('documents.pageReview.history.reviewer')}</TableCell><TableCell>{t('documents.pageReview.history.stateChange')}</TableCell><TableCell>{t('documents.pageReview.history.reason')}</TableCell><TableCell>{t('documents.pageReview.history.created')}</TableCell>
            </> : <>
                <TableCell>{t('documents.pageReview.history.event')}</TableCell><TableCell>{t('documents.pageReview.history.reviewer')}</TableCell><TableCell>{t('documents.pageReview.history.provenanceChange')}</TableCell><TableCell>{t('documents.pageReview.history.reason')}</TableCell><TableCell>{t('documents.pageReview.history.created')}</TableCell>
            </>}</TableRow></TableHead>
            <TableBody>{kind === 'ocr' ? ocrQuery.data?.content.map(item => <TableRow key={item.id}>
                <TableCell>{item.current ? t('common.yes') : t('common.no')}</TableCell><TableCell>{[item.ocrEngine, item.ocrEngineVersion].filter(Boolean).join(' ') || '—'}</TableCell><TableCell>{item.ocrConfidence === null ? '—' : `${Math.round(item.ocrConfidence * 100)}%`}</TableCell><TableCell>{formatDate(item.createdAt)}</TableCell>
            </TableRow>) : kind === 'reviews' ? reviewQuery.data?.content.map(item => <TableRow key={item.id}>
                <TableCell><Typography sx={{ fontWeight: 700 }}>{apiEnumLabel(t, 'reviewAction', item.reviewAction)}</Typography>{item.correctedTextSnapshot && <Typography variant="body2" color="text.secondary" sx={{ mt: .5, maxWidth: 360, whiteSpace: 'pre-wrap' }}>{item.correctedTextSnapshot}</Typography>}</TableCell>
                <TableCell>{item.reviewer}</TableCell><TableCell><StateChange previous={item.previousReviewState} next={item.newReviewState} /><StateChange previous={item.previousApprovalState} next={item.newApprovalState} /></TableCell><TableCell>{item.reason ?? '—'}</TableCell><TableCell>{formatDate(item.createdAt)}</TableCell>
            </TableRow>) : provenanceQuery.data?.content.map(item => <TableRow key={item.id}>
                <TableCell>{apiEnumLabel(t, 'provenanceEvent', item.eventType)}</TableCell><TableCell>{item.reviewedBy}</TableCell><TableCell><StateChange previous={item.previousProvenanceStatus} next={item.newProvenanceStatus} /><StateChange previous={item.previousTrustState} next={item.newTrustState} /></TableCell><TableCell>{item.reason || '—'}</TableCell><TableCell>{formatDate(item.createdAt)}</TableCell>
            </TableRow>)}</TableBody>
        </Table></TableContainer>
        <TablePagination component="div" count={query.data.totalElements} page={page} rowsPerPage={pageSize} rowsPerPageOptions={[pageSize]} onPageChange={(_, value) => setPage(value)} />
    </Stack>
}

function StateChange({ previous, next }: { previous: string | null; next: string | null }) {
    if (!previous && !next) return null
    return <Typography variant="body2">{previous ?? '—'} → {next ?? '—'}</Typography>
}

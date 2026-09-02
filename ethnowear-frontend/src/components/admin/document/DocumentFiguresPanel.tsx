import { useEffect, useState } from 'react'
import {
    Alert, Box, Button, Paper, Skeleton, Stack, Typography,
} from '@mui/material'
import AutorenewOutlinedIcon from '@mui/icons-material/AutorenewOutlined'
import ImageNotSupportedOutlinedIcon from '@mui/icons-material/ImageNotSupportedOutlined'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import {
    documentQueryKeys, listDocumentFigures, reextractPageFigures,
} from '../../../api/DocumentAdminApi'
import { apiErrorMessage } from '../../../api/http'
import { useAdminAuth } from '../../../app/adminAuth'
import { hasAnyRole, processingMutationRoles, reviewRoles } from '../../../app/permissions'
import type { DocumentPageFigure, DocumentFigurePageGroup, DocumentPageSummary } from '../../../types/document'
import AdminModal from '../AdminModal'
import ConfirmDialog from '../ConfirmDialog'
import DocumentFigureReviewEditor, { FigureStateChip, ProtectedFigureImage, formatConfidence } from './DocumentFigureReviewEditor'

export default function DocumentFiguresPanel({ documentId, defaultSourceReferenceId = null }: { documentId: number; defaultSourceReferenceId?: number | null }) {
    const { t } = useTranslation()
    const [params, setParams] = useSearchParams()
    const queryClient = useQueryClient()
    const { admin } = useAdminAuth()
    const canEdit = Boolean(admin && hasAnyRole(admin.roles, processingMutationRoles))
    const canReview = Boolean(admin && hasAnyRole(admin.roles, reviewRoles))
    const [selected, setSelected] = useState<{ page: DocumentPageSummary; figure: DocumentPageFigure } | null>(null)
    const [reextractPage, setReextractPage] = useState<DocumentPageSummary | null>(null)
    const [reextracting, setReextracting] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const figuresQuery = useQuery({
        queryKey: documentQueryKeys.figures(documentId),
        queryFn: ({ signal }) => listDocumentFigures(documentId, signal),
    })

    useEffect(() => {
        const pageId = Number(params.get('pageId'))
        const figureId = Number(params.get('figureId'))
        if (!figuresQuery.data || !pageId || !figureId) return
        const group = figuresQuery.data.find(item => item.page.id === pageId)
        const figure = group?.figures.find(item => item.id === figureId)
        if (group && figure) setSelected({ page: group.page, figure })
    }, [figuresQuery.data, params])

    function openFigure(page: DocumentPageSummary, figure: DocumentPageFigure) {
        setSelected({ page, figure })
        setParams(current => {
            const next = new URLSearchParams(current)
            next.set('tab', 'figures')
            next.set('pageId', String(page.id))
            next.set('figureId', String(figure.id))
            return next
        })
    }

    function closeFigure() {
        setSelected(null)
        setParams(current => {
            const next = new URLSearchParams(current)
            next.delete('pageId')
            next.delete('figureId')
            return next
        }, { replace: true })
    }

    async function reextract() {
        if (!reextractPage || reextracting) return
        setReextracting(true)
        setError(null)
        try {
            await reextractPageFigures(reextractPage.id)
            setReextractPage(null)
            await queryClient.invalidateQueries({ queryKey: documentQueryKeys.figuresRoot(documentId) })
        } catch (caught) {
            setError(apiErrorMessage(caught, t('documents.figures.reextractFailed')))
        } finally {
            setReextracting(false)
        }
    }

    if (figuresQuery.isPending) return <FigurePanelSkeleton />
    if (figuresQuery.isError) return <Alert severity="error" action={<Button onClick={() => void figuresQuery.refetch()}>{t('common.retry')}</Button>}>{apiErrorMessage(figuresQuery.error, t('documents.figures.loadFailed'))}</Alert>

    const groups = figuresQuery.data
    return <Stack spacing={2.5}>
        <Alert severity="info">{t('documents.figures.publicationNotice')}</Alert>
        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
        {groups.length === 0
            ? <Paper variant="outlined" sx={{ py: 8, px: 3, textAlign: 'center' }}><ImageNotSupportedOutlinedIcon color="disabled" sx={{ fontSize: 48 }} /><Typography variant="h6" sx={{ mt: 1 }}>{t('documents.figures.emptyTitle')}</Typography><Typography color="text.secondary">{t('documents.figures.emptyDescription')}</Typography></Paper>
            : groups.map(group => <FigurePageGroup key={group.page.id} group={group} canReextract={canEdit} onOpen={openFigure} onReextract={() => setReextractPage(group.page)} />)}
        <FigureReviewDialog
            key={selected?.figure.id ?? 'closed'}
            open={selected !== null}
            page={selected?.page ?? null}
            figure={selected?.figure ?? null}
            defaultSourceReferenceId={defaultSourceReferenceId}
            canEdit={canEdit}
            canReview={canReview}
            onClose={closeFigure}
            onConflict={() => void queryClient.invalidateQueries({ queryKey: documentQueryKeys.figuresRoot(documentId) })}
            onChanged={updated => {
                setSelected(current => current ? { ...current, figure: updated } : null)
                void queryClient.invalidateQueries({ queryKey: documentQueryKeys.figuresRoot(documentId) })
                void queryClient.invalidateQueries({ queryKey: ['admin', 'media'] })
            }}
        />
        <ConfirmDialog open={reextractPage !== null} title={t('documents.figures.reextractTitle')} confirmLabel={t('documents.figures.reextractConfirm')} confirmColor="primary" pending={reextracting} onCancel={() => setReextractPage(null)} onConfirm={() => void reextract()}>
            <Typography>{t('documents.figures.reextractDescription', { page: pageLabel(reextractPage) })}</Typography>
        </ConfirmDialog>
    </Stack>
}

function FigurePageGroup({ group, canReextract, onOpen, onReextract }: { group: DocumentFigurePageGroup; canReextract: boolean; onOpen: (page: DocumentPageSummary, figure: DocumentPageFigure) => void; onReextract: () => void }) {
    const { t } = useTranslation()
    return <Box component="section" aria-labelledby={`figure-page-${group.page.id}`}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mb: 1.25, alignItems: { sm: 'center' }, justifyContent: 'space-between' }}>
            <Box><Typography id={`figure-page-${group.page.id}`} variant="h6" sx={{ fontWeight: 800 }}>{t('documents.figures.pageHeading', { page: pageLabel(group.page) })}</Typography><Typography variant="body2" color="text.secondary">{t('documents.figures.figureCount', { count: group.figures.length })}</Typography></Box>
            {canReextract && <Button size="small" variant="outlined" startIcon={<AutorenewOutlinedIcon />} onClick={onReextract}>{t('documents.figures.reextract')}</Button>}
        </Stack>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, minmax(0, 1fr))', xl: 'repeat(3, minmax(0, 1fr))' }, gap: 2 }}>
            {group.figures.map(figure => <FigureCard key={figure.id} page={group.page} figure={figure} onOpen={() => onOpen(group.page, figure)} />)}
        </Box>
    </Box>
}

function FigureCard({ page, figure, onOpen }: { page: DocumentPageSummary; figure: DocumentPageFigure; onOpen: () => void }) {
    const { t } = useTranslation()
    const caption = figure.correctedCaptionText?.trim() || figure.rawCaptionText?.trim()
    return <Paper variant="outlined" sx={{ overflow: 'hidden', opacity: figure.reviewState === 'OUTDATED' ? .72 : 1, display: 'grid', gridTemplateRows: '190px 1fr' }}>
        <Box component="button" type="button" onClick={onOpen} aria-label={t('documents.figures.openFigure', { number: figure.figureOrdinal, page: pageLabel(page) })} sx={{ border: 0, p: 0, bgcolor: 'grey.100', cursor: 'pointer', overflow: 'hidden' }}><ProtectedFigureImage pageId={page.id} figureId={figure.id} alt={caption ?? t('documents.figures.unnamed')} /></Box>
        <Stack spacing={1} sx={{ p: 2, minWidth: 0 }}>
            <Stack direction="row" sx={{ gap: 1, justifyContent: 'space-between', alignItems: 'flex-start' }}><Typography sx={{ fontWeight: 800 }} noWrap>{figure.printedFigureNumber || t('documents.figures.ordinal', { number: figure.figureOrdinal })}</Typography><FigureStateChip state={figure.reviewState} /></Stack>
            <Typography variant="body2" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{caption || t('documents.figures.captionMissing')}</Typography>
            <Typography variant="caption" color="text.secondary">{t('documents.figures.confidence', { value: formatConfidence(figure.detectionConfidence) })}</Typography>
            {figure.reviewState === 'OUTDATED' && <Typography variant="caption" color="warning.main" sx={{ fontWeight: 700 }}>{t('documents.figures.outdatedHelp')}</Typography>}
            <Button size="small" startIcon={<VisibilityOutlinedIcon />} onClick={onOpen} sx={{ alignSelf: 'flex-start', mt: 'auto' }}>{t('documents.figures.review')}</Button>
        </Stack>
    </Paper>
}

function FigureReviewDialog({ open, page, figure, defaultSourceReferenceId, canEdit, canReview, onClose, onChanged, onConflict }: { open: boolean; page: DocumentPageSummary | null; figure: DocumentPageFigure | null; defaultSourceReferenceId: number | null; canEdit: boolean; canReview: boolean; onClose: () => void; onChanged: (figure: DocumentPageFigure) => void; onConflict: () => void }) {
    const { t } = useTranslation()
    if (!figure || !page) return null
    return <AdminModal open={open} onClose={onClose} maxWidth="lg" title={figure.printedFigureNumber || t('documents.figures.ordinal', { number: figure.figureOrdinal })} description={t('documents.figures.pageHeading', { page: pageLabel(page) })} actions={<Button onClick={onClose}>{t('admin.cancel')}</Button>}>
        <DocumentFigureReviewEditor figure={figure} suggestedSourceReferenceId={page.sourceReferenceId ?? defaultSourceReferenceId} canEdit={canEdit} canReview={canReview} onChanged={onChanged} onConflict={onConflict} />
    </AdminModal>
}

function FigurePanelSkeleton() {
    return <Stack spacing={2}><Skeleton variant="rounded" height={64} /><Skeleton width={220} height={34} /><Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)', xl: 'repeat(3, 1fr)' }, gap: 2 }}>{Array.from({ length: 6 }, (_, index) => <Skeleton key={index} variant="rounded" height={340} />)}</Box></Stack>
}

function pageLabel(page: DocumentPageSummary | null) {
    if (!page) return '—'
    return page.printedPageNumber || page.pageLabel || page.pageSequence
}

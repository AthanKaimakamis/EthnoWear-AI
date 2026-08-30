import { useEffect, useState } from 'react'
import { Alert, Box, Button, Skeleton, Stack, Tab, Tabs, Typography } from '@mui/material'
import AutorenewOutlinedIcon from '@mui/icons-material/AutorenewOutlined'
import ImageNotSupportedOutlinedIcon from '@mui/icons-material/ImageNotSupportedOutlined'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
    documentQueryKeys, listPageFigures, reextractPageFigures,
} from '../../../api/DocumentAdminApi'
import { apiErrorMessage } from '../../../api/http'
import { useAdminAuth } from '../../../app/adminAuth'
import { hasAnyRole, processingMutationRoles, reviewRoles } from '../../../app/permissions'
import type { DocumentPageFigure } from '../../../types/document'
import ConfirmDialog from '../ConfirmDialog'
import DocumentFigureReviewEditor, { FigureStateChip } from './DocumentFigureReviewEditor'

export default function DocumentPageFiguresWorkspace({ documentId, pageId }: { documentId: number; pageId: number }) {
    const { t } = useTranslation()
    const { admin } = useAdminAuth()
    const queryClient = useQueryClient()
    const canEdit = Boolean(admin && hasAnyRole(admin.roles, processingMutationRoles))
    const canReview = Boolean(admin && hasAnyRole(admin.roles, reviewRoles))
    const [selectedId, setSelectedId] = useState<number | null>(null)
    const [confirmReextract, setConfirmReextract] = useState(false)
    const [reextracting, setReextracting] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const figuresQuery = useQuery({
        queryKey: documentQueryKeys.pageFigures(documentId, pageId),
        queryFn: ({ signal }) => listPageFigures(pageId, signal),
    })

    useEffect(() => {
        if (!figuresQuery.data?.length) {
            setSelectedId(null)
            return
        }
        if (!figuresQuery.data.some(figure => figure.id === selectedId)) setSelectedId(figuresQuery.data[0].id)
    }, [figuresQuery.data, selectedId])

    async function refreshFigures() {
        await Promise.all([
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.pageFigures(documentId, pageId) }),
            queryClient.invalidateQueries({ queryKey: documentQueryKeys.figuresRoot(documentId) }),
            queryClient.invalidateQueries({ queryKey: ['admin', 'media'] }),
        ])
    }

    async function reextract() {
        if (reextracting) return
        setReextracting(true)
        setError(null)
        try {
            await reextractPageFigures(pageId)
            setConfirmReextract(false)
            await refreshFigures()
        } catch (caught) {
            setError(apiErrorMessage(caught, t('documents.figures.reextractFailed')))
        } finally {
            setReextracting(false)
        }
    }

    if (figuresQuery.isPending) return <Stack spacing={1.5} sx={{ p: 1.5 }}><Skeleton height={42} /><Skeleton variant="rounded" height={420} /></Stack>
    if (figuresQuery.isError) return <Alert severity="error" action={<Button onClick={() => void figuresQuery.refetch()}>{t('common.retry')}</Button>}>{apiErrorMessage(figuresQuery.error, t('documents.figures.loadFailed'))}</Alert>

    const figures = figuresQuery.data
    const selected = figures.find(figure => figure.id === selectedId) ?? null
    return <Stack spacing={1.5} sx={{ p: 1.5, minHeight: 0, overflow: 'auto' }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ gap: 1, alignItems: { sm: 'center' }, justifyContent: 'space-between' }}>
            <Box>
                <Typography sx={{ fontWeight: 800 }}>{t('documents.figures.pageWorkspaceTitle')}</Typography>
                <Typography variant="body2" color="text.secondary">{t('documents.figures.publicationNotice')}</Typography>
            </Box>
            {canEdit && <Button size="small" variant="outlined" startIcon={<AutorenewOutlinedIcon />} onClick={() => setConfirmReextract(true)}>{t('documents.figures.reextract')}</Button>}
        </Stack>
        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
        {figures.length === 0
            ? <Box sx={{ minHeight: 360, display: 'grid', placeItems: 'center', textAlign: 'center' }}>
                <Box><ImageNotSupportedOutlinedIcon color="disabled" sx={{ fontSize: 48 }} /><Typography variant="h6">{t('documents.figures.emptyTitle')}</Typography><Typography color="text.secondary">{t('documents.figures.emptyDescription')}</Typography></Box>
            </Box>
            : <>
                <Tabs value={selectedId} onChange={(_, value) => setSelectedId(value)} variant="scrollable" scrollButtons="auto" aria-label={t('documents.figures.selectFigure')} sx={{ minHeight: 42, borderBottom: 1, borderColor: 'divider', '& .MuiTab-root': { minHeight: 42, py: .5 } }}>
                    {figures.map(figure => <Tab key={figure.id} value={figure.id} label={<Stack direction="row" sx={{ gap: .75, alignItems: 'center' }}><span>{figure.printedFigureNumber || t('documents.figures.ordinal', { number: figure.figureOrdinal })}</span><FigureStateChip state={figure.reviewState} /></Stack>} />)}
                </Tabs>
                {selected && <DocumentFigureReviewEditor
                    key={`${selected.id}-${selected.version}`}
                    figure={selected}
                    canEdit={canEdit}
                    canReview={canReview}
                    onConflict={() => void refreshFigures()}
                    onChanged={updated => {
                        queryClient.setQueryData<DocumentPageFigure[]>(documentQueryKeys.pageFigures(documentId, pageId), current => current?.map(figure => figure.id === updated.id ? updated : figure))
                        void refreshFigures()
                    }}
                />}
            </>}
        <ConfirmDialog open={confirmReextract} title={t('documents.figures.reextractTitle')} confirmLabel={t('documents.figures.reextractConfirm')} confirmColor="primary" pending={reextracting} onCancel={() => setConfirmReextract(false)} onConfirm={() => void reextract()}>
            <Typography>{t('documents.figures.reextractCurrentPageDescription')}</Typography>
        </ConfirmDialog>
    </Stack>
}

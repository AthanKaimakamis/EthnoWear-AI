import { useEffect, useState } from 'react'
import { Alert, Button, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { sourceReferencesApi } from '../../../api/ArchiveAdminApi'
import { documentQueryKeys, getDocument, updateDocumentMetadata } from '../../../api/DocumentAdminApi'
import { apiErrorMessage } from '../../../api/http'
import type { DocumentSummary } from '../../../types/document'
import { emptySourceReference, findGeneralSourceReference } from './documentSourceReference'

type Props = {
    summary: DocumentSummary
    canEdit: boolean
}

export default function DocumentDefaultSourceReferenceEditor({ summary, canEdit }: Props) {
    const { t } = useTranslation()
    const queryClient = useQueryClient()
    const [value, setValue] = useState(String(summary.defaultSourceReferenceId ?? ''))
    const [pending, setPending] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [notice, setNotice] = useState<string | null>(null)
    const referencesQuery = useQuery({
        queryKey: ['admin', 'source-references', 'document-default', summary.sourceId],
        queryFn: ({ signal }) => sourceReferencesApi.findAll({ size: 1000 }, signal),
        enabled: summary.sourceId !== null,
        staleTime: 300_000,
    })

    useEffect(() => setValue(String(summary.defaultSourceReferenceId ?? '')), [summary.defaultSourceReferenceId])

    const references = (referencesQuery.data?.content ?? []).filter(reference => reference.sourceId === summary.sourceId)
    const dirty = value !== String(summary.defaultSourceReferenceId ?? '')

    async function ensureAndSave() {
        if (!canEdit || summary.sourceId === null || pending) return
        setPending(true)
        setError(null)
        setNotice(null)
        try {
            let referenceId = value ? Number(value) : null
            if (referenceId === null) {
                referenceId = findGeneralSourceReference(references, summary.sourceId)?.id
                    ?? (await sourceReferencesApi.create(emptySourceReference(summary.sourceId))).id
            }
            const currentDocument = await getDocument(summary.id)
            await updateDocumentMetadata(summary.id, {
                defaultSourceReferenceId: referenceId,
                title: summary.title,
                author: summary.author,
                publisher: summary.publisher,
                publicationYear: summary.publicationYear,
                language: summary.language,
                notes: currentDocument.notes,
            })
            setValue(String(referenceId))
            await Promise.all([
                queryClient.invalidateQueries({ queryKey: documentQueryKeys.detail(summary.id) }),
                queryClient.invalidateQueries({ queryKey: ['admin', 'source-references'] }),
            ])
            setNotice(t('documents.sourceReference.saved'))
        } catch (caught) {
            setError(apiErrorMessage(caught, t('documents.sourceReference.failed')))
        } finally {
            setPending(false)
        }
    }

    if (summary.sourceId === null) return <Alert severity="warning">{t('documents.sourceReference.needsSource')}</Alert>

    return <Stack spacing={1.5} sx={{ mt: 2 }}>
        <Typography variant="h6" sx={{ fontWeight: 700 }}>{t('documents.sourceReference.title')}</Typography>
        <Typography variant="body2" color="text.secondary">{t('documents.sourceReference.help')}</Typography>
        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
        {notice && <Alert severity="success" onClose={() => setNotice(null)}>{notice}</Alert>}
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'flex-start' } }}>
            <TextField select fullWidth size="small" label={t('documents.sourceReference.title')} value={value} onChange={event => setValue(event.target.value)} disabled={!canEdit || pending || referencesQuery.isPending} helperText={references.length === 0 ? t('documents.sourceReference.createHelp') : t('documents.sourceReference.selectHelp')}>
                <MenuItem value=""><em>{t('documents.sourceReference.createGeneral')}</em></MenuItem>
                {references.map(reference => <MenuItem key={reference.id} value={String(reference.id)}>{referenceLabel(reference)}</MenuItem>)}
            </TextField>
            {canEdit && <Button variant="outlined" disabled={pending || (!dirty && summary.defaultSourceReferenceId !== null)} onClick={() => void ensureAndSave()} sx={{ whiteSpace: 'nowrap' }}>{pending ? t('forms.saving') : t('forms.save')}</Button>}
        </Stack>
    </Stack>
}

function referenceLabel(reference: { id: number; chapter: string | null; pageFrom: number | null; pageTo: number | null; figureNumber: string | null; locator: string | null }) {
    const details = [reference.chapter, reference.pageFrom ? `с. ${reference.pageFrom}${reference.pageTo ? `-${reference.pageTo}` : ''}` : null, reference.figureNumber, reference.locator].filter(Boolean)
    return details.length ? details.join(' · ') : `#${reference.id}`
}

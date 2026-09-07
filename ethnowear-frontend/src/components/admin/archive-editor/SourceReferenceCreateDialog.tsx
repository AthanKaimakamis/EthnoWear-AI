import { useState, type SubmitEvent } from 'react'
import {
    Alert, Box, Button, FormControl, InputLabel, MenuItem, Select, Stack, TextField,
} from '@mui/material'
import { useTranslation } from 'react-i18next'
import { sourceReferencesApi } from '../../../api/ArchiveAdminApi'
import { apiErrorMessage } from '../../../api/http'
import type { SourceDetails, SourceReferenceDetails, SourceReferenceWriteDto } from '../../../types/archive'
import AdminModal from '../AdminModal'

type Props = {
    sources: SourceDetails[]
    initialSourceId?: number | null
    onClose: () => void
    onCreated: (reference: SourceReferenceDetails) => void
}

export default function SourceReferenceCreateDialog({ sources, initialSourceId, onClose, onCreated }: Props) {
    const { t } = useTranslation()
    const [form, setForm] = useState<SourceReferenceWriteDto>({
        sourceId: initialSourceId ?? sources[0]?.id ?? 0,
        chapter: null,
        pageFrom: null,
        pageTo: null,
        figureNumber: null,
        sectionTitle: null,
        catalogNumber: null,
        referenceUrl: null,
        accessedDate: null,
        locator: null,
        note: null,
    })
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const formId = 'quick-source-reference-create-form'

    function set<K extends keyof SourceReferenceWriteDto>(field: K, value: SourceReferenceWriteDto[K]) {
        setForm(current => ({ ...current, [field]: value }))
    }

    async function submit(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        setSaving(true)
        setError(null)
        try {
            onCreated(await sourceReferencesApi.create({
                ...form,
                chapter: form.chapter?.trim() || null,
                sectionTitle: form.sectionTitle?.trim() || null,
                figureNumber: form.figureNumber?.trim() || null,
                locator: form.locator?.trim() || null,
                note: form.note?.trim() || null,
            }))
        } catch (caught) {
            setError(apiErrorMessage(caught, t('curator.source.createCitationFailed')))
        } finally {
            setSaving(false)
        }
    }

    return (
        <AdminModal
            open
            title={t('curator.source.createCitationTitle')}
            description={t('curator.source.createCitationDescription')}
            onClose={onClose}
            closeDisabled={saving}
            maxWidth="md"
            actions={<>
                <Button onClick={onClose} disabled={saving}>{t('admin.cancel')}</Button>
                <Button type="submit" form={formId} variant="contained" disabled={saving || !form.sourceId}>
                    {saving ? t('forms.saving') : t('curator.source.createCitation')}
                </Button>
            </>}
        >
            <Box component="form" id={formId} onSubmit={submit}>
                <Stack spacing={2}>
                    {error && <Alert severity="error">{error}</Alert>}
                    <FormControl required fullWidth>
                        <InputLabel>{t('curator.fields.source')}</InputLabel>
                        <Select label={t('curator.fields.source')} value={form.sourceId || ''} onChange={event => set('sourceId', Number(event.target.value))}>
                            {sources.map(source => <MenuItem key={source.id} value={source.id}>{source.title}</MenuItem>)}
                        </Select>
                    </FormControl>
                    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
                        <TextField label={t('curator.source.chapter')} value={form.chapter ?? ''} onChange={event => set('chapter', nullable(event.target.value))} />
                        <TextField label={t('curator.source.section')} value={form.sectionTitle ?? ''} onChange={event => set('sectionTitle', nullable(event.target.value))} />
                        <TextField type="number" label={t('curator.source.pageFrom')} value={form.pageFrom ?? ''} onChange={event => set('pageFrom', numberOrNull(event.target.value))} />
                        <TextField type="number" label={t('curator.source.pageTo')} value={form.pageTo ?? ''} onChange={event => set('pageTo', numberOrNull(event.target.value))} />
                        <TextField label={t('curator.source.figure')} value={form.figureNumber ?? ''} onChange={event => set('figureNumber', nullable(event.target.value))} />
                        <TextField label={t('curator.source.locator')} value={form.locator ?? ''} onChange={event => set('locator', nullable(event.target.value))} />
                    </Box>
                    <TextField multiline minRows={3} label={t('curator.source.note')} value={form.note ?? ''} onChange={event => set('note', nullable(event.target.value))} />
                </Stack>
            </Box>
        </AdminModal>
    )
}

function nullable(value: string) {
    return value || null
}

function numberOrNull(value: string) {
    return value ? Number(value) : null
}

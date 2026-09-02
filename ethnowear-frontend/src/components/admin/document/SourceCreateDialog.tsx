import { useState, type SubmitEvent } from 'react'
import {
    Alert, Box, Button, Checkbox, FormControlLabel, InputLabel, MenuItem, Select, Stack, TextField,
    FormControl,
} from '@mui/material'
import { useTranslation } from 'react-i18next'
import { sourcesApi } from '../../../api/ArchiveAdminApi'
import { apiErrorMessage } from '../../../api/http'
import type { SourceDetails, SourceType, SourceWriteDto } from '../../../types/archive'
import AdminModal from '../AdminModal'

const sourceTypes: SourceType[] = ['BOOK', 'SCANNED_BOOK', 'WEBSITE', 'MUSEUM_CATALOG', 'ARTICLE', 'FIELD_NOTE']

type InitialValues = Pick<SourceWriteDto, 'title' | 'author' | 'publisher' | 'year' | 'language'>

type Props = {
    initialValues: InitialValues
    onClose: () => void
    onCreated: (source: SourceDetails) => void
}

export default function SourceCreateDialog({ initialValues, onClose, onCreated }: Props) {
    const { t } = useTranslation()
    const [form, setForm] = useState<SourceWriteDto>({
        ...initialValues,
        sourceType: 'SCANNED_BOOK',
        filePath: null,
        url: null,
        isbn: null,
        notes: null,
        trusted: false,
    })
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const formId = 'quick-source-create-form'

    function set<K extends keyof SourceWriteDto>(field: K, value: SourceWriteDto[K]) {
        setForm(current => ({ ...current, [field]: value }))
    }

    async function submit(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        setSaving(true)
        setError(null)
        try {
            onCreated(await sourcesApi.create({
                ...form,
                title: form.title.trim(),
                author: normalized(form.author),
                publisher: normalized(form.publisher),
                language: normalized(form.language),
                isbn: normalized(form.isbn),
                url: normalized(form.url),
                notes: normalized(form.notes),
            }))
        } catch (caught) {
            setError(apiErrorMessage(caught, t('documents.uploadDialog.sourceCreateFailed')))
        } finally {
            setSaving(false)
        }
    }

    return (
        <AdminModal
            open
            title={t('documents.uploadDialog.createSourceTitle')}
            description={t('documents.uploadDialog.createSourceDescription')}
            onClose={onClose}
            closeDisabled={saving}
            maxWidth="md"
            actions={<>
                <Button onClick={onClose} disabled={saving}>{t('admin.cancel')}</Button>
                <Button type="submit" form={formId} variant="contained" disabled={saving || !form.title.trim()}>
                    {saving ? t('forms.saving') : t('documents.uploadDialog.createSource')}
                </Button>
            </>}
        >
            <Box component="form" id={formId} onSubmit={submit}>
                <Stack spacing={2}>
                    {error && <Alert severity="error">{error}</Alert>}
                    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
                        <TextField required label={t('admin.archive.fields.title')} value={form.title} onChange={event => set('title', event.target.value)} />
                        <FormControl required>
                            <InputLabel>{t('admin.archive.fields.sourceType')}</InputLabel>
                            <Select label={t('admin.archive.fields.sourceType')} value={form.sourceType} onChange={event => set('sourceType', event.target.value as SourceType)}>
                                {sourceTypes.map(value => <MenuItem key={value} value={value}>{t(`documents.sourceType.${value}`)}</MenuItem>)}
                            </Select>
                        </FormControl>
                        <TextField label={t('admin.archive.fields.author')} value={form.author ?? ''} onChange={event => set('author', nullable(event.target.value))} />
                        <TextField label={t('admin.archive.fields.publisher')} value={form.publisher ?? ''} onChange={event => set('publisher', nullable(event.target.value))} />
                        <TextField type="number" label={t('admin.archive.fields.year')} value={form.year ?? ''} onChange={event => set('year', event.target.value ? Number(event.target.value) : null)} />
                        <TextField label={t('admin.archive.fields.language')} value={form.language ?? ''} onChange={event => set('language', nullable(event.target.value))} />
                        <TextField label={t('admin.archive.fields.isbn')} value={form.isbn ?? ''} onChange={event => set('isbn', nullable(event.target.value))} />
                        <TextField type="url" label={t('admin.archive.fields.url')} value={form.url ?? ''} onChange={event => set('url', nullable(event.target.value))} />
                    </Box>
                    <TextField multiline minRows={3} label={t('admin.archive.fields.notes')} value={form.notes ?? ''} onChange={event => set('notes', nullable(event.target.value))} />
                    <FormControlLabel control={<Checkbox checked={form.trusted} onChange={event => set('trusted', event.target.checked)} />} label={t('admin.archive.fields.trusted')} />
                </Stack>
            </Box>
        </AdminModal>
    )
}

function nullable(value: string) {
    return value === '' ? null : value
}

function normalized(value: string | null | undefined) {
    return value?.trim() || null
}

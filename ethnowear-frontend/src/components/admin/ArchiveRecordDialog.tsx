import {
    Alert, Box, Button,
    FormControl, FormControlLabel, FormHelperText, InputLabel, MenuItem,
    Select, Stack, Switch, TextField, Typography,
} from '@mui/material'
import { useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { ArchiveAdminRecord, ArchiveAdminWriteDto } from '../../types/archiveAdmin'
import AdminModal from './AdminModal'

export type ArchiveFieldOption = {
    value: string
    label: string
    iri?: string
}

export type ArchiveFormValue = string | boolean
export type ArchiveFormValues = Record<string, ArchiveFormValue>

export type ArchiveField = {
    name: string
    label: string
    section: string
    kind?: 'text' | 'number' | 'url' | 'date' | 'textarea' | 'select' | 'boolean' | 'hidden'
    required?: boolean
    nullable?: boolean
    options?: ArchiveFieldOption[] | ((values: ArchiveFormValues) => ArchiveFieldOption[])
    pairedIriField?: string
    clearFields?: string[]
    min?: number
    max?: number
    step?: number
    rows?: number
    helperText?: string
    defaultValue?: ArchiveFormValue
    requiredWhen?: (values: ArchiveFormValues) => boolean
    disabledWhen?: (values: ArchiveFormValues) => boolean
    visibleWhen?: (values: ArchiveFormValues) => boolean
}

type Props = {
    open: boolean
    title: string
    fields: ArchiveField[]
    record: ArchiveAdminRecord | null
    saving: boolean
    error: string | null
    onClose: () => void
    onSubmit: (input: ArchiveAdminWriteDto) => void
    warnings?: (values: ArchiveFormValues) => string[]
    intro?: ReactNode
}

function initialValues(fields: ArchiveField[], record: ArchiveAdminRecord | null): ArchiveFormValues {
    const source = record ? record as unknown as Record<string, unknown> : {}
    return Object.fromEntries(fields.map(field => {
        const value = source[field.name] ?? field.defaultValue
        if (field.kind === 'boolean') return [field.name, Boolean(value)]
        return [field.name, value === null || value === undefined ? '' : String(value)]
    }))
}

function toPayload(fields: ArchiveField[], values: ArchiveFormValues): ArchiveAdminWriteDto {
    const payload: Record<string, unknown> = {}
    fields.forEach(field => {
        const value = values[field.name]
        if (field.kind === 'boolean') {
            payload[field.name] = Boolean(value)
        } else if (field.kind === 'number') {
            payload[field.name] = value === '' && field.nullable ? null : Number(value)
        } else {
            payload[field.name] = value === '' && field.nullable ? null : value
        }
    })
    return payload as ArchiveAdminWriteDto
}

function ArchiveRecordDialog({ open, title, fields, record, saving, error, onClose, onSubmit, warnings, intro }: Props) {
    const { t } = useTranslation()
    const [values, setValues] = useState<ArchiveFormValues>(() => initialValues(fields, record))
    const formId = 'archive-record-form'

    const sections = useMemo(() => Array.from(new Set(fields.map(field => field.section))), [fields])

    function change(field: ArchiveField, value: ArchiveFormValue) {
        setValues(current => {
            const next = { ...current, [field.name]: value }
            field.clearFields?.forEach(name => { next[name] = '' })
            if (field.name === 'rightsStatus' && (value === 'UNKNOWN' || value === 'RESTRICTED')) next.publicDisplayAllowed = false
            if (field.pairedIriField) {
                const options = typeof field.options === 'function' ? field.options(next) : field.options ?? []
                next[field.pairedIriField] = options.find(option => option.value === value)?.iri ?? ''
            }
            return next
        })
    }

    const invalid = fields.some(field => {
        const required = field.required || field.requiredWhen?.(values)
        const value = values[field.name]
        return Boolean(required && (value === '' || (typeof value === 'string' && !value.trim())))
    })
    const activeWarnings = warnings?.(values) ?? []

    function submit(event: React.SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        if (!invalid) onSubmit(toPayload(fields, values))
    }

    return (
        <AdminModal
            open={open}
            title={title}
            onClose={onClose}
            closeDisabled={saving}
            maxWidth="md"
            actions={
                <>
                    <Button onClick={onClose} disabled={saving}>{t('admin.cancel')}</Button>
                    <Button type="submit" form={formId} variant="contained" disabled={saving || invalid}>
                        {saving ? t('forms.saving') : t('forms.save')}
                    </Button>
                </>
            }
        >
            <Box component="form" id={formId} onSubmit={submit}>
                    <Stack spacing={3}>
                        {error && <Alert severity="error">{error}</Alert>}
                        {activeWarnings.map(message => <Alert key={message} severity="warning">{message}</Alert>)}
                        {intro}
                        {sections.map(section => (
                            <Stack key={section} spacing={2}>
                                <Typography variant="subtitle2" sx={{ fontWeight: 800, color: 'text.secondary' }}>{section}</Typography>
                                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 2 }}>
                                    {fields.filter(field => field.section === section).map(field => {
                                        const value = values[field.name] ?? ''
                                        if (field.visibleWhen && !field.visibleWhen(values)) return null
                                        const required = field.required || field.requiredWhen?.(values)
                                        const disabled = saving || field.disabledWhen?.(values)
                                        const options = typeof field.options === 'function' ? field.options(values) : field.options ?? []
                                        if (field.kind === 'hidden') return null
                                        if (field.kind === 'boolean') {
                                            return <FormControl key={field.name} sx={{ justifyContent: 'center' }}>
                                                <FormControlLabel control={<Switch checked={Boolean(value)} disabled={disabled} onChange={event => change(field, event.target.checked)} />} label={field.label} />
                                                {field.helperText && <FormHelperText>{field.helperText}</FormHelperText>}
                                            </FormControl>
                                        }
                                        if (field.kind === 'select') {
                                            return <FormControl key={field.name} required={required} disabled={disabled} fullWidth>
                                                <InputLabel id={`${field.name}-label`}>{field.label}</InputLabel>
                                                <Select labelId={`${field.name}-label`} label={field.label} value={String(value)} onChange={event => change(field, event.target.value)}>
                                                    {field.nullable && <MenuItem value=""><em>{t('admin.archive.none')}</em></MenuItem>}
                                                    {options.map(option => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
                                                </Select>
                                                {field.helperText && <FormHelperText>{field.helperText}</FormHelperText>}
                                            </FormControl>
                                        }
                                        return <TextField key={field.name} name={field.name} label={field.label}
                                            required={required} disabled={disabled} value={String(value)}
                                            type={field.kind === 'number' ? 'number' : field.kind === 'date' ? 'date' : field.kind === 'url' ? 'url' : 'text'}
                                            multiline={field.kind === 'textarea'} minRows={field.rows ?? (field.kind === 'textarea' ? 3 : undefined)}
                                            helperText={field.helperText} onChange={event => change(field, event.target.value)}
                                            slotProps={{
                                                inputLabel: field.kind === 'date' ? { shrink: true } : undefined,
                                                htmlInput: field.kind === 'number' ? { min: field.min, max: field.max, step: field.step } : undefined,
                                            }}
                                            sx={field.kind === 'textarea' ? { gridColumn: '1 / -1' } : undefined} />
                                    })}
                                </Box>
                            </Stack>
                        ))}
                    </Stack>
            </Box>
        </AdminModal>
    )
}

export default ArchiveRecordDialog

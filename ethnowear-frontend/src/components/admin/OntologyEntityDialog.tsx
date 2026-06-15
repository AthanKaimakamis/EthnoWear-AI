import { useEffect, useState, type FormEvent } from 'react'
import { Alert, Box, Button, Stack } from '@mui/material'
import AppDialog from '../AppDialog.tsx'
import FormTextField from '../forms/FormTextField.tsx'
import FormSelectField from '../forms/FormSelectField.tsx'
import FormMultiSelectField from '../forms/formMultiSelectFields.tsx'
import FormSection from '../forms/FormSection.tsx'
import FormStringListField from '../forms/FormStringListField.tsx'
import type { SelectOption } from '../forms/formTypes.ts'
import type { OntologyEntity, OntologyEntityInput, OntologyEntityType } from '../../types/ontologyAdmin.ts'
import { useTranslation } from 'react-i18next'

export type EntityOptions = {
    regions: SelectOption[]
    regionGroups: SelectOption[]
    ornaments: SelectOption[]
    techniques: SelectOption[]
    motifs: SelectOption[]
}

type Props = {
    open: boolean
    type: OntologyEntityType
    entity: OntologyEntity | null
    options: EntityOptions
    saving: boolean
    error: string | null
    onClose: () => void
    onSubmit: (input: OntologyEntityInput) => void
}

const ornamentTypes: SelectOption[] = [
    'GeometricOrnament', 'PlantOrnament', 'AnimalOrnament', 'HumanOrnament', 'SymbolicOrnament',
].map(value => ({ value, label: value }))

const techniqueTypes: SelectOption[] = [
    'ChainTechnique', 'ContourTechnique', 'CrossTechnique', 'GaitanTechnique',
    'OpenworkTechnique', 'PoligatTechnique', 'ScallopTechnique', 'SplitTechnique',
].map(value => ({ value, label: value }))

function selected(values: string[] | undefined, options: SelectOption[]) {
    const selectedValues = new Set(values ?? [])
    return options.filter(option => selectedValues.has(option.value))
}

function OntologyEntityDialog(props: Props) {
    const { t } = useTranslation()
    const [form, setForm] = useState<OntologyEntityInput>({
        localName: '', labelBg: '', labelEn: '', altLabelsBg: [], altLabelsEn: [],
        commentBg: '', commentEn: '', typeLocalNames: [], characteristicRegionLocalNames: [],
        regionGroupLocalName: '', regionLocalName: '', ornamentLocalNames: [], techniqueLocalNames: [], motifLocalNames: [],
    })

    useEffect(() => {
        setForm(props.entity ? { ...props.entity } : {
            localName: '', labelBg: '', labelEn: '', altLabelsBg: [], altLabelsEn: [],
            commentBg: '', commentEn: '', typeLocalNames: [], characteristicRegionLocalNames: [],
            regionGroupLocalName: '', regionLocalName: '', ornamentLocalNames: [], techniqueLocalNames: [], motifLocalNames: [],
        })
    }, [props.entity, props.open])

    function set<K extends keyof OntologyEntityInput>(key: K, value: OntologyEntityInput[K]) {
        setForm(current => ({ ...current, [key]: value }))
    }

    function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        props.onSubmit(form)
    }

    const typeOptions = props.type === 'ornaments' ? ornamentTypes : techniqueTypes
    const showTypes = props.type === 'ornaments' || props.type === 'techniques'
    const showRegion = props.type === 'motifs' || props.type === 'regional-embroideries'
    const showRegionGroup = props.type === 'regions'
    const showOrnaments = props.type === 'regions' || props.type === 'motifs' || props.type === 'regional-embroideries'
    const showTechniques = props.type === 'regions' || props.type === 'motifs' || props.type === 'regional-embroideries'
    const showMotifs = props.type === 'regional-embroideries'

    return (
        <AppDialog open={props.open} onClose={props.onClose} maxWidth="md"
            title={t(props.entity ? 'admin.form.editTitle' : 'admin.form.addTitle')}>
            <Box component="form" onSubmit={submit}>
                <Stack spacing={3}>
                    {props.error && <Alert severity="error">{props.error}</Alert>}
                    <FormSection title={t('admin.form.identity')}>
                        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
                            <FormTextField name="localName" label="Local name" required disabled={Boolean(props.entity)}
                                value={form.localName} onChange={event => set('localName', event.target.value)} />
                            {showTypes && <FormMultiSelectField name="typeLocalNames" label={t('admin.form.types')}
                                options={typeOptions} value={selected(form.typeLocalNames, typeOptions)}
                                onChange={value => set('typeLocalNames', value.map(option => option.value))} />}
                            <FormTextField name="labelBg" label={t('admin.form.labelBg')} value={form.labelBg ?? ''}
                                onChange={event => set('labelBg', event.target.value)} />
                            <FormTextField name="labelEn" label={t('admin.form.labelEn')} value={form.labelEn ?? ''}
                                onChange={event => set('labelEn', event.target.value)} />
                            <FormStringListField name="altLabelsBg" label={t('admin.form.altLabelsBg')}
                                helperText={t('admin.form.altLabelsHelp')} value={form.altLabelsBg}
                                onChange={value => set('altLabelsBg', value)} />
                            <FormStringListField name="altLabelsEn" label={t('admin.form.altLabelsEn')}
                                helperText={t('admin.form.altLabelsHelp')} value={form.altLabelsEn}
                                onChange={value => set('altLabelsEn', value)} />
                        </Box>
                    </FormSection>
                    <FormSection title={t('admin.form.description')}>
                        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
                            <FormTextField name="commentBg" label={t('admin.form.commentBg')} multiline minRows={3}
                                value={form.commentBg ?? ''} onChange={event => set('commentBg', event.target.value)} />
                            <FormTextField name="commentEn" label={t('admin.form.commentEn')} multiline minRows={3}
                                value={form.commentEn ?? ''} onChange={event => set('commentEn', event.target.value)} />
                        </Box>
                    </FormSection>
                    {(showRegion || showRegionGroup || showOrnaments || showTechniques || showMotifs) && (
                        <FormSection title={t('admin.form.relationships')}>
                            <Stack spacing={2}>
                                {showRegionGroup && <FormSelectField name="regionGroupLocalName" label={t('admin.form.regionGroup')}
                                    options={[{ value: '', label: t('admin.form.none') }, ...props.options.regionGroups]}
                                    value={form.regionGroupLocalName ?? ''}
                                    onChange={event => set('regionGroupLocalName', event.target.value)} />}
                                {showRegion && <FormSelectField name="regionLocalName" label={t('admin.form.region')}
                                    options={[{ value: '', label: t('admin.form.none') }, ...props.options.regions]}
                                    value={form.regionLocalName ?? ''} onChange={event => set('regionLocalName', event.target.value)} />}
                                {showOrnaments && <FormMultiSelectField name="ornamentLocalNames" label={t('admin.entities.ornaments')}
                                    options={props.options.ornaments} value={selected(form.ornamentLocalNames, props.options.ornaments)}
                                    onChange={value => set('ornamentLocalNames', value.map(option => option.value))} />}
                                {showTechniques && <FormMultiSelectField name="techniqueLocalNames" label={t('admin.entities.techniques')}
                                    options={props.options.techniques} value={selected(form.techniqueLocalNames, props.options.techniques)}
                                    onChange={value => set('techniqueLocalNames', value.map(option => option.value))} />}
                                {showMotifs && <FormMultiSelectField name="motifLocalNames" label={t('admin.entities.motifs')}
                                    options={props.options.motifs} value={selected(form.motifLocalNames, props.options.motifs)}
                                    onChange={value => set('motifLocalNames', value.map(option => option.value))} />}
                            </Stack>
                        </FormSection>
                    )}
                    <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
                        <Button onClick={props.onClose} disabled={props.saving}>{t('admin.cancel')}</Button>
                        <Button type="submit" variant="contained" disabled={props.saving}>
                            {props.saving ? t('forms.saving') : t('forms.save')}
                        </Button>
                    </Stack>
                </Stack>
            </Box>
        </AppDialog>
    )
}

export default OntologyEntityDialog

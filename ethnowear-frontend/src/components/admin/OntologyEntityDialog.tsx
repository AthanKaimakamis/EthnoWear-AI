import { useState, type FormEvent } from 'react'
import { Alert, Box, Button, Stack } from '@mui/material'
import AdminModal from './AdminModal.tsx'
import FormTextField from '../forms/FormTextField.tsx'
import FormSection from '../forms/FormSection.tsx'
import FormStringListField from '../forms/FormStringListField.tsx'
import OntologyRelationshipField from './OntologyRelationshipField.tsx'
import type { OptionCategory, SelectOption } from '../forms/formTypes.ts'
import type { OntologyEntity, OntologyEntityInput, OntologyEntityType } from '../../types/ontologyAdmin.ts'
import { useTranslation } from 'react-i18next'

export type EntityOptions = {
    regions: SelectOption[]
    regionGroups: SelectOption[]
    ornaments: SelectOption[]
    techniques: SelectOption[]
    motifs: SelectOption[]
    ornamentCategories: OptionCategory[]
    techniqueCategories: OptionCategory[]
}

type Props = {
    open: boolean
    type: OntologyEntityType
    entity: OntologyEntity | null
    options: EntityOptions
    saving: boolean
    error: string | null
    onClose: () => void
    onSubmit: (input: OntologyEntityInput, changeReason?: string) => void
}

function initialForm(entity: OntologyEntity | null): OntologyEntityInput {
    return entity ? { ...entity } : {
        localName: '', labelBg: '', labelEn: '', altLabelsBg: [], altLabelsEn: [],
        commentBg: '', commentEn: '', typeLocalNames: [], characteristicRegionLocalNames: [],
        regionGroupLocalName: '', regionLocalName: '', ornamentLocalNames: [], techniqueLocalNames: [], motifLocalNames: [],
    }
}

function OntologyEntityDialog(props: Props) {
    const { t } = useTranslation()
    const [form, setForm] = useState<OntologyEntityInput>(() => initialForm(props.entity))
    const [changeReason, setChangeReason] = useState('')
    const formId = 'ontology-entity-form'

    function set<K extends keyof OntologyEntityInput>(key: K, value: OntologyEntityInput[K]) {
        setForm(current => ({ ...current, [key]: value }))
    }

    function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        props.onSubmit(form, changeReason.trim() || undefined)
    }

    const typeOptions = props.type === 'ornaments'
        ? props.options.ornamentCategories
        : props.options.techniqueCategories
    const showTypes = props.type === 'ornaments' || props.type === 'techniques'
    const showRegion = props.type === 'motifs' || props.type === 'regional-motifs'
    const showRegionGroup = props.type === 'regions'
    const showOrnaments = props.type === 'regions' || props.type === 'motifs' || props.type === 'regional-motifs'
    const showTechniques = props.type === 'regions' || props.type === 'motifs' || props.type === 'regional-motifs'
    const showMotifs = false

    return (
        <AdminModal
            open={props.open}
            onClose={props.onClose}
            closeDisabled={props.saving}
            maxWidth="md"
            title={t(props.entity ? 'admin.form.editTitle' : 'admin.form.addTitle')}
            actions={
                <>
                    <Button onClick={props.onClose} disabled={props.saving}>{t('admin.cancel')}</Button>
                    <Button type="submit" form={formId} variant="contained" disabled={props.saving}>
                        {props.saving ? t('forms.saving') : t('forms.save')}
                    </Button>
                </>
            }
        >
            <Box component="form" id={formId} onSubmit={submit}>
                <Stack spacing={3}>
                    {props.error && <Alert severity="error">{props.error}</Alert>}
                    <FormSection title={t('admin.form.identity')}>
                        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
                            <FormTextField name="localName" label="Local name" required disabled={Boolean(props.entity)}
                                value={form.localName} onChange={event => set('localName', event.target.value)} />
                            {showTypes && <OntologyRelationshipField label={t('admin.form.types')}
                                options={typeOptions} value={form.typeLocalNames ?? []}
                                onChange={value => set('typeLocalNames', value)} />}
                            <FormTextField name="labelBg" label={t('admin.form.labelBg')} value={form.labelBg ?? ''}
                                onChange={event => set('labelBg', event.target.value)} />
                            <FormTextField name="labelEn" label={t('admin.form.labelEn')} value={form.labelEn ?? ''}
                                onChange={event => set('labelEn', event.target.value)} />
                            <FormStringListField key={`${props.entity?.localName ?? 'new'}-alt-bg`} name="altLabelsBg" label={t('admin.form.altLabelsBg')}
                                helperText={t('admin.form.altLabelsHelp')} value={form.altLabelsBg}
                                onChange={value => set('altLabelsBg', value)} />
                            <FormStringListField key={`${props.entity?.localName ?? 'new'}-alt-en`} name="altLabelsEn" label={t('admin.form.altLabelsEn')}
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
                                {showRegionGroup && <OntologyRelationshipField mode="single" label={t('admin.form.regionGroup')}
                                    options={props.options.regionGroups} value={form.regionGroupLocalName ? [form.regionGroupLocalName] : []}
                                    onChange={value => set('regionGroupLocalName', value[0] ?? '')} />}
                                {showRegion && <OntologyRelationshipField mode="single" label={t('admin.form.region')}
                                    options={props.options.regions} value={form.regionLocalName ? [form.regionLocalName] : []}
                                    onChange={value => set('regionLocalName', value[0] ?? '')} />}
                                {showOrnaments && <OntologyRelationshipField key={`${props.entity?.localName ?? 'new'}-ornaments`} label={t('admin.entities.ornaments')}
                                    options={props.options.ornaments} value={form.ornamentLocalNames ?? []}
                                    categories={props.options.ornamentCategories}
                                    onChange={value => set('ornamentLocalNames', value)} />}
                                {showTechniques && <OntologyRelationshipField key={`${props.entity?.localName ?? 'new'}-techniques`} label={t('admin.entities.techniques')}
                                    options={props.options.techniques} value={form.techniqueLocalNames ?? []}
                                    categories={props.options.techniqueCategories}
                                    onChange={value => set('techniqueLocalNames', value)} />}
                                {showMotifs && <OntologyRelationshipField label={t('admin.entities.motifs')}
                                    options={props.options.motifs} value={form.motifLocalNames ?? []}
                                    onChange={value => set('motifLocalNames', value)} />}
                            </Stack>
                        </FormSection>
                    )}
                    <FormSection title={t('admin.form.changeReasonSection')}>
                        <FormTextField name="changeReason" label={t('admin.form.changeReason')} multiline minRows={2}
                            helperText={t('admin.form.changeReasonHelp')} value={changeReason}
                            onChange={event => setChangeReason(event.target.value)} />
                    </FormSection>
                </Stack>
            </Box>
        </AdminModal>
    )
}

export default OntologyEntityDialog

import {
    Alert, Button, Checkbox, Divider, FormControlLabel, FormGroup, Grid, Stack,
    TextField, Typography,
} from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { apiErrorMessages } from '../../../api/http'
import { roleNames, type RoleName } from '../../../app/permissions'
import { isFutureServerDateTime } from '../../../app/serverDateTime'
import type { UserCreateCommand, UserDetails, UserProfile } from '../../../types/userAdmin'
import AdminModal from '../AdminModal'

const emptyProfile: UserProfile = {
    firstName: '', lastName: '', email: '', phone: '', addressLine1: '', addressLine2: '',
    city: '', postalCode: '', countryCode: '',
}

type Props = {
    open: boolean
    user?: UserDetails | null
    pending: boolean
    onClose: () => void
    onCreate: (command: UserCreateCommand) => Promise<void>
    onUpdate: (profile: UserProfile, roles: RoleName[]) => Promise<void>
    onUnlock?: () => void
}

function nullable(value: FormDataEntryValue | null) {
    const text = String(value ?? '').trim()
    return text || null
}

export default function UserEditorDialog({ open, user, pending, onClose, onCreate, onUpdate, onUnlock }: Props) {
    const { t } = useTranslation()
    const [error, setError] = useState<string[] | null>(null)
    const [roles, setRoles] = useState<RoleName[]>(user?.roles ?? ['EDITOR'])

    async function submit(event: React.SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        if (roles.length === 0) {
            setError([t('users.validation.role')])
            return
        }

        const data = new FormData(event.currentTarget)
        const profile: UserProfile = {
            firstName: String(data.get('firstName') ?? '').trim(),
            lastName: String(data.get('lastName') ?? '').trim(),
            email: nullable(data.get('email')),
            phone: nullable(data.get('phone')),
            addressLine1: nullable(data.get('addressLine1')),
            addressLine2: nullable(data.get('addressLine2')),
            city: nullable(data.get('city')),
            postalCode: nullable(data.get('postalCode')),
            countryCode: nullable(data.get('countryCode'))?.toUpperCase() ?? null,
        }

        setError(null)
        try {
            if (user) await onUpdate(profile, roles)
            else await onCreate({ username: String(data.get('username') ?? '').trim(), profile, roles })
        } catch (cause) {
            setError(apiErrorMessages(cause, t('users.errors.save')))
        }
    }

    function toggleRole(role: RoleName) {
        setRoles(current => current.includes(role) ? current.filter(item => item !== role) : [...current, role])
    }

    const profile = user?.profile ?? emptyProfile
    const activelyLocked = isFutureServerDateTime(user?.lockedUntil)

    return (
        <AdminModal
            open={open}
            title={user ? t('users.edit.title', { username: user.username }) : t('users.create.title')}
            description={user ? t('users.edit.description') : t('users.create.description')}
            onClose={onClose}
            closeDisabled={pending}
            actions={
                <>
                    <Button onClick={onClose} disabled={pending}>{t('admin.cancel')}</Button>
                    <Button type="submit" form="user-editor-form" variant="contained" disabled={pending}>
                        {pending ? t('forms.saving') : t('forms.save')}
                    </Button>
                </>
            }
        >
            <Stack id="user-editor-form" component="form" onSubmit={submit} spacing={3}>
                {error && <Alert severity="error">{error.map(message => <div key={message}>{message}</div>)}</Alert>}
                {activelyLocked && onUnlock && (
                    <Alert
                        severity="warning"
                        action={<Button color="inherit" size="small" onClick={onUnlock}>{t('users.actions.unlock')}</Button>}
                    >
                        {t('users.status.locked')}
                    </Alert>
                )}
                {!user && <TextField name="username" label={t('users.fields.username')} required slotProps={{ htmlInput: { minLength: 3, maxLength: 100 } }} />}
                <Typography variant="h6">{t('users.sections.profile')}</Typography>
                <Grid container spacing={2}>
                    <Grid size={{ xs: 12, sm: 6 }}><TextField fullWidth name="firstName" label={t('users.fields.firstName')} defaultValue={profile.firstName} required /></Grid>
                    <Grid size={{ xs: 12, sm: 6 }}><TextField fullWidth name="lastName" label={t('users.fields.lastName')} defaultValue={profile.lastName} required /></Grid>
                    <Grid size={{ xs: 12, sm: 7 }}><TextField fullWidth name="email" type="email" label={t('users.fields.email')} defaultValue={profile.email ?? ''} /></Grid>
                    <Grid size={{ xs: 12, sm: 5 }}><TextField fullWidth name="phone" label={t('users.fields.phone')} defaultValue={profile.phone ?? ''} /></Grid>
                    <Grid size={12}><TextField fullWidth name="addressLine1" label={t('users.fields.addressLine1')} defaultValue={profile.addressLine1 ?? ''} /></Grid>
                    <Grid size={12}><TextField fullWidth name="addressLine2" label={t('users.fields.addressLine2')} defaultValue={profile.addressLine2 ?? ''} /></Grid>
                    <Grid size={{ xs: 12, sm: 5 }}><TextField fullWidth name="city" label={t('users.fields.city')} defaultValue={profile.city ?? ''} /></Grid>
                    <Grid size={{ xs: 7, sm: 4 }}><TextField fullWidth name="postalCode" label={t('users.fields.postalCode')} defaultValue={profile.postalCode ?? ''} /></Grid>
                    <Grid size={{ xs: 5, sm: 3 }}><TextField fullWidth name="countryCode" label={t('users.fields.countryCode')} defaultValue={profile.countryCode ?? ''} slotProps={{ htmlInput: { minLength: 2, maxLength: 2 } }} /></Grid>
                </Grid>
                <Divider />
                <Typography variant="h6">{t('users.sections.roles')}</Typography>
                <FormGroup row>
                    {roleNames.map(role => (
                        <FormControlLabel key={role} control={<Checkbox checked={roles.includes(role)} onChange={() => toggleRole(role)} />}
                            label={t(`auth.roles.${role}`)} />
                    ))}
                </FormGroup>
            </Stack>
        </AdminModal>
    )
}

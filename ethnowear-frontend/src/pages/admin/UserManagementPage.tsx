import {
    Alert, Box, Button, Chip, CircularProgress, IconButton, InputAdornment, Paper, Stack,
    Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow,
    TextField, Tooltip, Typography,
} from '@mui/material'
import AddOutlinedIcon from '@mui/icons-material/AddOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import PasswordOutlinedIcon from '@mui/icons-material/PasswordOutlined'
import SearchOutlinedIcon from '@mui/icons-material/SearchOutlined'
import ToggleOffOutlinedIcon from '@mui/icons-material/ToggleOffOutlined'
import ToggleOnOutlinedIcon from '@mui/icons-material/ToggleOnOutlined'
import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
    assignUserRole, createUser, disableUser, enableUser, getUser, getUsers,
    removeUserRole, resetUserPassword, unlockUser, updateUserProfile,
} from '../../api/UserAdminApi'
import { apiErrorMessage } from '../../api/http'
import type { RoleName } from '../../app/permissions'
import AdminPageHeader from '../../components/admin/AdminPageHeader'
import ConfirmDialog from '../../components/admin/ConfirmDialog'
import TemporaryPasswordDialog from '../../components/admin/user/TemporaryPasswordDialog'
import UserEditorDialog from '../../components/admin/user/UserEditorDialog'
import type { PageResponse, TemporaryPassword, UserCreateCommand, UserDetails, UserProfile, UserSummary } from '../../types/userAdmin'

const emptyPage: PageResponse<UserSummary> = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }

type PendingAction = 'status' | 'unlock' | 'reset' | 'save' | null

export default function UserManagementPage() {
    const { t } = useTranslation()
    const [result, setResult] = useState(emptyPage)
    const [search, setSearch] = useState('')
    const [page, setPage] = useState(0)
    const [size, setSize] = useState(20)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [createOpen, setCreateOpen] = useState(false)
    const [editing, setEditing] = useState<UserDetails | null>(null)
    const [loadingUserId, setLoadingUserId] = useState<number | null>(null)
    const [pending, setPending] = useState<PendingAction>(null)
    const [confirmation, setConfirmation] = useState<{ user: { id: number; username: string }; action: 'enable' | 'disable' | 'unlock' | 'reset' } | null>(null)
    const [credentials, setCredentials] = useState<TemporaryPassword | null>(null)

    const load = useCallback((signal?: AbortSignal) => {
        setLoading(true)
        setError(null)
        return getUsers({ search: search.trim() || undefined, page, size, sort: 'username,asc' }, signal)
            .then(setResult)
            .catch(cause => {
                if (cause instanceof DOMException && cause.name === 'AbortError') return
                setError(apiErrorMessage(cause, t('users.errors.load')))
            })
            .finally(() => { if (!signal?.aborted) setLoading(false) })
    }, [page, search, size, t])

    useEffect(() => {
        const controller = new AbortController()
        const timer = setTimeout(() => void load(controller.signal), 300)
        return () => { clearTimeout(timer); controller.abort() }
    }, [load])

    async function openUser(userId: number) {
        setLoadingUserId(userId)
        setError(null)
        try { setEditing(await getUser(userId)) }
        catch (cause) { setError(apiErrorMessage(cause, t('users.errors.details'))) }
        finally { setLoadingUserId(null) }
    }

    async function handleCreate(command: UserCreateCommand) {
        setPending('save')
        try {
            const created = await createUser(command)
            setCreateOpen(false)
            setCredentials(created.credentials)
            await load()
        } finally { setPending(null) }
    }

    async function handleUpdate(profile: UserProfile, roles: RoleName[]) {
        if (!editing) return
        setPending('save')
        try {
            await updateUserProfile(editing.id, profile)
            const additions = roles.filter(role => !editing.roles.includes(role))
            const removals = editing.roles.filter(role => !roles.includes(role))
            for (const role of additions) await assignUserRole(editing.id, role)
            for (const role of removals) await removeUserRole(editing.id, role)
            setEditing(null)
            await load()
        } finally { setPending(null) }
    }

    async function runConfirmedAction() {
        if (!confirmation) return
        const { user, action } = confirmation
        setPending(action === 'reset' ? 'reset' : action === 'unlock' ? 'unlock' : 'status')
        setError(null)
        try {
            if (action === 'reset') setCredentials(await resetUserPassword(user.id))
            else if (action === 'unlock') {
                const updated = await unlockUser(user.id)
                if (editing?.id === user.id) setEditing(updated)
            }
            else if (action === 'enable') await enableUser(user.id)
            else await disableUser(user.id)
            setConfirmation(null)
            await load()
        } catch (cause) {
            setError(apiErrorMessage(cause, t('users.errors.action')))
            setConfirmation(null)
        } finally { setPending(null) }
    }

    return (
        <Stack spacing={3}>
            <AdminPageHeader title={t('users.title')} description={t('users.description')}
                actions={<Button variant="contained" startIcon={<AddOutlinedIcon />} onClick={() => setCreateOpen(true)}>{t('users.create.action')}</Button>} />
            {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
            <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
                <Box sx={{ p: 2 }}>
                    <TextField size="small" value={search} onChange={event => { setSearch(event.target.value); setPage(0) }}
                        placeholder={t('users.search')} sx={{ width: '100%', maxWidth: 560 }}
                        slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchOutlinedIcon fontSize="small" /></InputAdornment> } }} />
                </Box>
                <TableContainer>
                    <Table size="small">
                        <TableHead><TableRow>
                            <TableCell>{t('users.fields.username')}</TableCell>
                            <TableCell>{t('users.fields.name')}</TableCell>
                            <TableCell>{t('users.fields.email')}</TableCell>
                            <TableCell>{t('users.sections.roles')}</TableCell>
                            <TableCell>{t('users.fields.status')}</TableCell>
                            <TableCell align="right">{t('admin.columns.actions')}</TableCell>
                        </TableRow></TableHead>
                        <TableBody>
                            {loading ? <TableRow><TableCell colSpan={6} align="center" sx={{ py: 8 }}><CircularProgress size={28} /></TableCell></TableRow>
                                : result.content.length === 0 ? <TableRow><TableCell colSpan={6} align="center" sx={{ py: 8 }}>{t('admin.noResults')}</TableCell></TableRow>
                                    : result.content.map(user => (
                                        <TableRow key={user.id} hover>
                                            <TableCell><Typography sx={{ fontWeight: 700 }}>{user.username}</Typography></TableCell>
                                            <TableCell>{user.firstName} {user.lastName}</TableCell>
                                            <TableCell>{user.email || t('admin.archive.none')}</TableCell>
                                            <TableCell><Stack direction="row" sx={{ gap: .5, flexWrap: 'wrap' }}>{user.roles.map(role => <Chip key={role} size="small" label={t(`auth.roles.${role}`)} />)}</Stack></TableCell>
                                            <TableCell><Stack direction="row" sx={{ gap: .5, flexWrap: 'wrap' }}>
                                                <Chip size="small" color={user.enabled ? 'success' : 'default'} label={t(user.enabled ? 'users.status.enabled' : 'users.status.disabled')} />
                                                {user.passwordChangeRequired && <Chip size="small" color="warning" label={t('users.status.passwordChange')} />}
                                            </Stack></TableCell>
                                            <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                                                <Tooltip title={t('admin.edit')}><span><IconButton size="small" disabled={loadingUserId === user.id} onClick={() => void openUser(user.id)}><EditOutlinedIcon fontSize="small" /></IconButton></span></Tooltip>
                                                <Tooltip title={t(user.enabled ? 'users.actions.disable' : 'users.actions.enable')}><IconButton size="small" onClick={() => setConfirmation({ user, action: user.enabled ? 'disable' : 'enable' })}>{user.enabled ? <ToggleOffOutlinedIcon fontSize="small" /> : <ToggleOnOutlinedIcon fontSize="small" />}</IconButton></Tooltip>
                                                <Tooltip title={t('users.actions.resetPassword')}><IconButton size="small" color="warning" onClick={() => setConfirmation({ user, action: 'reset' })}><PasswordOutlinedIcon fontSize="small" /></IconButton></Tooltip>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                        </TableBody>
                    </Table>
                </TableContainer>
                <TablePagination component="div" count={result.totalElements} page={page} rowsPerPage={size}
                    onPageChange={(_, value) => setPage(value)} onRowsPerPageChange={event => { setSize(Number(event.target.value)); setPage(0) }}
                    rowsPerPageOptions={[20, 50, 100]} />
            </Paper>
            {createOpen && <UserEditorDialog key="create" open pending={pending === 'save'} onClose={() => setCreateOpen(false)} onCreate={handleCreate} onUpdate={async () => undefined} />}
            {editing && <UserEditorDialog key={editing.id} open user={editing} pending={pending === 'save'} onClose={() => setEditing(null)}
                onCreate={async () => undefined} onUpdate={handleUpdate}
                onUnlock={() => setConfirmation({ user: { id: editing.id, username: editing.username }, action: 'unlock' })} />}
            <TemporaryPasswordDialog open={credentials !== null} credentials={credentials} onClose={() => setCredentials(null)} />
            <ConfirmDialog open={confirmation !== null} title={t(`users.confirm.${confirmation?.action ?? 'enable'}.title`)} pending={pending !== null}
                onCancel={() => setConfirmation(null)} onConfirm={() => void runConfirmedAction()} confirmLabel={t(`users.actions.${confirmation?.action === 'reset' ? 'resetPassword' : confirmation?.action ?? 'enable'}`)}>
                {t(`users.confirm.${confirmation?.action ?? 'enable'}.message`, { username: confirmation?.user.username })}
            </ConfirmDialog>
        </Stack>
    )
}

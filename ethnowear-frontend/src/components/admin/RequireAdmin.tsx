import type { ReactNode } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { useAdminAuth } from '../../app/adminAuth'
import { hasAnyRole, managementRoles, type RoleName } from '../../app/permissions'
import PageLoading from '../loading/PageLoading'
import PermissionDenied from './PermissionDenied'
import AdminLoginDialog from './AdminLoginDialog'

type Props = {
    children: ReactNode
    roles?: readonly RoleName[]
    allowPasswordChange?: boolean
}

function RequireAdmin({ children, roles = managementRoles, allowPasswordChange = false }: Props) {
    const { admin, authenticated, initializing, sessionExpired, logout } = useAdminAuth()
    const navigate = useNavigate()

    function closeExpiredLogin() {
        logout()
        navigate('/archive', { replace: true })
    }
    if (initializing) {
        return <PageLoading />
    }

    if (!authenticated) {
        if (!sessionExpired) return <AdminLoginDialog open />

        return <>
            <div inert aria-hidden="true" style={{ display: 'contents', pointerEvents: 'none' }}>{children}</div>
            <AdminLoginDialog open onClose={closeExpiredLogin} />
        </>
    }

    if (admin?.passwordChangeRequired && !allowPasswordChange) {
        return <Navigate to="/account/password" replace />
    }

    if (!admin || !hasAnyRole(admin.roles, roles)) {
        return <PermissionDenied />
    }

    return <>
        <div style={{ display: 'contents' }}>{children}</div>
    </>
}

export default RequireAdmin

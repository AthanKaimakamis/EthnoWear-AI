import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
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
    const { admin, authenticated, initializing, sessionExpired } = useAdminAuth()
    if (initializing) {
        return <PageLoading />
    }

    if (!authenticated) {
        return <>
            {sessionExpired && <div aria-hidden="true" style={{ pointerEvents: 'none' }}>{children}</div>}
            <AdminLoginDialog open />
        </>
    }

    if (admin?.passwordChangeRequired && !allowPasswordChange) {
        return <Navigate to="/account/password" replace />
    }

    if (!admin || !hasAnyRole(admin.roles, roles)) {
        return <PermissionDenied />
    }

    return children
}

export default RequireAdmin

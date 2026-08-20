import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAdminAuth } from '../../app/adminAuth'
import { hasAnyRole, managementRoles, type RoleName } from '../../app/permissions'
import PageLoading from '../loading/PageLoading'
import PermissionDenied from './PermissionDenied'

type Props = {
    children: ReactNode
    roles?: readonly RoleName[]
    allowPasswordChange?: boolean
}

function RequireAdmin({ children, roles = managementRoles, allowPasswordChange = false }: Props) {
    const { admin, authenticated, initializing } = useAdminAuth()
    const location = useLocation()

    if (initializing) {
        return <PageLoading />
    }

    if (!authenticated) {
        return <Navigate to="/admin/login" replace state={{ from: location.pathname }} />
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

import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAdminAuth } from '../../app/adminAuth'

function RequireAdmin({ children }: { children: ReactNode }) {
    const { authenticated } = useAdminAuth()
    const location = useLocation()

    if (!authenticated) {
        return <Navigate to="/admin/login" replace state={{ from: location.pathname }} />
    }

    return children
}

export default RequireAdmin

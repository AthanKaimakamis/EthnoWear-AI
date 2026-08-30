import { Navigate, useNavigate } from 'react-router'
import { useAdminAuth } from '../../app/adminAuth'
import AdminLoginDialog from '../../components/admin/AdminLoginDialog'

export default function AdminLoginPage() {
    const { authenticated, initializing } = useAdminAuth()
    const navigate = useNavigate()
    if (!initializing && authenticated) return <Navigate to="/management" replace />
    return <AdminLoginDialog open redirectAfterLogin onClose={() => navigate('/archive')} />
}

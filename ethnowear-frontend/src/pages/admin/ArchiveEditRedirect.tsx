import { Navigate, useParams } from 'react-router'

export default function ArchiveEditRedirect() {
    const { id } = useParams()
    return <Navigate to={`/management/archive?edit=${encodeURIComponent(id ?? '')}`} replace />
}

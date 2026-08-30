import { Alert } from '@mui/material'
import { useNavigate, useParams } from 'react-router'
import DocumentPageReviewDialog from '../../components/admin/document/DocumentPageReviewDialog'

export default function DocumentPageDetailPage() {
    const navigate = useNavigate()
    const documentId = Number(useParams().documentId)
    const pageId = Number(useParams().pageId)
    if (!Number.isInteger(documentId) || documentId < 1 || !Number.isInteger(pageId) || pageId < 1) return <Alert severity="error">Invalid document page.</Alert>
    return <DocumentPageReviewDialog open documentId={documentId} pageId={pageId} onClose={() => navigate(`/management/documents/${documentId}?tab=pages`)} />
}

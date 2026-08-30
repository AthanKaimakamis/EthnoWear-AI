import { Suspense } from 'react'
import { createBrowserRouter, Navigate, useLocation } from 'react-router'
import MainLayout from "../components/layout/MainLayout.tsx";
import RequireAdmin from "../components/admin/RequireAdmin.tsx";
import { administratorRoles, processingReadRoles, reviewRoles } from './permissions'
import RouterErrorPage from "../pages/RouterErrorPage.tsx";
import ArchiveLayout from '../components/archive/ArchiveLayout'
import EmbroideryPageSkeleton from '../components/loading/EmbroideryPageSkeleton'
import ArchiveReferencePageSkeleton from '../components/loading/ArchiveReferencePageSkeleton'
import {
    AdminLayout,
    AdminLoginPage,
    ArchiveAdminPage,
    ArchiveEditRedirect,
    ArchiveItemDetailPage,
    ArchiveManagerPage,
    ArchivePage,
    ArchiveReferencePage,
    DocumentsPage,
    DocumentDetailPage,
    DocumentPageDetailPage,
    EntityDetailPage,
    MediaLibraryPage,
    OntologyEntityPage,
    ProcessingStatusPage,
    PasswordChangePage,
    UserManagementPage,
} from './routeComponents'

export const router = createBrowserRouter([
    {
        path: '/',
        element: <MainLayout />,
        errorElement: <RouterErrorPage />,
        children: [
            { index: true, element: <Navigate to="archive/embroideries" replace /> },
            {
                path: 'archive',
                element: <ArchiveLayout />,
                children: [
                    { index: true, element: <Navigate to="embroideries" replace /> },
                    {
                        path: 'embroideries',
                        element: <Suspense fallback={<EmbroideryPageSkeleton />}><ArchivePage /></Suspense>,
                    },
                    {
                        path: 'motifs',
                        element: <Suspense fallback={<ArchiveReferencePageSkeleton />}><ArchiveReferencePage key="motifs" kind="motifs" /></Suspense>,
                    },
                    {
                        path: 'techniques',
                        element: <Suspense fallback={<ArchiveReferencePageSkeleton />}><ArchiveReferencePage key="techniques" kind="techniques" /></Suspense>,
                    },
                    {
                        path: 'ornaments',
                        element: <Suspense fallback={<ArchiveReferencePageSkeleton />}><ArchiveReferencePage key="ornaments" kind="ornaments" /></Suspense>,
                    },
                    { path: 'items/:id', element: <ArchiveItemDetailPage /> },
                    { path: ':entityType/:localName', element: <EntityDetailPage /> },
                ],
            },
            {
                path: 'management/login',
                element: <AdminLoginPage />,
            },
            {
                path: 'account/password',
                element: <RequireAdmin allowPasswordChange><PasswordChangePage /></RequireAdmin>,
            },
            {
                path: 'management',
                element: <RequireAdmin><AdminLayout /></RequireAdmin>,
                children: [
                    { index: true, element: <Navigate to="archive" replace /> },
                    { path: 'archive', element: <ArchiveManagerPage /> },
                    { path: 'archive/new', element: <Navigate to="/management/archive?create=1" replace /> },
                    { path: 'archive/:id/edit', element: <ArchiveEditRedirect /> },
                    { path: 'documents', element: <DocumentsPage /> },
                    { path: 'documents/:documentId', element: <DocumentDetailPage /> },
                    { path: 'documents/:documentId/pages/:pageId', element: <DocumentPageDetailPage /> },
                    { path: 'media', element: <MediaLibraryPage /> },
                    { path: 'processing', element: <RequireAdmin roles={processingReadRoles}><ProcessingStatusPage /></RequireAdmin> },
                    { path: 'users', element: <RequireAdmin roles={administratorRoles}><UserManagementPage /></RequireAdmin> },
                    { path: 'ontology', element: <Navigate to="/management/ornaments" replace /> },
                    { path: 'advanced/:resource', element: <RequireAdmin roles={reviewRoles}><ArchiveAdminPage /></RequireAdmin> },
                    { path: 'archive/:resource', element: <ArchiveAdminPage /> },
                    { path: ':entityType', element: <OntologyEntityPage /> },
                ],
            },
            { path: 'admin/*', element: <LegacyAdminRedirect /> },
        ]
    }
])

function LegacyAdminRedirect() {
    const location = useLocation()
    const path = location.pathname.replace(/^\/admin(?=\/|$)/, '/management')
    return <Navigate to={`${path}${location.search}${location.hash}`} replace />
}

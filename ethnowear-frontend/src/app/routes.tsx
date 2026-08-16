import { Suspense } from 'react'
import { createBrowserRouter, Navigate } from 'react-router'
import MainLayout from "../components/layout/MainLayout.tsx";
import RequireAdmin from "../components/admin/RequireAdmin.tsx";
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
    EntityDetailPage,
    MaterialUiTestPage,
    MediaLibraryPage,
    OntologyEntityPage,
    ProcessingStatusPage,
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
                path: 'admin/login',
                element: <AdminLoginPage />,
            },
            {
                path: 'admin',
                element: <RequireAdmin><AdminLayout /></RequireAdmin>,
                children: [
                    { index: true, element: <Navigate to="archive" replace /> },
                    { path: 'archive', element: <ArchiveManagerPage /> },
                    { path: 'archive/new', element: <Navigate to="/admin/archive?create=1" replace /> },
                    { path: 'archive/:id/edit', element: <ArchiveEditRedirect /> },
                    { path: 'documents', element: <DocumentsPage /> },
                    { path: 'media', element: <MediaLibraryPage /> },
                    { path: 'processing', element: <ProcessingStatusPage /> },
                    { path: 'ontology', element: <Navigate to="/admin/ornaments" replace /> },
                    { path: 'advanced/:resource', element: <ArchiveAdminPage /> },
                    { path: 'archive/:resource', element: <ArchiveAdminPage /> },
                    { path: ':entityType', element: <OntologyEntityPage /> },
                ],
            },
            { path: 'test', element: <MaterialUiTestPage/> },
        ]
    }
])

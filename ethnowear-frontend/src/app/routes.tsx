import {createBrowserRouter, Navigate} from "react-router";
import MainLayout from "../components/layout/MainLayout.tsx";
import MaterialUiTestPage from "../pages/MaterialUiTestPage.tsx";
import ArchivePage from "../pages/ArchivePage.tsx";
import AdminLayout from "../components/admin/AdminLayout.tsx";
import OntologyEntityPage from "../pages/admin/OntologyEntityPage.tsx";
import ArchiveLayout from "../components/archive/ArchiveLayout.tsx";
import ArchiveReferencePage from "../pages/archive/ArchiveReferencePage.tsx";


export const router = createBrowserRouter([
    {
        path: '/',
        element: <MainLayout />,
        children: [
            { index: true, element: <Navigate to="archive/embroideries" replace /> },
            {
                path: 'archive',
                element: <ArchiveLayout />,
                children: [
                    { index: true, element: <Navigate to="embroideries" replace /> },
                    { path: 'embroideries', element: <ArchivePage /> },
                    { path: 'motifs', element: <ArchiveReferencePage kind="motifs" /> },
                    { path: 'techniques', element: <ArchiveReferencePage kind="techniques" /> },
                    { path: 'ornaments', element: <ArchiveReferencePage kind="ornaments" /> },
                ],
            },
            {
                path: 'admin',
                element: <AdminLayout />,
                children: [
                    { index: true, element: <Navigate to="ornaments" replace /> },
                    { path: ':entityType', element: <OntologyEntityPage /> },
                ],
            },
            { path: 'test', element: <MaterialUiTestPage/> },
        ]
    }
])

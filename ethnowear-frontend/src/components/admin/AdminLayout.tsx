import { useState } from 'react'
import {
    Box, Collapse, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText,
    Tooltip, useMediaQuery, useTheme,
} from '@mui/material'
import CategoryOutlinedIcon from '@mui/icons-material/CategoryOutlined'
import DesignServicesOutlinedIcon from '@mui/icons-material/DesignServicesOutlined'
import HubOutlinedIcon from '@mui/icons-material/HubOutlined'
import MapOutlinedIcon from '@mui/icons-material/MapOutlined'
import MenuOpenIcon from '@mui/icons-material/MenuOpen'
import TextureOutlinedIcon from '@mui/icons-material/TextureOutlined'
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined'
import MenuBookOutlinedIcon from '@mui/icons-material/MenuBookOutlined'
import BookmarkBorderOutlinedIcon from '@mui/icons-material/BookmarkBorderOutlined'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import PermMediaOutlinedIcon from '@mui/icons-material/PermMediaOutlined'
import LinkOutlinedIcon from '@mui/icons-material/LinkOutlined'
import CropFreeOutlinedIcon from '@mui/icons-material/CropFreeOutlined'
import ArticleOutlinedIcon from '@mui/icons-material/ArticleOutlined'
import FolderCopyOutlinedIcon from '@mui/icons-material/FolderCopyOutlined'
import CollectionsOutlinedIcon from '@mui/icons-material/CollectionsOutlined'
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined'
import PendingActionsOutlinedIcon from '@mui/icons-material/PendingActionsOutlined'
import PeopleAltOutlinedIcon from '@mui/icons-material/PeopleAltOutlined'
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined'
import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { NavLink, Outlet, useLocation } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAdminAuth } from '../../app/adminAuth'
import { administratorRoles, hasAnyRole, managementRoles, processingReadRoles, reviewRoles, rightsManagementRoles, type RoleName } from '../../app/permissions'
import { ManagementEventsProvider } from '../../app/ManagementEventsProvider'

const drawerWidth = 248
type NavigationItem = {
    path: string
    key: string
    icon: typeof CategoryOutlinedIcon
    roles: readonly RoleName[]
}

const ontologyItems: NavigationItem[] = [
    { path: '/management/ornaments', key: 'ornaments', icon: CategoryOutlinedIcon, roles: managementRoles },
    { path: '/management/techniques', key: 'techniques', icon: DesignServicesOutlinedIcon, roles: managementRoles },
    { path: '/management/motifs', key: 'motifs', icon: HubOutlinedIcon, roles: managementRoles },
    { path: '/management/regions', key: 'regions', icon: MapOutlinedIcon, roles: managementRoles },
    { path: '/management/regional-embroideries', key: 'regionalEmbroideries', icon: TextureOutlinedIcon, roles: managementRoles },
    { path: '/management/regional-motifs', key: 'regionalMotifs', icon: HubOutlinedIcon, roles: managementRoles },
    { path: '/management/ontology/versions', key: 'ontologyVersions', icon: HistoryOutlinedIcon, roles: administratorRoles },
]

const primaryItems: NavigationItem[] = [
    { path: '/management/archive', key: 'archive', icon: Inventory2OutlinedIcon, roles: managementRoles },
    { path: '/management/documents', key: 'documents', icon: FolderCopyOutlinedIcon, roles: managementRoles },
    { path: '/management/media', key: 'media', icon: CollectionsOutlinedIcon, roles: managementRoles },
    { path: '/management/processing', key: 'processing', icon: PendingActionsOutlinedIcon, roles: processingReadRoles },
]
const archiveItems: NavigationItem[] = [
    { path: '/management/advanced/archive-items', key: 'archiveitems', icon: Inventory2OutlinedIcon, roles: reviewRoles },
    { path: '/management/advanced/sources', key: 'sources', icon: MenuBookOutlinedIcon, roles: rightsManagementRoles },
    { path: '/management/advanced/source-references', key: 'sourcereferences', icon: BookmarkBorderOutlinedIcon, roles: reviewRoles },
    { path: '/management/advanced/archive-item-features', key: 'archiveitemfeatures', icon: FactCheckOutlinedIcon, roles: reviewRoles },
    { path: '/management/advanced/media-assets', key: 'mediaassets', icon: PermMediaOutlinedIcon, roles: rightsManagementRoles },
    { path: '/management/advanced/archive-item-media', key: 'archiveitemmedia', icon: LinkOutlinedIcon, roles: reviewRoles },
    { path: '/management/advanced/media-feature-annotations', key: 'mediafeatureannotations', icon: CropFreeOutlinedIcon, roles: reviewRoles },
    { path: '/management/advanced/knowledge-chunks', key: 'knowledgechunks', icon: ArticleOutlinedIcon, roles: reviewRoles },
]

function AdminLayout() {
    const { t } = useTranslation()
    const { admin } = useAdminAuth()
    const location = useLocation()
    const desktop = useMediaQuery(useTheme().breakpoints.up('md'))
    const [open, setOpen] = useState(false)
    const ontologyActive = ontologyItems.some(item => location.pathname.startsWith(item.path))
    const [ontologyOpen, setOntologyOpen] = useState(ontologyActive)
    const [advancedOpen, setAdvancedOpen] = useState(false)

    const visiblePrimaryItems = primaryItems.filter(item => admin && hasAnyRole(admin.roles, item.roles))
    const visibleOntologyItems = ontologyItems.filter(item => admin && hasAnyRole(admin.roles, item.roles))
    const visibleArchiveItems = archiveItems.filter(item => admin && hasAnyRole(admin.roles, item.roles))

    const navigation = (
        <Box sx={{ width: drawerWidth, minHeight: 'calc(100dvh - 68px)', py: 2, display: 'flex', flexDirection: 'column' }}>
            <List sx={{ px: 1.25 }}>
                {visiblePrimaryItems.map((item) => {
                    const Icon = item.icon
                    return (
                        <ListItemButton key={item.path} component={NavLink} to={item.path}
                            onClick={() => setOpen(false)}
                            sx={{ mb: .5, borderRadius: 1, borderLeft: 3, borderColor: 'transparent',
                                '&.active': { color: 'primary.main', bgcolor: '#F4E9EB', borderLeftColor: 'primary.main' } }}>
                            <ListItemIcon sx={{ minWidth: 40, color: 'inherit' }}><Icon fontSize="small" /></ListItemIcon>
                            <ListItemText primary={t(`curator.navigation.${item.key}`)} />
                        </ListItemButton>
                    )
                })}
            </List>
            <Divider sx={{ my: 1.5 }} />
            <List sx={{ px: 1.25, py: 0 }}>
                <ListItemButton
                    onClick={() => setOntologyOpen(value => !value)}
                    sx={{
                        mb: .5,
                        borderRadius: 1,
                        borderLeft: 3,
                        borderColor: ontologyActive ? 'primary.main' : 'transparent',
                        color: ontologyActive ? 'primary.main' : 'inherit',
                        bgcolor: ontologyActive ? '#F4E9EB' : 'transparent',
                    }}
                >
                    <ListItemIcon sx={{ minWidth: 40, color: 'inherit' }}><AccountTreeOutlinedIcon fontSize="small" /></ListItemIcon>
                    <ListItemText primary={t('curator.navigation.ontology')} />
                    {ontologyOpen ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                </ListItemButton>
                <Collapse in={ontologyOpen} unmountOnExit>
                    <List disablePadding sx={{ pl: 2 }}>
                        {visibleOntologyItems.map((item) => {
                            const Icon = item.icon
                            return (
                                <ListItemButton
                                    key={item.path}
                                    component={NavLink}
                                    to={item.path}
                                    onClick={() => setOpen(false)}
                                    sx={{
                                        mb: .5,
                                        borderRadius: 1,
                                        '&.active': { color: 'primary.main', bgcolor: '#F4E9EB' },
                                    }}
                                >
                                    <ListItemIcon sx={{ minWidth: 36, color: 'inherit' }}><Icon fontSize="small" /></ListItemIcon>
                                    <ListItemText primary={item.key === 'ontologyVersions' ? t('ontologyVersions.navigation') : t(`admin.entities.${item.key}`)} />
                                </ListItemButton>
                            )
                        })}
                    </List>
                </Collapse>
                {visibleArchiveItems.length > 0 && <ListItemButton onClick={() => setAdvancedOpen(value => !value)}><ListItemIcon sx={{ minWidth: 40 }}><ArticleOutlinedIcon fontSize="small" /></ListItemIcon><ListItemText primary={t('curator.navigation.advanced')} />{advancedOpen ? <ExpandLessIcon /> : <ExpandMoreIcon />}</ListItemButton>}
                <Collapse in={advancedOpen} unmountOnExit><List disablePadding sx={{ pl: 2 }}>
                {visibleArchiveItems.map((item) => {
                    const Icon = item.icon
                    return (
                        <ListItemButton key={item.path} component={NavLink} to={item.path}
                            onClick={() => setOpen(false)}
                            sx={{ mb: .5, borderRadius: 1, borderLeft: 3, borderColor: 'transparent',
                                '&.active': { color: 'primary.main', bgcolor: '#F4E9EB', borderLeftColor: 'primary.main' } }}>
                            <ListItemIcon sx={{ minWidth: 40, color: 'inherit' }}><Icon fontSize="small" /></ListItemIcon>
                            <ListItemText primary={t(`admin.archive.resources.${item.key}`)} />
                        </ListItemButton>
                    )
                })}
                </List></Collapse>
                {admin && hasAnyRole(admin.roles, administratorRoles) && (
                    <ListItemButton component={NavLink} to="/management/users" onClick={() => setOpen(false)}
                        sx={{ mt: .5, borderRadius: 1, borderLeft: 3, borderColor: 'transparent', '&.active': { color: 'primary.main', bgcolor: '#F4E9EB', borderLeftColor: 'primary.main' } }}>
                        <ListItemIcon sx={{ minWidth: 40, color: 'inherit' }}><PeopleAltOutlinedIcon fontSize="small" /></ListItemIcon>
                        <ListItemText primary={t('curator.navigation.users')} />
                    </ListItemButton>
                )}
            </List>
        </Box>
    )

    return (
        <ManagementEventsProvider><Box sx={{ display: 'flex', minHeight: 'calc(100dvh - 68px)' }}>
            {desktop ? (
                <Box component="aside" sx={{ width: drawerWidth, flexShrink: 0, bgcolor: 'background.paper', borderRight: 1, borderColor: 'divider' }}>
                    {navigation}
                </Box>
            ) : (
                <>
                    <Tooltip title={t('admin.openMenu')}>
                        <IconButton onClick={() => setOpen(true)} sx={{ position: 'fixed', left: 12, bottom: 16, zIndex: 1100,
                            bgcolor: 'primary.main', color: 'primary.contrastText', boxShadow: 3,
                            '&:hover': { bgcolor: 'primary.dark' } }}><MenuOpenIcon /></IconButton>
                    </Tooltip>
                    <Drawer open={open} onClose={() => setOpen(false)}>{navigation}</Drawer>
                </>
            )}
            <Box component="main" sx={{ minWidth: 0, flex: 1, px: { xs: 2, sm: 3, lg: 5 }, py: { xs: 3, md: 4 } }}>
                <Outlet />
            </Box>
        </Box></ManagementEventsProvider>
    )
}

export default AdminLayout

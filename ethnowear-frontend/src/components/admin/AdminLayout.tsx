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
import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { NavLink, Outlet, useLocation } from 'react-router'
import { useTranslation } from 'react-i18next'

const drawerWidth = 248
const ontologyItems = [
    { path: '/admin/ornaments', key: 'ornaments', icon: CategoryOutlinedIcon },
    { path: '/admin/techniques', key: 'techniques', icon: DesignServicesOutlinedIcon },
    { path: '/admin/motifs', key: 'motifs', icon: HubOutlinedIcon },
    { path: '/admin/regions', key: 'regions', icon: MapOutlinedIcon },
    { path: '/admin/regional-embroideries', key: 'regionalEmbroideries', icon: TextureOutlinedIcon },
] as const

const primaryItems = [
    { path: '/admin/archive', key: 'archive', icon: Inventory2OutlinedIcon },
    { path: '/admin/documents', key: 'documents', icon: FolderCopyOutlinedIcon },
    { path: '/admin/media', key: 'media', icon: CollectionsOutlinedIcon },
    { path: '/admin/processing', key: 'processing', icon: PendingActionsOutlinedIcon },
] as const
const archiveItems = [
    { path: '/admin/advanced/archive-items', key: 'archiveitems', icon: Inventory2OutlinedIcon },
    { path: '/admin/advanced/sources', key: 'sources', icon: MenuBookOutlinedIcon },
    { path: '/admin/advanced/source-references', key: 'sourcereferences', icon: BookmarkBorderOutlinedIcon },
    { path: '/admin/advanced/archive-item-features', key: 'archiveitemfeatures', icon: FactCheckOutlinedIcon },
    { path: '/admin/advanced/media-assets', key: 'mediaassets', icon: PermMediaOutlinedIcon },
    { path: '/admin/advanced/archive-item-media', key: 'archiveitemmedia', icon: LinkOutlinedIcon },
    { path: '/admin/advanced/media-feature-annotations', key: 'mediafeatureannotations', icon: CropFreeOutlinedIcon },
    { path: '/admin/advanced/knowledge-chunks', key: 'knowledgechunks', icon: ArticleOutlinedIcon },
] as const

function AdminLayout() {
    const { t } = useTranslation()
    const location = useLocation()
    const desktop = useMediaQuery(useTheme().breakpoints.up('md'))
    const [open, setOpen] = useState(false)
    const ontologyActive = ontologyItems.some(item => location.pathname.startsWith(item.path))
    const [ontologyOpen, setOntologyOpen] = useState(ontologyActive)
    const [advancedOpen, setAdvancedOpen] = useState(false)

    const navigation = (
        <Box sx={{ width: drawerWidth, py: 2 }}>
            <List sx={{ px: 1.25 }}>
                {primaryItems.map((item) => {
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
                        {ontologyItems.map((item) => {
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
                                    <ListItemText primary={t(`admin.entities.${item.key}`)} />
                                </ListItemButton>
                            )
                        })}
                    </List>
                </Collapse>
                <ListItemButton onClick={() => setAdvancedOpen(value => !value)}><ListItemIcon sx={{ minWidth: 40 }}><ArticleOutlinedIcon fontSize="small" /></ListItemIcon><ListItemText primary={t('curator.navigation.advanced')} />{advancedOpen ? <ExpandLessIcon /> : <ExpandMoreIcon />}</ListItemButton>
                <Collapse in={advancedOpen} unmountOnExit><List disablePadding sx={{ pl: 2 }}>
                {archiveItems.map((item) => {
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
            </List>
        </Box>
    )

    return (
        <Box sx={{ display: 'flex', minHeight: 'calc(100vh - 68px)' }}>
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
        </Box>
    )
}

export default AdminLayout

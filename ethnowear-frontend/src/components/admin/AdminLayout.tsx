import { useState } from 'react'
import {
    Box, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText,
    Tooltip, Typography, useMediaQuery, useTheme,
} from '@mui/material'
import CategoryOutlinedIcon from '@mui/icons-material/CategoryOutlined'
import DesignServicesOutlinedIcon from '@mui/icons-material/DesignServicesOutlined'
import HubOutlinedIcon from '@mui/icons-material/HubOutlined'
import MapOutlinedIcon from '@mui/icons-material/MapOutlined'
import MenuOpenIcon from '@mui/icons-material/MenuOpen'
import TextureOutlinedIcon from '@mui/icons-material/TextureOutlined'
import { NavLink, Outlet } from 'react-router'
import { useTranslation } from 'react-i18next'

const drawerWidth = 248
const items = [
    { path: '/admin/ornaments', key: 'ornaments', icon: CategoryOutlinedIcon },
    { path: '/admin/techniques', key: 'techniques', icon: DesignServicesOutlinedIcon },
    { path: '/admin/motifs', key: 'motifs', icon: HubOutlinedIcon },
    { path: '/admin/regions', key: 'regions', icon: MapOutlinedIcon },
    { path: '/admin/regional-embroideries', key: 'regionalEmbroideries', icon: TextureOutlinedIcon },
] as const

function AdminLayout() {
    const { t } = useTranslation()
    const desktop = useMediaQuery(useTheme().breakpoints.up('md'))
    const [open, setOpen] = useState(false)

    const navigation = (
        <Box sx={{ width: drawerWidth, py: 2 }}>
            <Typography variant="overline" color="text.secondary" sx={{ px: 2.5 }}>
                {t('admin.ontology')}
            </Typography>
            <List sx={{ px: 1.25 }}>
                {items.map((item) => {
                    const Icon = item.icon
                    return (
                        <ListItemButton key={item.path} component={NavLink} to={item.path}
                            onClick={() => setOpen(false)}
                            sx={{ mb: .5, borderRadius: 1, borderLeft: 3, borderColor: 'transparent',
                                '&.active': { color: 'primary.main', bgcolor: '#F4E9EB', borderLeftColor: 'primary.main' } }}>
                            <ListItemIcon sx={{ minWidth: 40, color: 'inherit' }}><Icon fontSize="small" /></ListItemIcon>
                            <ListItemText primary={t(`admin.entities.${item.key}`)} />
                        </ListItemButton>
                    )
                })}
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

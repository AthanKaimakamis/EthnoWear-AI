import { useState } from 'react'
import {
    AppBar,
    Box,
    Button,
    Divider,
    Drawer,
    FormControl,
    IconButton,
    List,
    ListItemButton,
    ListItemIcon,
    ListItemText,
    MenuItem,
    Select,
    Stack,
    Toolbar,
    Typography,
} from '@mui/material'
import AdminPanelSettingsOutlinedIcon from '@mui/icons-material/AdminPanelSettingsOutlined'
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined'
import CloseIcon from '@mui/icons-material/Close'
import MenuIcon from '@mui/icons-material/Menu'
import ScienceOutlinedIcon from '@mui/icons-material/ScienceOutlined'
import type { SelectChangeEvent } from '@mui/material/Select'
import { NavLink, Outlet } from 'react-router'
import { useTranslation } from 'react-i18next'

type NavItem = {
    translationKey: 'nav.archive' | 'nav.admin' | 'nav.test'
    path: string
    icon: typeof ArchiveOutlinedIcon
}

const navItems: NavItem[] = [
    { translationKey: 'nav.archive', path: '/archive', icon: ArchiveOutlinedIcon },
    { translationKey: 'nav.admin', path: '/admin', icon: AdminPanelSettingsOutlinedIcon },
    { translationKey: 'nav.test', path: '/test', icon: ScienceOutlinedIcon },
]

function MainLayout() {
    const { t, i18n } = useTranslation()
    const language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
    const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false)

    function handleLanguageChange(event: SelectChangeEvent) {
        void i18n.changeLanguage(event.target.value)
    }

    return (
        <Box
            sx={{
                minHeight: '100vh',
                bgcolor: 'background.default',
            }}
        >
            <AppBar
                position="sticky"
                color="default"
                elevation={0}
                sx={{
                    bgcolor: '#FFFFFF',
                    color: 'text.primary',
                    borderBottom: 1,
                    borderColor: 'divider',
                    boxShadow: '0 2px 6px rgba(24, 28, 24, 0.10)',
                }}
            >
                <Toolbar
                    sx={{
                        minHeight: { xs: 64, md: 68 },
                        px: { xs: 2, md: 5 },
                        gap: 3,
                    }}
                >
                    <IconButton
                        color="inherit"
                        aria-label={t('app.openNavigation')}
                        aria-controls="mobile-navigation"
                        aria-expanded={mobileNavigationOpen}
                        onClick={() => setMobileNavigationOpen(true)}
                        sx={{
                            display: { xs: 'inline-flex', md: 'none' },
                            ml: -1,
                        }}
                    >
                        <MenuIcon />
                    </IconButton>

                    <Box sx={{ flexGrow: 1, minWidth: 0, textAlign: 'left' }}>
                        <Typography
                            variant="h5"
                            component={NavLink}
                            to="/archive"
                            sx={{
                                color: 'text.primary',
                                fontWeight: 800,
                                lineHeight: 1,
                                textDecoration: 'none',
                            }}
                        >
                            EthnoWear
                        </Typography>

                        <Typography
                            variant="caption"
                            sx={{
                                display: 'block',
                                color: 'text.secondary',
                                mt: 0.5,
                                letterSpacing: 0.8,
                                textTransform: 'uppercase',
                            }}
                        >
                            {t('app.subtitle')}
                        </Typography>
                    </Box>

                    <Stack
                        component="nav"
                        direction="row"
                        spacing={1}
                        sx={{
                            display: { xs: 'none', md: 'flex' },
                            alignItems: 'center',
                        }}
                    >
                        {navItems.map((item) => (
                            <Button
                                key={item.path}
                                component={NavLink}
                                to={item.path}
                                color="inherit"
                                sx={{
                                    borderRadius: 0,
                                    px: 2,
                                    py: 2.25,
                                    color: 'text.primary',
                                    borderBottom: 3,
                                    borderColor: 'transparent',
                                    '&.active': {
                                        color: 'primary.main',
                                        borderColor: 'primary.main',
                                    },
                                }}
                            >
                                {t(item.translationKey)}
                            </Button>
                        ))}
                    </Stack>

                    <FormControl size="small" sx={{ minWidth: 82 }}>
                        <Select
                            value={language}
                            onChange={handleLanguageChange}
                            inputProps={{ 'aria-label': t('app.language') }}
                            sx={{
                                bgcolor: 'background.paper',
                                fontWeight: 700,
                                textTransform: 'uppercase',
                            }}
                        >
                            <MenuItem value="bg">BG</MenuItem>
                            <MenuItem value="en">EN</MenuItem>
                        </Select>
                    </FormControl>
                </Toolbar>
            </AppBar>

            <Drawer
                id="mobile-navigation"
                anchor="left"
                open={mobileNavigationOpen}
                onClose={() => setMobileNavigationOpen(false)}
                slotProps={{
                    paper: {
                        sx: {
                            width: 280,
                            maxWidth: '85vw',
                            bgcolor: 'background.paper',
                        },
                    },
                }}
            >
                <Box
                    sx={{
                        display: 'flex',
                        alignItems: 'flex-start',
                        justifyContent: 'space-between',
                        gap: 2,
                        px: 2.5,
                        py: 2.25,
                    }}
                >
                    <Box>
                        <Typography variant="h6" sx={{ fontWeight: 800 }}>
                            EthnoWear
                        </Typography>
                        <Typography
                            variant="caption"
                            color="text.secondary"
                            sx={{ textTransform: 'uppercase' }}
                        >
                            {t('app.subtitle')}
                        </Typography>
                    </Box>

                    <IconButton
                        aria-label={t('app.closeNavigation')}
                        onClick={() => setMobileNavigationOpen(false)}
                        size="small"
                    >
                        <CloseIcon />
                    </IconButton>
                </Box>

                <Divider />

                <List component="nav" aria-label={t('app.navigation')} sx={{ p: 1.5 }}>
                    {navItems.map((item) => {
                        const NavIcon = item.icon

                        return (
                            <ListItemButton
                                key={item.path}
                                component={NavLink}
                                to={item.path}
                                onClick={() => setMobileNavigationOpen(false)}
                                sx={{
                                    minHeight: 48,
                                    mb: 0.5,
                                    borderRadius: 1,
                                    borderLeft: 3,
                                    borderLeftColor: 'transparent',
                                    color: 'text.primary',
                                    '&.active': {
                                        bgcolor: '#F4E9EB',
                                        borderLeftColor: 'primary.main',
                                        color: 'primary.main',
                                        '& .MuiListItemIcon-root': {
                                            color: 'primary.main',
                                        },
                                    },
                                }}
                            >
                                <ListItemIcon sx={{ minWidth: 40, color: 'text.secondary' }}>
                                    <NavIcon fontSize="small" />
                                </ListItemIcon>
                                <ListItemText
                                    primary={t(item.translationKey)}
                                    slotProps={{
                                        primary: {
                                            sx: { fontWeight: 700 },
                                        },
                                    }}
                                />
                            </ListItemButton>
                        )
                    })}
                </List>
            </Drawer>

            <Box
                component="main"
                sx={{
                    minHeight: 'calc(100vh - 72px)',
                    backgroundColor: 'background.default',
                    backgroundImage: `
                        linear-gradient(rgba(90, 98, 90, 0.055) 1px, transparent 1px),
                        linear-gradient(90deg, rgba(90, 98, 90, 0.055) 1px, transparent 1px)
                    `,
                    backgroundSize: '16px 16px',
                }}
            >
                <Outlet />
            </Box>
        </Box>
    )
}

export default MainLayout

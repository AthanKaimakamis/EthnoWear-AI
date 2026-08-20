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
    Menu,
    Select,
    Stack,
    Toolbar,
    Typography,
} from '@mui/material'
import AccountCircleOutlinedIcon from '@mui/icons-material/AccountCircleOutlined'
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined'
import CloseIcon from '@mui/icons-material/Close'
import LoginOutlinedIcon from '@mui/icons-material/LoginOutlined'
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined'
import ManageAccountsOutlinedIcon from '@mui/icons-material/ManageAccountsOutlined'
import MenuIcon from '@mui/icons-material/Menu'
import type { SelectChangeEvent } from '@mui/material/Select'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAdminAuth } from '../../app/adminAuth'

type NavItem = {
    translationKey: 'nav.archive' | 'nav.management'
    path: string
    icon: typeof ArchiveOutlinedIcon
}

function MainLayout() {
    const { t, i18n } = useTranslation()
    const language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
    const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false)
    const [accountAnchor, setAccountAnchor] = useState<HTMLElement | null>(null)
    const { admin, authenticated, logout } = useAdminAuth()
    const navigate = useNavigate()
    const navItems: NavItem[] = [
        { translationKey: 'nav.archive', path: '/archive', icon: ArchiveOutlinedIcon },
        ...(authenticated && !admin?.passwordChangeRequired
            ? [{ translationKey: 'nav.management' as const, path: '/admin', icon: ManageAccountsOutlinedIcon }]
            : []),
    ]

    function handleLanguageChange(event: SelectChangeEvent) {
        void i18n.changeLanguage(event.target.value)
    }

    function handleLogout() {
        setAccountAnchor(null)
        logout()
        navigate('/archive')
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

                    <Box
                        component={NavLink}
                        to="/archive"
                        aria-label="EthnoWear"
                        sx={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 1.25,
                            flexGrow: 1,
                            minWidth: 0,
                            color: 'text.primary',
                            textAlign: 'left',
                            textDecoration: 'none',
                        }}
                    >
                        <Box
                            component="img"
                            src="/logo_v3.png"
                            alt=""
                            aria-hidden="true"
                            sx={{
                                width: { xs: 40, md: 48 },
                                height: { xs: 40, md: 48 },
                                flexShrink: 0,
                                objectFit: 'contain',
                            }}
                        />
                        <Box sx={{ minWidth: 0 }}>
                            <Typography
                                variant="h5"
                                component="span"
                                sx={{ display: 'block', fontWeight: 800, lineHeight: 1 }}
                            >
                                EthnoWear
                            </Typography>
                            <Typography
                                variant="caption"
                                component="span"
                                sx={{
                                    display: { xs: 'none', sm: 'block' },
                                    color: 'text.secondary',
                                    mt: 0.5,
                                    letterSpacing: 0.8,
                                    textTransform: 'uppercase',
                                }}
                            >
                                {t('app.subtitle')}
                            </Typography>
                        </Box>
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

                    {authenticated && admin ? (
                        <>
                            <Button
                                color="inherit"
                                startIcon={<AccountCircleOutlinedIcon />}
                                onClick={event => setAccountAnchor(event.currentTarget)}
                                aria-haspopup="menu"
                                aria-expanded={Boolean(accountAnchor)}
                                sx={{ display: { xs: 'none', sm: 'inline-flex' }, fontWeight: 700 }}
                            >
                                {admin.username}
                            </Button>
                            <IconButton
                                color="inherit"
                                onClick={event => setAccountAnchor(event.currentTarget)}
                                aria-label={t('auth.account.open')}
                                sx={{ display: { xs: 'inline-flex', sm: 'none' } }}
                            >
                                <AccountCircleOutlinedIcon />
                            </IconButton>
                            <Menu anchorEl={accountAnchor} open={Boolean(accountAnchor)} onClose={() => setAccountAnchor(null)}
                                slotProps={{ paper: { sx: { width: 300, mt: 1 } } }}>
                                <Box sx={{ px: 2, py: 1.25 }}>
                                    <Typography sx={{ fontWeight: 800 }}>{[admin.firstName, admin.lastName].filter(Boolean).join(' ') || admin.username}</Typography>
                                    <Typography variant="body2" color="text.secondary">{admin.email || admin.username}</Typography>
                                    <Typography variant="caption" color="text.secondary">
                                        {admin.roles.map(role => t(`auth.roles.${role}`)).join(', ')}
                                    </Typography>
                                </Box>
                                <Divider />
                                {admin.passwordChangeRequired && (
                                    <MenuItem component={NavLink} to="/account/password" onClick={() => setAccountAnchor(null)}>
                                        <ListItemIcon><ManageAccountsOutlinedIcon fontSize="small" /></ListItemIcon>
                                        <ListItemText>{t('auth.passwordChange.title')}</ListItemText>
                                    </MenuItem>
                                )}
                                <MenuItem onClick={handleLogout}>
                                    <ListItemIcon><LogoutOutlinedIcon fontSize="small" /></ListItemIcon>
                                    <ListItemText>{t('admin.session.signOut')}</ListItemText>
                                </MenuItem>
                            </Menu>
                        </>
                    ) : (
                        <Button component={NavLink} to="/admin/login" color="inherit" startIcon={<LoginOutlinedIcon />} sx={{ fontWeight: 700 }}>
                            {t('nav.login')}
                        </Button>
                    )}
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
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, minWidth: 0 }}>
                        <Box
                            component="img"
                            src="/logo_v3.png"
                            alt=""
                            aria-hidden="true"
                            sx={{ width: 48, height: 48, flexShrink: 0, objectFit: 'contain' }}
                        />
                        <Box sx={{ minWidth: 0 }}>
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
                <Divider />
                <List sx={{ p: 1.5 }}>
                    {authenticated && admin ? (
                        <ListItemButton onClick={handleLogout} sx={{ borderRadius: 1 }}>
                            <ListItemIcon><LogoutOutlinedIcon /></ListItemIcon>
                            <ListItemText primary={admin.username} secondary={t('admin.session.signOut')} />
                        </ListItemButton>
                    ) : (
                        <ListItemButton component={NavLink} to="/admin/login" onClick={() => setMobileNavigationOpen(false)} sx={{ borderRadius: 1 }}>
                            <ListItemIcon><LoginOutlinedIcon /></ListItemIcon>
                            <ListItemText primary={t('nav.login')} />
                        </ListItemButton>
                    )}
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

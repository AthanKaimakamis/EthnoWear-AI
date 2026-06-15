import { Box, Button, Stack } from '@mui/material'
import AutoAwesomeMosaicOutlinedIcon from '@mui/icons-material/AutoAwesomeMosaicOutlined'
import DesignServicesOutlinedIcon from '@mui/icons-material/DesignServicesOutlined'
import HubOutlinedIcon from '@mui/icons-material/HubOutlined'
import TextureOutlinedIcon from '@mui/icons-material/TextureOutlined'
import { NavLink, Outlet } from 'react-router'
import { useTranslation } from 'react-i18next'

const archiveNavItems = [
    {
        path: '/archive/embroideries',
        translationKey: 'archiveNav.embroideries',
        icon: TextureOutlinedIcon,
    },
    {
        path: '/archive/motifs',
        translationKey: 'archiveNav.motifs',
        icon: HubOutlinedIcon,
    },
    {
        path: '/archive/techniques',
        translationKey: 'archiveNav.techniques',
        icon: DesignServicesOutlinedIcon,
    },
    {
        path: '/archive/ornaments',
        translationKey: 'archiveNav.ornaments',
        icon: AutoAwesomeMosaicOutlinedIcon,
    },
] as const

function ArchiveLayout() {
    const { t } = useTranslation()

    return (
        <Box>
            <Box
                component="nav"
                aria-label={t('archiveNav.label')}
                sx={{
                    position: 'sticky',
                    top: { xs: 64, md: 68 },
                    zIndex: 1000,
                    bgcolor: 'background.paper',
                    borderBottom: 1,
                    borderColor: 'divider',
                    boxShadow: '0 2px 6px rgba(24, 28, 24, 0.08)',
                    px: { xs: 1.5, md: 5 },
                    overflowX: 'auto',
                }}
            >
                <Stack direction="row" spacing={0.5} sx={{ minWidth: 'max-content' }}>
                    {archiveNavItems.map((item) => {
                        const Icon = item.icon

                        return (
                            <Button
                                key={item.path}
                                component={NavLink}
                                to={item.path}
                                color="inherit"
                                startIcon={<Icon fontSize="small" />}
                                sx={{
                                    minHeight: 52,
                                    borderRadius: 0,
                                    px: { xs: 1.25, sm: 2 },
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
                        )
                    })}
                </Stack>
            </Box>

            <Outlet />
        </Box>
    )
}

export default ArchiveLayout

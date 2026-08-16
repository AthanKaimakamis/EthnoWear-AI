import { Box, Button, Stack } from '@mui/material'
import AutoAwesomeMosaicOutlinedIcon from '@mui/icons-material/AutoAwesomeMosaicOutlined'
import DesignServicesOutlinedIcon from '@mui/icons-material/DesignServicesOutlined'
import HubOutlinedIcon from '@mui/icons-material/HubOutlined'
import TextureOutlinedIcon from '@mui/icons-material/TextureOutlined'
import { NavLink, Outlet } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { catalogueQueryOptions, regionalEmbroideryArchiveQueryOptions } from '../../api/PublicQueryOptions'
import type { Language } from '../../types/reference'
import type { OntologyFeatureType } from '../../types/catalogue'

const archiveNavItems = [
    {
        path: '/archive/embroideries',
        kind: 'embroideries',
        translationKey: 'archiveNav.embroideries',
        icon: TextureOutlinedIcon,
    },
    {
        path: '/archive/motifs',
        kind: 'MOTIF',
        translationKey: 'archiveNav.motifs',
        icon: HubOutlinedIcon,
    },
    {
        path: '/archive/techniques',
        kind: 'TECHNIQUE',
        translationKey: 'archiveNav.techniques',
        icon: DesignServicesOutlinedIcon,
    },
    {
        path: '/archive/ornaments',
        kind: 'ORNAMENT',
        translationKey: 'archiveNav.ornaments',
        icon: AutoAwesomeMosaicOutlinedIcon,
    },
] as const

function ArchiveLayout() {
    const { t, i18n } = useTranslation()
    const queryClient = useQueryClient()
    const language: Language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'

    function prefetch(kind: 'embroideries' | OntologyFeatureType) {
        if (kind === 'embroideries') {
            void queryClient.prefetchQuery(regionalEmbroideryArchiveQueryOptions(language, 4))
            void queryClient.prefetchQuery(catalogueQueryOptions({
                entityType: 'REGIONAL_EMBROIDERY',
                language,
                relatedEntityLocalNames: { REGION: [], ORNAMENT: [], TECHNIQUE: [] },
                relatedCategoryLocalNames: { REGION: [], ORNAMENT: [] },
                combinationMode: 'AND',
            }, { page: 0, size: 500, sort: 'label,asc' }))
            return
        }

        void queryClient.prefetchQuery(catalogueQueryOptions({
            entityType: kind,
            language,
            searchText: '',
            categoryLocalNames: [],
            relatedEntityLocalNames: { REGION: [] },
            combinationMode: 'AND',
        }, { page: 0, size: 200, sort: 'label,asc' }))
    }

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
                                onMouseEnter={() => prefetch(item.kind)}
                                onFocus={() => prefetch(item.kind)}
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

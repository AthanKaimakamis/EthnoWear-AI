import { Accordion, AccordionDetails, AccordionSummary, Box, Chip, Stack, Tooltip, Typography } from '@mui/material'
import ExpandMoreOutlinedIcon from '@mui/icons-material/ExpandMoreOutlined'
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'
import { useTranslation } from 'react-i18next'
import { apiEnumLabel } from '../../../app/apiEnumLabels'
import type { RightsStatus } from '../../../types/archive'

const statuses: RightsStatus[] = ['UNKNOWN', 'PUBLIC_DOMAIN', 'LICENSED', 'RESTRICTED']

export default function RightsGuidance() {
    const { t } = useTranslation()

    return <Accordion disableGutters elevation={0} sx={{ border: '1px solid', borderColor: 'divider', '&:before': { display: 'none' } }}>
        <AccordionSummary expandIcon={<ExpandMoreOutlinedIcon />}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <Tooltip title={t('admin.archive.rights.helpTooltip')}><InfoOutlinedIcon color="info" fontSize="small" /></Tooltip>
                <Typography sx={{ fontWeight: 700 }}>{t('admin.archive.rights.helpTitle')}</Typography>
            </Stack>
        </AccordionSummary>
        <AccordionDetails>
            <Stack spacing={1.5}>
                {statuses.map(status => <Box key={status} sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '150px 1fr' }, gap: 1, alignItems: 'start' }}>
                    <Chip size="small" variant="outlined" label={apiEnumLabel(t, 'rightsStatus', status)} sx={{ justifySelf: 'start' }} />
                    <Typography variant="body2" color="text.secondary">{t(`admin.archive.rights.statusHelp.${status}`)}</Typography>
                </Box>)}
                <Typography variant="body2" sx={{ pt: 1, borderTop: '1px solid', borderColor: 'divider', fontWeight: 600 }}>
                    {t('admin.archive.rights.effectiveVisibilityHelp')}
                </Typography>
            </Stack>
        </AccordionDetails>
    </Accordion>
}

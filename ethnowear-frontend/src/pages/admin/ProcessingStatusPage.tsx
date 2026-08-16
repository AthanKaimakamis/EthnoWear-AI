import { Alert, Box, Button, Paper, Stack, Typography } from '@mui/material'
import AutorenewOutlinedIcon from '@mui/icons-material/AutorenewOutlined'
import TaskAltOutlinedIcon from '@mui/icons-material/TaskAltOutlined'
import AdminPageHeader from '../../components/admin/AdminPageHeader'
import { useTranslation } from 'react-i18next'

export default function ProcessingStatusPage() {
    const { t } = useTranslation()
    return <Stack spacing={3}><AdminPageHeader title={t('curator.processing.title')} description={t('curator.processing.description')} /><Alert severity="info">{t('curator.processing.apiGap')}</Alert><Paper variant="outlined" sx={{ p: 5 }}><Stack spacing={2} sx={{ alignItems: 'center', textAlign: 'center' }}><TaskAltOutlinedIcon color="success" sx={{ fontSize: 52 }} /><Box><Typography variant="h6">{t('curator.processing.noJobs')}</Typography><Typography color="text.secondary">{t('curator.processing.noJobsDescription')}</Typography></Box><Button disabled startIcon={<AutorenewOutlinedIcon />}>{t('curator.processing.retry')}</Button></Stack></Paper></Stack>
}

import { Chip } from '@mui/material'
import type { PublicationStatus } from '../../types/archive'
import { useTranslation } from 'react-i18next'

export default function ArchiveStatusChip({ value }: { value: PublicationStatus }) {
    const { t } = useTranslation()
    const color = value === 'PUBLISHED'
        ? 'success'
        : value === 'IN_REVIEW'
            ? 'warning'
            : value === 'ARCHIVED'
                ? 'default'
                : 'info'

    return (
        <Chip
            size="small"
            color={color}
            variant={value === 'ARCHIVED' ? 'outlined' : 'filled'}
            label={t(`publication.status.${value}`)}
        />
    )
}

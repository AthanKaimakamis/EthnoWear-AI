import { Chip } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { TrustedLevel } from '../../types/archive'

export default function TrustedLevelChip({ value }: { value: TrustedLevel }) {
    const { t } = useTranslation()
    const color = value === 'VERIFIED' ? 'success' : value === 'LIKELY' ? 'warning' : 'default'

    return (
        <Chip
            size="small"
            color={color}
            variant={value === 'UNVERIFIED' ? 'outlined' : 'filled'}
            label={t(`archiveDetails.trust.${value}`)}
        />
    )
}

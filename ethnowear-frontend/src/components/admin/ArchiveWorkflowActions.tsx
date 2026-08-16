import { Button, CircularProgress, Stack } from '@mui/material'
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined'
import AssignmentTurnedInOutlinedIcon from '@mui/icons-material/AssignmentTurnedInOutlined'
import EditNoteOutlinedIcon from '@mui/icons-material/EditNoteOutlined'
import PublishOutlinedIcon from '@mui/icons-material/PublishOutlined'
import { useTranslation } from 'react-i18next'
import type { PublicationCommand } from '../../api/ArchiveAdminApi'
import type { PublicationReadinessDetails, PublicationStatus } from '../../types/archive'
import {
    adminWorkflowPermissions,
    canRunCommand,
    type ArchiveWorkflowPermissions,
} from './archiveWorkflow'

type Props = {
    status: PublicationStatus
    readiness?: PublicationReadinessDetails | null
    requireReadiness?: boolean
    pendingCommand?: PublicationCommand | null
    permissions?: ArchiveWorkflowPermissions
    disabled?: boolean
    onCommand: (command: PublicationCommand) => void
}

export default function ArchiveWorkflowActions({
    status,
    readiness,
    requireReadiness = false,
    pendingCommand = null,
    permissions = adminWorkflowPermissions,
    disabled = false,
    onCommand,
}: Props) {
    const { t } = useTranslation()
    const busy = pendingCommand !== null
    const readinessBlocks = requireReadiness && (!readiness || !readiness.ready)

    const button = (command: PublicationCommand, icon: React.ReactNode) => {
        if (!canRunCommand(command, permissions)) return null
        const needsReadiness = command === 'submit' || command === 'publish'
        return (
            <Button
                key={command}
                variant={command === 'publish' ? 'contained' : 'outlined'}
                startIcon={pendingCommand === command ? <CircularProgress size={16} /> : icon}
                disabled={disabled || busy || (needsReadiness && readinessBlocks)}
                onClick={() => onCommand(command)}
            >
                {t(`publication.actions.${command}`)}
            </Button>
        )
    }

    return (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            {status === 'DRAFT' && button('submit', <AssignmentTurnedInOutlinedIcon />)}
            {status === 'IN_REVIEW' && (
                <>
                    {button('return-to-draft', <EditNoteOutlinedIcon />)}
                    {button('publish', <PublishOutlinedIcon />)}
                </>
            )}
            {status === 'PUBLISHED' && button('archive', <ArchiveOutlinedIcon />)}
        </Stack>
    )
}

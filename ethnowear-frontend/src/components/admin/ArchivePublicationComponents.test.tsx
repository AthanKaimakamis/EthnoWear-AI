import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import ArchiveStatusChip from './ArchiveStatusChip'
import ArchiveWorkflowActions from './ArchiveWorkflowActions'
import PublicationReadinessPanel from './PublicationReadinessPanel'
import { publicationErrorMessages, workflowPermissionsForRoles } from './archiveWorkflow'
import { ApiError } from '../../api/http'
import i18n from '../../app/i18n'
import { renderApp } from '../../test/render'
import type { PublicationReadinessDetails } from '../../types/archive'

const blockedReadiness: PublicationReadinessDetails = {
    archiveItemId: 7,
    publicationStatus: 'DRAFT',
    ready: false,
    requirements: [{
        key: 'MEDIA_ATTACHED',
        satisfied: false,
        severity: 'ERROR',
        message: 'Add a primary image.',
        field: 'media',
    }],
}

const readyReadiness: PublicationReadinessDetails = {
    ...blockedReadiness,
    ready: true,
    requirements: blockedReadiness.requirements.map(requirement => ({
        ...requirement,
        satisfied: true,
    })),
}

describe('archive publication components', () => {
    it.each([
        ['DRAFT', 'Draft'],
        ['IN_REVIEW', 'In review'],
        ['PUBLISHED', 'Published'],
        ['ARCHIVED', 'Archived'],
    ] as const)('renders %s independently from evidence trust', (status, label) => {
        renderApp(<ArchiveStatusChip value={status} />)
        expect(screen.getByText(label)).toBeVisible()
    })

    it('renders backend readiness messages and opens the relevant section', () => {
        const onOpen = vi.fn()
        renderApp(
            <PublicationReadinessPanel
                readiness={blockedReadiness}
                loading={false}
                onOpenRequirement={onOpen}
            />,
        )

        expect(screen.getByText('Add a primary image.')).toBeVisible()
        fireEvent.click(screen.getByRole('button', { name: 'Open section' }))
        expect(onOpen).toHaveBeenCalledWith(blockedReadiness.requirements[0])
    })

    it('disables submit when backend readiness has blockers', () => {
        renderApp(
            <ArchiveWorkflowActions
                status="DRAFT"
                readiness={blockedReadiness}
                requireReadiness
                onCommand={vi.fn()}
            />,
        )
        expect(screen.getByRole('button', { name: 'Submit for review' })).toBeDisabled()
    })

    it('enables the valid review transitions when readiness and permissions allow them', () => {
        const onCommand = vi.fn()
        renderApp(
            <ArchiveWorkflowActions
                status="IN_REVIEW"
                readiness={{ ...readyReadiness, publicationStatus: 'IN_REVIEW' }}
                requireReadiness
                onCommand={onCommand}
            />,
        )

        fireEvent.click(screen.getByRole('button', { name: 'Publish' }))
        expect(onCommand).toHaveBeenCalledWith('publish')
        expect(screen.getByRole('button', { name: 'Return to draft' })).toBeEnabled()
    })

    it('hides publish when the current role capability does not allow it', () => {
        renderApp(
            <ArchiveWorkflowActions
                status="IN_REVIEW"
                readiness={{ ...readyReadiness, publicationStatus: 'IN_REVIEW' }}
                requireReadiness
                permissions={{ edit: true, submit: true, publish: false, returnToDraft: true, archive: false }}
                onCommand={vi.fn()}
            />,
        )
        expect(screen.queryByRole('button', { name: 'Publish' })).not.toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Return to draft' })).toBeVisible()
    })

    it('limits editors to editing and submitting while reviewers retain review actions', () => {
        expect(workflowPermissionsForRoles(['EDITOR'])).toEqual({
            edit: true, submit: true, publish: false, returnToDraft: false, archive: false,
        })
        expect(workflowPermissionsForRoles(['REVIEWER']).publish).toBe(true)
        expect(workflowPermissionsForRoles(['ADMINISTRATOR']).archive).toBe(true)
    })

    it('turns backend failed requirements into actionable localized messages', () => {
        const error = new ApiError('Request failed with status 409', 409, {
            message: 'Archive item is not ready for publication',
            failedRequirements: ['LOCALIZED_TITLE', 'SOURCE_REFERENCE'],
        })

        expect(publicationErrorMessages(error, 'Publication failed', i18n.t.bind(i18n))).toEqual([
            'Add a title in at least one supported language.',
            'Link an exact source and citation.',
        ])
    })
})

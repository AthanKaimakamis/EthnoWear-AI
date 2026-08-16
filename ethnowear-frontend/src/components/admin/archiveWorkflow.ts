import type { PublicationReadinessRequirement, PublicationStatus } from '../../types/archive'
import type { PublicationCommand } from '../../api/ArchiveAdminApi'
import { ApiError, apiErrorMessages } from '../../api/http'
import type { TFunction } from 'i18next'

export type ArchiveWorkflowPermissions = {
    edit: boolean
    submit: boolean
    publish: boolean
    returnToDraft: boolean
    archive: boolean
}

export const adminWorkflowPermissions: ArchiveWorkflowPermissions = {
    edit: true,
    submit: true,
    publish: true,
    returnToDraft: true,
    archive: true,
}

export function commandForStatus(status: PublicationStatus): PublicationCommand | null {
    if (status === 'DRAFT') return 'submit'
    if (status === 'IN_REVIEW') return 'publish'
    if (status === 'PUBLISHED') return 'archive'
    return null
}

export function canRunCommand(
    command: PublicationCommand,
    permissions: ArchiveWorkflowPermissions,
) {
    if (command === 'submit') return permissions.submit
    if (command === 'publish') return permissions.publish
    if (command === 'return-to-draft') return permissions.returnToDraft
    return permissions.archive
}

export function publicationErrorMessages(error: unknown, fallback: string, t: TFunction) {
    if (error instanceof ApiError) {
        if (error.status === 401) return [t('publication.errors.unauthorized')]
        if (error.status === 403) return [t('publication.errors.forbidden')]
        if (error.status === 404) return [t('publication.errors.notFound')]
        if (error.status === 409) {
            const failedRequirements = publicationFailedRequirements(error.details)
            if (failedRequirements.length > 0) {
                return failedRequirements.map(requirement =>
                    t(`publication.requirementMessages.${requirement}`, requirement),
                )
            }
            const details = apiErrorMessages(error, t('publication.errors.conflict'))
            return details[0] === error.message ? [t('publication.errors.conflict')] : details
        }
    }
    return apiErrorMessages(error, fallback)
}

function publicationFailedRequirements(details: unknown) {
    if (!details || typeof details !== 'object' || !('failedRequirements' in details)) return []
    const requirements = (details as { failedRequirements?: unknown }).failedRequirements
    return Array.isArray(requirements)
        ? requirements.filter((requirement): requirement is string => typeof requirement === 'string')
        : []
}

export function tabForRequirement(requirement: PublicationReadinessRequirement) {
    const key = requirement.key.toLocaleUpperCase()
    const field = requirement.field?.toLocaleLowerCase() ?? ''
    if (key.includes('TITLE') || field.includes('title')) return 0
    if (key.includes('SOURCE') || field.includes('source')) return 3
    if (key.includes('MEDIA') || field.includes('media')) return 2
    if (key.includes('DESCRIPTION') || field.includes('description')) return 4
    if (key.includes('CLASSIFICATION') || key.includes('FEATURE') || field.includes('ontology')) return 1
    return 5
}

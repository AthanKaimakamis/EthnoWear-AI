import i18n from '../app/i18n'
import { backendEnglishMessages } from '../app/apiErrorTranslations'

export type ApiErrorDetails = {
    code?: unknown
    message?: unknown
    fields?: unknown
    errors?: unknown
    references?: unknown
    failedRequirements?: unknown
    blockers?: unknown
}

const exactMessageKeys = new Map<string, string>(
    Object.entries(backendEnglishMessages).map(([key, message]) => [message, key]),
)

const codeKeys: Record<string, string> = {
    USER_USERNAME_EXISTS: 'usernameExists',
    USER_EMAIL_EXISTS: 'emailExists',
    USER_SELF_DELETE_FORBIDDEN: 'userSelfDeleteForbidden',
    FINAL_ADMINISTRATOR_DELETE_FORBIDDEN: 'finalAdministratorDeleteForbidden',
    AUTH_INVALID_CREDENTIALS: 'invalidCredentials',
    AUTH_CURRENT_PASSWORD_INVALID: 'currentPasswordInvalid',
    MEDIA_FILE_TOO_LARGE: 'mediaTooLarge',
    PROCESSING_JOB_NOT_FOUND: 'processingJobNotFound',
    PROCESSING_JOB_ACTIVE_CONFLICT: 'processingJobActiveConflict',
    PROCESSING_JOB_INVALID_TRANSITION: 'processingJobInvalidTransition',
    VISION_JOB_ALREADY_ACTIVE: 'visionJobAlreadyActive',
    DOCUMENT_DEPENDENCY_CONFLICT: 'documentDependencyConflict',
    DOCUMENT_VECTOR_CLEANUP_UNAVAILABLE: 'documentVectorCleanupUnavailable',
    CHUNK_GENERATION_INELIGIBLE: 'chunkGenerationIneligible',
    ONTOLOGY_VERSION_NOT_FOUND: 'ontologyVersionNotFound',
    ONTOLOGY_VERSION_RESTORE_FAILED: 'ontologyVersionRestoreFailed',
}

type DynamicMatch = {
    pattern: RegExp
    key: string
    values: string[]
    enumValues?: string[]
}

const dynamicMatches: DynamicMatch[] = [
    { pattern: /^User not found: (.+)$/, key: 'userNotFound', values: ['id'] },
    { pattern: /^User information not found: (.+)$/, key: 'userInformationNotFound', values: ['id'] },
    { pattern: /^Archive item (.+) cannot be modified while its status is (.+)$/, key: 'archiveNotEditable', values: ['id', 'status'], enumValues: ['status'] },
    { pattern: /^Archive item (.+) cannot transition from (.+) to (.+)$/, key: 'archiveTransition', values: ['id', 'current', 'target'], enumValues: ['current', 'target'] },
    { pattern: /^Archive item is not ready for publication: (.+)$/, key: 'archiveNotReady', values: ['id'] },
    { pattern: /^Media asset not found: (.+)$/, key: 'mediaAssetNotFound', values: ['id'] },
    { pattern: /^Media content not found: (.+)$/, key: 'mediaContentNotFound', values: ['id'] },
    { pattern: /^Media file not found: (.+)$/, key: 'mediaFileNotFound', values: ['id'] },
    { pattern: /^Unsupported entity type: (.+)$/, key: 'unsupportedEntityType', values: ['value'], enumValues: ['value'] },
    { pattern: /^Invalid ontology local name: (.+)$/, key: 'invalidOntologyName', values: ['value'] },
    { pattern: /^Ornament type does not exist: (.+)$/, key: 'ornamentTypeMissing', values: ['value'] },
    { pattern: /^Technique type does not exist: (.+)$/, key: 'techniqueTypeMissing', values: ['value'] },
    { pattern: /^Ornament already exists: (.+)$/, key: 'ornamentExists', values: ['value'] },
    { pattern: /^Technique already exists: (.+)$/, key: 'techniqueExists', values: ['value'] },
    { pattern: /^Ornament not found: (.+)$/, key: 'ornamentNotFound', values: ['value'] },
    { pattern: /^Technique not found: (.+)$/, key: 'techniqueNotFound', values: ['value'] },
    { pattern: /^Ornament is referenced by other ontology resources: (.+)$/, key: 'ornamentInUse', values: ['value'] },
    { pattern: /^Technique is referenced by other ontology resources: (.+)$/, key: 'techniqueInUse', values: ['value'] },
    { pattern: /^(.+) ontology entity not found: (.+)$/, key: 'ontologyEntityNotFound', values: ['entity', 'value'], enumValues: ['entity'] },
    { pattern: /^Document not found: (.+)$/, key: 'documentNotFound', values: ['id'] },
    { pattern: /^Document page not found: (.+)$/, key: 'documentPageNotFound', values: ['id'] },
    { pattern: /^Document processing job not found: (.+)$/, key: 'jobNotFound', values: ['id'] },
    { pattern: /^Unsupported PDF document type: (.+)$/, key: 'unsupportedDocumentType', values: ['value'], enumValues: ['value'] },
    { pattern: /^Document page sequence already exists: (.+)$/, key: 'pageSequenceExists', values: ['value'] },
    { pattern: /^An active (.+) job already exists$/, key: 'activeJobExists', values: ['value'], enumValues: ['value'] },
    { pattern: /^The processing job cannot be cancelled from state (.+)$/, key: 'jobCancellationState', values: ['value'], enumValues: ['value'] },
    { pattern: /^(.+) not found: (.+)$/, key: 'resourceNotFound', values: ['resource', 'id'], enumValues: ['resource'] },
    { pattern: /^(.+) is referenced and cannot be deleted: (.+)$/, key: 'resourceInUse', values: ['resource', 'id'], enumValues: ['resource'] },
]

const statusFallbacks: Record<number, string> = {
    0: 'network', 400: 'invalidRequest', 401: 'authenticationRequired', 403: 'permissionDenied',
    404: 'notFound', 409: 'conflict', 413: 'fileTooLarge', 422: 'unprocessableContent',
}

export function localizedErrorMessages(
    status: number,
    details: unknown,
    rawMessage: string,
    fallback?: string,
) {
    const body = asDetails(details)
    const messages: string[] = []
    const backendMessage = typeof body?.message === 'string' ? body.message : rawMessage
    const localizedMessage = localizeBackendMessage(backendMessage, body?.code)

    if (localizedMessage) messages.push(localizedMessage)
    if (body?.fields && typeof body.fields === 'object') {
        Object.entries(body.fields as Record<string, unknown>).forEach(([field, constraint]) => {
            messages.push(localizeFieldError(field, String(constraint)))
        })
    }
    if (Array.isArray(body?.errors)) {
        body.errors.forEach(item => {
            const message = typeof item === 'string'
                ? item
                : item && typeof item === 'object' && 'message' in item ? String(item.message) : ''
            const localized = localizeBackendMessage(message)
            if (localized) messages.push(localized)
        })
    }
    if (Array.isArray(body?.failedRequirements)) {
        body.failedRequirements.forEach(requirement => {
            if (typeof requirement !== 'string') return
            const key = `publication.requirementMessages.${requirement}`
            messages.push(i18n.exists(key) ? tr(key) : humanize(requirement))
        })
    }
    if (Array.isArray(body?.blockers)) {
        body.blockers.forEach(blocker => {
            if (!blocker || typeof blocker !== 'object') return
            const code = 'code' in blocker ? String(blocker.code) : 'UNKNOWN'
            const key = `documents.chunks.blockers.${code}`
            messages.push(i18n.exists(key)
                ? tr(key)
                : tr('documents.chunks.blockers.UNKNOWN'))
        })
    }
    if (Array.isArray(body?.references) && body.references.length > 0) {
        messages.push(tr('errors.referencesTitle'))
        body.references.forEach(reference => messages.push(localizeReference(reference)))
    }

    if (messages.length === 0) {
        const statusKey = status >= 500 ? 'serverError' : statusFallbacks[status]
        if (statusKey) messages.push(tr(`errors.${statusKey}`))
        else if (backendMessage) messages.push(backendMessage)
        else if (fallback) messages.push(fallback)
    }

    return [...new Set(messages)]
}

export function localizedNonApiError(error: unknown, fallback: string) {
    if (error instanceof TypeError || (error instanceof Error && /network|failed to fetch/i.test(error.message))) {
        return tr('errors.network')
    }
    return error instanceof Error && error.message ? error.message : fallback
}

function localizeBackendMessage(message: string, code?: unknown) {
    if (typeof code === 'string' && codeKeys[code]) return tr(`errors.messages.${codeKeys[code]}`)
    if (!message) return null
    if (message === 'Validation failed') return tr('errors.validationFailed')

    const exactKey = exactMessageKeys.get(message)
    if (exactKey) return tr(`errors.messages.${exactKey}`)

    for (const matcher of dynamicMatches) {
        const match = message.match(matcher.pattern)
        if (!match) continue
        const options: Record<string, string> = {}
        matcher.values.forEach((name, index) => {
            const value = match[index + 1]
            options[name] = matcher.enumValues?.includes(name) ? localizeDynamicValue(value) : value
        })
        return tr(`errors.messages.${matcher.key}`, options)
    }

    if (isUnsafeStorageMessage(message)) return tr('errors.unsafeStorage')
    return null
}

function localizeFieldError(fieldPath: string, constraint: string) {
    const field = fieldPath.split(/[.[\]]/).filter(Boolean).at(-1) ?? fieldPath
    const fieldKey = `errors.fields.${field}`
    const label = i18n.exists(fieldKey) ? tr(fieldKey) : humanize(field)
    const localizedMessage = localizeBackendMessage(constraint)
    if (localizedMessage) return `${label}: ${localizedMessage}`

    const constraintKey = fieldConstraintKey(constraint)
    return `${label} ${tr(`errors.constraints.${constraintKey}`)}.`
}

function fieldConstraintKey(message: string) {
    if (/must not be (null|blank|empty)|must be selected|is required|required/i.test(message)) return 'required'
    if (/email/i.test(message)) return 'email'
    if (/too short|minimum|min\b|greater than or equal/i.test(message)) return 'tooShort'
    if (/too long|maximum|max\b|less than or equal/i.test(message)) return 'tooLong'
    if (/size must be between|length/i.test(message)) return 'length'
    if (/positive|greater than 0/i.test(message)) return 'positive'
    if (/unsupported|must be one of/i.test(message)) return 'unsupported'
    return 'invalid'
}

function localizeReference(reference: unknown) {
    if (!reference || typeof reference !== 'object') return tr('errors.reference', { subject: tr('errors.notFound'), property: '' })
    const value = reference as Record<string, unknown>
    const subject = humanize(String(value.subjectLocalName ?? value.subject ?? ''))
    const property = localizeDynamicValue(String(value.propertyLocalName ?? value.property ?? ''))
    return tr('errors.reference', { subject, property })
}

function localizeDynamicValue(value: string) {
    const normalized = value.trim().replace(/^ROLE_/, '')
    const candidates = [
        `errors.values.${normalized}`,
        `auth.roles.${normalized}`,
        `publication.status.${normalized}`,
        `documents.status.job.${normalized}`,
        `documents.status.processing.${normalized}`,
        `entityTypes.${normalized}`,
    ]
    const key = candidates.find(candidate => i18n.exists(candidate))
    return key ? tr(key) : humanize(normalized)
}

function isUnsafeStorageMessage(message: string) {
    return /(storage root|storage path|filesystem|file system|path traversal|outside.*storage|cannot store|could not store|failed to store|create director|write.*file)/i.test(message)
}

function humanize(value: string) {
    return value
        .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
        .replace(/[_-]+/g, ' ')
        .trim()
        .replace(/^./, character => character.toLocaleUpperCase())
}

function asDetails(details: unknown) {
    return details && typeof details === 'object' ? details as ApiErrorDetails : null
}

function tr(key: string, options?: Record<string, unknown>) {
    return String(i18n.t(key, options))
}

import { afterEach, describe, expect, it } from 'vitest'
import i18n from '../app/i18n'
import { ApiError, apiErrorMessage, apiErrorMessages } from './http'

describe('API error localization', () => {
    afterEach(async () => i18n.changeLanguage('en'))

    it('localizes known backend messages and dynamic enum values', async () => {
        await i18n.changeLanguage('bg')

        expect(apiErrorMessage(new ApiError('Username already exists', 409, {
            message: 'Username already exists',
        }))).toBe('Потребителското име вече се използва.')

        expect(apiErrorMessage(new ApiError('transition', 409, {
            message: 'Archive item 12 cannot transition from IN_REVIEW to PUBLISHED',
        }))).toBe('Архивен запис 12 не може да премине от „Очаква одобрение“ към „Публикуван“.')
    })

    it('localizes validation fields and common constraints', async () => {
        await i18n.changeLanguage('bg')

        expect(apiErrorMessages(new ApiError('Validation failed', 400, {
            message: 'Validation failed',
            fields: { username: 'must not be blank', email: 'must be a well-formed email address' },
        }))).toEqual([
            'Проверете отбелязаните полета.',
            'Потребителско име е задължително.',
            'Имейл трябва да съдържа валиден имейл адрес.',
        ])
        expect(apiErrorMessage(new ApiError('Validation failed', 400, {
            message: 'Validation failed',
            fields: { username: 'must not be blank', email: 'must be a well-formed email address' },
        }))).toContain('Имейл трябва да съдържа валиден имейл адрес.')
    })

    it('renders ontology references without exposing the raw response objects', async () => {
        await i18n.changeLanguage('bg')

        expect(apiErrorMessages(new ApiError('in use', 409, {
            message: 'Technique is referenced by other ontology resources: EmbroideryTechnique',
            references: [{ subjectLocalName: 'ShoplukEmbroidery', propertyLocalName: 'usesTechnique' }],
        }))).toEqual([
            'Техниката се използва от други онтологични записи: EmbroideryTechnique',
            'Използва се от:',
            'Shopluk Embroidery чрез „използва техника“',
        ])
    })

    it('uses localized status fallbacks and sanitizes storage failures', async () => {
        await i18n.changeLanguage('bg')

        expect(apiErrorMessage(new ApiError('Request failed with status 413', 413, '<html />')))
            .toBe('Избраният файл надвишава максималния разрешен размер.')
        expect(apiErrorMessage(new ApiError('storage', 500, {
            message: 'Failed to store file under storage root /private/data/media',
        }))).toBe('Файлът не можа да бъде съхранен безопасно.')
    })

    it('prefers a future stable backend code over its message', async () => {
        await i18n.changeLanguage('bg')

        expect(apiErrorMessage(new ApiError('conflict', 409, {
            code: 'USER_USERNAME_EXISTS',
            message: 'A message that may change',
        }))).toBe('Потребителското име вече се използва.')
    })

    it('localizes stable processing-job error codes', async () => {
        await i18n.changeLanguage('bg')

        expect(apiErrorMessage(new ApiError('conflict', 409, {
            code: 'PROCESSING_JOB_INVALID_TRANSITION',
            message: 'Backend wording may change',
        }))).toBe('Това действие не е възможно при текущото състояние на задачата.')

        expect(apiErrorMessage(new ApiError('conflict', 409, {
            code: 'VISION_JOB_ALREADY_ACTIVE',
            message: 'Raw backend conflict details',
        }))).toBe('За този OCR резултат вече се изпълнява ръчно визуално сравнение.')
    })

    it('localizes document deletion safeguards without exposing backend details', async () => {
        await i18n.changeLanguage('bg')

        expect(apiErrorMessage(new ApiError('service unavailable', 503, {
            code: 'DOCUMENT_VECTOR_CLEANUP_UNAVAILABLE',
            message: 'Raw Qdrant failure details',
        }))).toBe('Документът не може да бъде изтрит, защото данните му в търсачката не могат да бъдат почистени безопасно. Опитайте отново по-късно.')
    })

    it('renders chunk-generation blockers instead of a generic 422 error', async () => {
        await i18n.changeLanguage('bg')

        expect(apiErrorMessages(new ApiError('unprocessable', 422, {
            code: 'CHUNK_GENERATION_INELIGIBLE',
            message: 'Document has no eligible approved pages for chunk generation',
            blockers: [{ documentPageId: 42, code: 'PROVENANCE_INELIGIBLE', message: 'raw details' }],
        }))).toEqual([
            'Страницата още не отговаря на изискванията за създаване на текстови фрагменти.',
            'Произходът на страницата не позволява използването й.',
        ])
    })
})

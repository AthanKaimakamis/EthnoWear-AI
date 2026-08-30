import { beforeAll, describe, expect, it } from 'vitest'
import i18n from '../../../app/i18n'
import {
    canCreateReplacementJob,
    canManuallyRetryJob,
    isAutomaticRetryPending,
    processingJobErrorPresentation,
} from './processingJobPresentation'

beforeAll(async () => {
    await i18n.changeLanguage('bg')
})

describe('processingJobErrorPresentation', () => {
    const cases = [
        ['OCR_LAYOUT_INVALID', 'Невалидно оформление на страницата', 'Оформлението на страницата не може да бъде обработено безопасно.'],
        ['VISION_RESPONSE_TRUNCATED', 'Прекъснат отговор от визуалния модел', 'Визуалният модел достигна лимита на отговора. Задачата може да бъде повторена.'],
        ['VISION_RESPONSE_JSON_INVALID', 'Невалиден отговор от визуалния модел', 'Визуалният модел върна невалиден JSON отговор.'],
        ['VISION_RESPONSE_SCHEMA_INVALID', 'Невалиден формат на отговора', 'Отговорът на визуалния модел не отговаря на очаквания формат.'],
        ['VISION_ISSUE_EXCERPT_UNGROUNDED', 'Непотвърден откъс', 'Моделът посочи откъс, който не присъства точно в OCR текста.'],
        ['VISION_NUMBER_GROUNDING_INVALID', 'Непотвърдена промяна на числа', 'Предложението променя или премахва числа без достатъчно основание.'],
        ['VISION_SUGGESTION_EXPANSION_INVALID', 'Прекалено разширено предложение', 'Предложението добавя прекалено много текст.'],
        ['VISION_SUGGESTION_OVERLAP_INVALID', 'Несъответстващо предложение', 'Предложението не съответства достатъчно на OCR текста.'],
        ['VISION_CONTRACT_INVALID', 'Невалиден отговор от предишна версия', 'Старата версия на работника отхвърли отговора като невалиден. Точната причина не е налична.'],
    ] as const

    it.each(cases)('localizes %s', (code, title, message) => {
        expect(processingJobErrorPresentation(i18n.t, code)).toMatchObject({ code, title, message })
    })

    it('uses a safe fallback for unknown or absent codes', () => {
        expect(processingJobErrorPresentation(i18n.t, 'SECRET_INTERNAL_FAILURE').message).toBe('Задачата завърши с безопасно обработена грешка.')
        expect(processingJobErrorPresentation(i18n.t, null).message).toBe('Задачата завърши с безопасно обработена грешка.')
    })
})

describe('processing job lifecycle actions', () => {
    const capabilities = { retryable: false, cloneable: false, cancellable: false, deletable: false }

    it('disables retry and replacement while automatic retry is pending', () => {
        const job = { status: 'RETRY_WAIT' as const, capabilities: { ...capabilities, retryable: true, cloneable: true }, versionToken: 'v1' }
        expect(isAutomaticRetryPending(job.status)).toBe(true)
        expect(canManuallyRetryJob(job)).toBe(false)
        expect(canCreateReplacementJob(job)).toBe(false)
    })

    it('enables retry for retryable failed jobs', () => {
        const job = { status: 'FAILED' as const, capabilities: { ...capabilities, retryable: true, cloneable: true }, versionToken: 'v1' }
        expect(canManuallyRetryJob(job)).toBe(true)
        expect(canCreateReplacementJob(job)).toBe(false)
    })

    it('uses replacement for non-retryable failed jobs when supported', () => {
        const job = { status: 'FAILED' as const, capabilities: { ...capabilities, cloneable: true }, versionToken: 'v1' }
        expect(canManuallyRetryJob(job)).toBe(false)
        expect(canCreateReplacementJob(job)).toBe(true)
    })

    it.each(['RUNNING', 'CLAIMED'] as const)('exposes no retry action for %s jobs', status => {
        const job = { status, capabilities: { ...capabilities, retryable: true, cloneable: true }, versionToken: 'v1' }
        expect(canManuallyRetryJob(job)).toBe(false)
        expect(canCreateReplacementJob(job)).toBe(false)
    })
})

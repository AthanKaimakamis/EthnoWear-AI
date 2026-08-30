import { describe, expect, it } from 'vitest'
import i18n from './i18n'
import { apiEnumLabel } from './apiEnumLabels'

describe('apiEnumLabel', () => {
    it('localizes known API constants and humanizes unknown ones', async () => {
        await i18n.changeLanguage('bg')
        expect(apiEnumLabel(i18n.t, 'mediaType', 'SCAN')).toBe('Дигитализирано изображение')
        expect(apiEnumLabel(i18n.t, 'unknown', 'NEW_API_VALUE')).toBe('New api value')
    })
})

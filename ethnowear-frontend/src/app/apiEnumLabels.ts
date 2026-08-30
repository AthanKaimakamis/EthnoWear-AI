import type { TFunction } from 'i18next'

export function apiEnumLabel(t: TFunction, group: string, value: string | null | undefined) {
    if (!value) return '—'
    const key = `apiEnums.${group}.${value}`
    const translated = t(key)
    return translated === key
        ? value.replaceAll('_', ' ').toLocaleLowerCase().replace(/^./, letter => letter.toLocaleUpperCase())
        : translated
}

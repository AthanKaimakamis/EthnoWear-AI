import type { ConversationAction } from '../api/ConversationApi'

export const archiveFilterParams = {
    categories: 'categories',
    entities: 'entities',
    regions: 'regions',
} as const

const targetPaths = {
    REGIONAL_EMBROIDERY: '/archive/embroideries',
    REGIONAL_MOTIF: '/archive/motifs',
    MOTIF: '/archive/motifs',
    TECHNIQUE: '/archive/techniques',
    ORNAMENT: '/archive/ornaments',
} as const

export function readListParam(params: URLSearchParams, key: string) {
    return [...new Set(params.getAll(key).map(value => value.trim()).filter(Boolean))]
}

export function replaceListParam(params: URLSearchParams, key: string, values: string[]) {
    params.delete(key)
    ;[...new Set(values)].filter(Boolean).sort().forEach(value => params.append(key, value))
}

function safeStringList(value: unknown) {
    return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

export function archiveActionPath(action: ConversationAction) {
    if (!action || action.type !== 'OPEN_ARCHIVE_FILTER' || !action.filters) return null
    const path = targetPaths[action.target]
    if (!path) return null
    const params = new URLSearchParams()
    replaceListParam(params, archiveFilterParams.categories, safeStringList(action.filters.categoryLocalNames))
    replaceListParam(params, archiveFilterParams.entities, safeStringList(action.filters.entityLocalNames))
    replaceListParam(params, archiveFilterParams.regions, safeStringList(action.filters.regionLocalNames))
    const query = params.toString()
    return query ? `${path}?${query}` : path
}

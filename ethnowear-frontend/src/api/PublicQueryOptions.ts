import { queryOptions } from '@tanstack/react-query'
import { getEntityDetails, searchCatalogue } from './CatalogueApi'
import { getArchiveItemDetails, getRegionalEmbroideryArchive, getRegionalMotifArchive } from './PublicArchiveApi'
import { getFullReference } from './ReferenceApi'
import { publicQueryKeys } from '../app/queryClient'
import type { PageRequest } from '../types/api'
import type { ConceptCatalogQuery } from '../types/catalogue'
import type { Language } from '../types/reference'

export const publicDataStaleTime = 5 * 60 * 1000

function sorted(values: string[] | undefined) {
    return values ? [...values].sort() : []
}

function normalizedRelations(relations: ConceptCatalogQuery['relatedEntityLocalNames']) {
    return Object.fromEntries(
        Object.entries(relations ?? {})
            .sort(([left], [right]) => left.localeCompare(right))
            .map(([key, values]) => [key, sorted(values)]),
    )
}

function normalizedCatalogueQuery(query: ConceptCatalogQuery) {
    return {
        entityType: query.entityType,
        language: query.language,
        searchText: query.searchText?.trim() ?? '',
        categoryLocalNames: sorted(query.categoryLocalNames),
        relatedEntityLocalNames: normalizedRelations(query.relatedEntityLocalNames),
        relatedCategoryLocalNames: normalizedRelations(query.relatedCategoryLocalNames),
        combinationMode: query.combinationMode ?? 'AND',
    }
}

function normalizedPage(page: PageRequest) {
    return {
        page: page.page ?? 0,
        size: page.size ?? 20,
        sort: page.sort ?? '',
    }
}

export function catalogueQueryOptions(query: ConceptCatalogQuery, page: PageRequest = {}) {
    const normalizedQuery = normalizedCatalogueQuery(query)
    const normalizedPageRequest = normalizedPage(page)

    return queryOptions({
        queryKey: [...publicQueryKeys.catalogue, normalizedQuery, normalizedPageRequest] as const,
        queryFn: ({ signal }) => searchCatalogue(query, page, signal),
        staleTime: publicDataStaleTime,
    })
}

export function regionalEmbroideryArchiveQueryOptions(language: Language, previewSize = 4) {
    return queryOptions({
        queryKey: [...publicQueryKeys.archive, 'regional-embroideries', language, previewSize] as const,
        queryFn: ({ signal }) => getRegionalEmbroideryArchive(language, previewSize, signal),
        staleTime: publicDataStaleTime,
    })
}

export function regionalMotifArchiveQueryOptions(language: Language, previewSize = 4) {
    return queryOptions({
        queryKey: [...publicQueryKeys.archive, 'regional-motifs', language, previewSize] as const,
        queryFn: ({ signal }) => getRegionalMotifArchive(language, previewSize, signal),
        staleTime: publicDataStaleTime,
    })
}

export function archiveItemDetailsQueryOptions(id: number) {
    return queryOptions({
        queryKey: [...publicQueryKeys.archive, 'items', id] as const,
        queryFn: ({ signal }) => getArchiveItemDetails(id, signal),
        staleTime: publicDataStaleTime,
        enabled: Number.isInteger(id) && id > 0,
    })
}

export function referenceDataQueryOptions(language: Language) {
    return queryOptions({
        queryKey: [...publicQueryKeys.all, 'reference', 'full', language] as const,
        queryFn: () => getFullReference(language),
        staleTime: publicDataStaleTime,
    })
}

export function entityDetailsQueryOptions(
    entityType: ConceptCatalogQuery['entityType'] | null,
    localName: string | undefined,
    language: Language,
    page: PageRequest = {},
) {
    const normalizedPageRequest = normalizedPage(page)

    return queryOptions({
        queryKey: [
            ...publicQueryKeys.catalogue,
            'entity',
            entityType,
            localName ?? '',
            language,
            normalizedPageRequest,
        ] as const,
        queryFn: ({ signal }) => getEntityDetails(entityType!, localName!, language, page, signal),
        staleTime: publicDataStaleTime,
        enabled: Boolean(entityType && localName),
    })
}

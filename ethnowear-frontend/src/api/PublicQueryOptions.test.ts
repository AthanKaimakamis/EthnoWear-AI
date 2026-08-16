import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import { searchCatalogue } from './CatalogueApi'
import { catalogueQueryOptions } from './PublicQueryOptions'
import type { ConceptCatalogResultDetails } from '../types/catalogue'

vi.mock('./CatalogueApi', () => ({
    searchCatalogue: vi.fn(),
    getEntityDetails: vi.fn(),
}))

const result: ConceptCatalogResultDetails = {
    items: [],
    facets: [],
    page: {
        number: 0,
        size: 200,
        totalElements: 0,
        totalPages: 0,
        first: true,
        last: true,
    },
}

describe('public catalogue query cache', () => {
    let client: QueryClient

    beforeEach(() => {
        client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
        vi.mocked(searchCatalogue).mockResolvedValue(result)
    })

    afterEach(() => {
        client.clear()
        vi.clearAllMocks()
    })

    it('reuses fresh data for equivalent filters regardless of selection order', async () => {
        const page = { page: 0, size: 200, sort: 'label,asc' }

        await client.fetchQuery(catalogueQueryOptions({
            entityType: 'ORNAMENT',
            language: 'bg',
            categoryLocalNames: ['Floral', 'Geometric'],
            relatedEntityLocalNames: { REGION: ['Sofia', 'Elhovo'] },
        }, page))

        await client.fetchQuery(catalogueQueryOptions({
            entityType: 'ORNAMENT',
            language: 'bg',
            categoryLocalNames: ['Geometric', 'Floral'],
            relatedEntityLocalNames: { REGION: ['Elhovo', 'Sofia'] },
        }, page))

        expect(searchCatalogue).toHaveBeenCalledOnce()
    })

    it('keeps different entity types in separate cache entries', async () => {
        const page = { page: 0, size: 200, sort: 'label,asc' }

        await client.fetchQuery(catalogueQueryOptions({ entityType: 'ORNAMENT', language: 'bg' }, page))
        await client.fetchQuery(catalogueQueryOptions({ entityType: 'TECHNIQUE', language: 'bg' }, page))

        expect(searchCatalogue).toHaveBeenCalledTimes(2)
    })
})

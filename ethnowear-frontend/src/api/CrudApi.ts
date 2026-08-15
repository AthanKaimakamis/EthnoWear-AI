import { apiRequest } from './http'
import type { IdentifiableDto, PageRequest, PageResponse } from '../types/api'

export type CrudApi<WriteDto, DetailsDto extends IdentifiableDto> = {
    findAll: (page?: PageRequest, signal?: AbortSignal) => Promise<PageResponse<DetailsDto>>
    findById: (id: number, signal?: AbortSignal) => Promise<DetailsDto>
    create: (input: WriteDto) => Promise<DetailsDto>
    update: (id: number, input: WriteDto) => Promise<DetailsDto>
    remove: (id: number) => Promise<void>
}

export function createCrudApi<WriteDto, DetailsDto extends IdentifiableDto>(
    basePath: string,
): CrudApi<WriteDto, DetailsDto> {
    return {
        findAll(page: PageRequest = {}, signal?: AbortSignal) {
            return apiRequest<PageResponse<DetailsDto>>(basePath, { query: page, signal })
        },

        findById(id: number, signal?: AbortSignal) {
            return apiRequest<DetailsDto>(`${basePath}/${id}`, { signal })
        },

        create(input: WriteDto) {
            return apiRequest<DetailsDto>(basePath, { method: 'POST', body: input })
        },

        update(id: number, input: WriteDto) {
            return apiRequest<DetailsDto>(`${basePath}/${id}`, { method: 'PUT', body: input })
        },

        remove(id: number) {
            return apiRequest<void>(`${basePath}/${id}`, { method: 'DELETE' })
        },
    }
}

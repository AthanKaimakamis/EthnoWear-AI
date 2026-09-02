import { describe, expect, it, vi } from 'vitest'
import { archiveAdminApi } from './archiveAdmin'

describe('archiveAdminApi', () => {
    it('keeps list requests within the backend page-size limit', async () => {
        const findAll = vi.fn().mockResolvedValue({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 })
        const api = archiveAdminApi({
            findAll,
            findById: vi.fn(),
            create: vi.fn(),
            update: vi.fn(),
            remove: vi.fn(),
        })

        await api.findAll()

        expect(findAll).toHaveBeenCalledWith({ size: 100, sort: 'id,asc' }, undefined)
    })
})

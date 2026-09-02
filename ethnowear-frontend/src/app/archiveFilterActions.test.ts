import { describe, expect, it } from 'vitest'
import { archiveActionPath, readListParam, replaceListParam } from './archiveFilterActions'

describe('archive filter actions', () => {
    it('builds a deterministic allowlisted archive URL', () => {
        expect(archiveActionPath({
            type: 'OPEN_ARCHIVE_FILTER', label: 'Show', target: 'ORNAMENT',
            filters: { categoryLocalNames: ['BirdOrnament'], entityLocalNames: ['Eagle'], regionLocalNames: ['Shopluk'] },
        })).toBe('/archive/ornaments?categories=BirdOrnament&entities=Eagle&regions=Shopluk')
    })

    it('rejects unsupported actions and targets', () => {
        expect(archiveActionPath({ type: 'OTHER', label: 'Unsafe', target: 'ORNAMENT', filters: { categoryLocalNames: [], entityLocalNames: [], regionLocalNames: [] } } as never)).toBeNull()
        expect(archiveActionPath({ type: 'OPEN_ARCHIVE_FILTER', label: 'Unsafe', target: 'COLOR', filters: { categoryLocalNames: [], entityLocalNames: [], regionLocalNames: [] } } as never)).toBeNull()
    })

    it('round trips unique list parameters', () => {
        const params = new URLSearchParams('regions=Old')
        replaceListParam(params, 'regions', ['Shopluk', 'Dobrudzha', 'Shopluk'])
        expect(readListParam(params, 'regions')).toEqual(['Dobrudzha', 'Shopluk'])
    })
})

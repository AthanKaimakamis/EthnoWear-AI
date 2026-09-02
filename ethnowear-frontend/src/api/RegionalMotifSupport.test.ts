import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearAdminSession, setAdminSession } from '../app/adminAuthStore'
import { archiveActionPath } from '../app/archiveFilterActions'
import { conceptPath } from '../app/archiveRoutes'
import { regionalMotifsForRegion, validateArchiveClassification } from '../app/regionalMotifs'
import type { ArchiveItemWriteDto } from '../types/archive'
import type { ReferenceData } from '../types/reference'
import { synchronizeDerivedRegionTypes } from './OntologyAdminApi'
import { getRegionalMotifArchive } from './PublicArchiveApi'
import { getFullReference, getRegionalMotifTypes } from './ReferenceApi'

const json = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
const motif = { iri: 'https://example.test/ElhovoMotif', localName: 'ElhovoMotif', label: 'Елховски мотив' }
const refs = {
    language: 'bg', regions: [{ iri: 'r', localName: 'ElhovoRegion', label: 'Елховски регион' }],
    regionGroups: [], ornaments: [], ornamentTypes: [], colors: [], techniques: [], techniqueTypes: [], motifs: [],
    regionalMotifTypes: [motif], regionalEmbroideryTypes: [], regionsByRegionGroup: {},
    regionByRegionalEmbroidery: {}, regionByRegionalMotif: { ElhovoMotif: 'ElhovoRegion' },
    ornamentsByRegion: {}, techniquesByRegion: {}, ornamentsByType: {}, techniquesByType: {},
} satisfies ReferenceData
const item = {
    sourceReferenceId: 1, collectionId: null, inventoryNumber: null, titleBg: 'Запис', titleEn: null,
    descriptionBg: null, descriptionEn: null, archiveType: 'MOTIF_EXAMPLE', periodText: null, originText: null,
    currentLocation: null, trustedLevel: 'LIKELY', ontologyRegionIri: 'r', ontologyRegionLocalName: 'ElhovoRegion',
    ontologyRegionalEmbroideryIri: null, ontologyRegionalEmbroideryLocalName: null,
    ontologyRegionalMotifIri: motif.iri, ontologyRegionalMotifLocalName: motif.localName,
} satisfies ArchiveItemWriteDto

describe('regional motif frontend support', () => {
    beforeEach(() => {
        vi.restoreAllMocks()
        clearAdminSession()
        sessionStorage.clear()
        setAdminSession({ accessToken: 'jwt', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', username: 'admin' })
    })

    it('maps full reference data and loads the dedicated reference endpoint', async () => {
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(json(refs)).mockResolvedValueOnce(json([motif]))
        expect((await getFullReference('bg')).regionByRegionalMotif).toEqual({ ElhovoMotif: 'ElhovoRegion' })
        expect(await getRegionalMotifTypes('bg')).toEqual([motif])
        expect(String(fetchMock.mock.calls[1][0])).toContain('/api/reference/regional-motif-types?language=bg')
    })

    it('loads public sections and invokes explicit derived-type synchronization', async () => {
        const overview = { language: 'bg', sections: [{ regionalMotif: motif, totalItems: 0, previewItems: [] }] }
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(json(overview)).mockResolvedValueOnce(json({ created: 2 }))
        expect((await getRegionalMotifArchive('bg', 4)).sections[0].regionalMotif).toEqual(motif)
        expect(await synchronizeDerivedRegionTypes()).toEqual({ created: 2 })
        expect(String(fetchMock.mock.calls[1][0])).toContain('/api/admin/ontology/regions/synchronize-derived-types')
        expect(fetchMock.mock.calls[1][1]?.method).toBe('POST')
    })

    it('filters motifs by region and validates required and stale classifications', () => {
        expect(regionalMotifsForRegion(refs, 'ElhovoRegion')).toEqual([motif])
        expect(regionalMotifsForRegion(refs, 'OtherRegion')).toEqual([])
        expect(validateArchiveClassification(item, refs)).toBeNull()
        expect(validateArchiveClassification({ ...item, ontologyRegionLocalName: null }, refs)).toBe('curator.validation.region')
        expect(validateArchiveClassification({ ...item, ontologyRegionalMotifLocalName: null }, refs)).toBe('curator.validation.regionalMotif')
        expect(validateArchiveClassification({ ...item, ontologyRegionLocalName: 'OtherRegion' }, refs)).toBe('curator.validation.regionalMotifRegion')
    })

    it('routes catalogue and conversation actions without deriving paths from text', () => {
        expect(conceptPath('REGIONAL_MOTIF', 'ElhovoMotif')).toBe('/archive/motifs/ElhovoMotif')
        expect(archiveActionPath({ type: 'OPEN_ARCHIVE_FILTER', target: 'REGIONAL_MOTIF', label: 'Open', filters: { categoryLocalNames: [], entityLocalNames: ['ElhovoMotif'], regionLocalNames: [] } }))
            .toBe('/archive/motifs?entities=ElhovoMotif')
    })
})

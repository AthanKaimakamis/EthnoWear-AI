import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearAdminSession, setAdminSession } from '../app/adminAuthStore'
import { archiveActionPath } from '../app/archiveFilterActions'
import { conceptPath } from '../app/archiveRoutes'
import { changeArchiveItemField, regionalMotifsForRegion, validateArchiveClassification } from '../app/regionalMotifs'
import { buildArchiveEntryPayload } from '../components/admin/archive-editor/archiveEditorPayload'
import { archiveMediaDefaults } from '../components/admin/archive-editor/archiveMediaDefaults'
import type { MediaDraft } from '../components/admin/archive-editor/ArchiveEditorSections'
import type { ArchiveItemFeatureDetails, ArchiveItemWriteDto } from '../types/archive'
import type { ReferenceData } from '../types/reference'
import { synchronizeDerivedRegionTypes } from './OntologyAdminApi'
import { getRegionalMotifArchive } from './PublicArchiveApi'
import { getFullReference, getRegionalMotifTypes } from './ReferenceApi'

const json = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
const motif = { iri: 'https://example.test/ElhovoMotif', localName: 'ElhovoMotif', label: 'Елховски мотив' }
const embroidery = { iri: 'https://example.test/ElhovoEmbroidery', localName: 'ElhovoEmbroidery', label: 'Елховска шевица' }
const refs = {
    language: 'bg', regions: [{ iri: 'r', localName: 'ElhovoRegion', label: 'Елховски регион' }],
    regionGroups: [], ornaments: [], ornamentTypes: [], colors: [], techniques: [], techniqueTypes: [], motifs: [],
    regionalMotifTypes: [motif], regionalEmbroideryTypes: [embroidery], regionsByRegionGroup: {},
    regionByRegionalEmbroidery: { ElhovoEmbroidery: 'ElhovoRegion' }, regionByRegionalMotif: { ElhovoMotif: 'ElhovoRegion' },
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
    it('inherits only an unambiguous citation and never overwrites an explicit citation', () => {
        const image = (sourceReferenceId: number | null) => ({ asset: { sourceReferenceId } } as MediaDraft)
        const blank = { ...item, sourceReferenceId: 0 }
        expect(archiveMediaDefaults(blank, [image(5), image(5)]).sourceReferenceId).toBe(5)
        expect(archiveMediaDefaults(blank, [image(5), image(6)]).sourceReferenceId).toBe(0)
        expect(archiveMediaDefaults(blank, [image(5), image(null)]).sourceReferenceId).toBe(0)
        expect(archiveMediaDefaults(item, [image(5)]).sourceReferenceId).toBe(1)
        expect(archiveMediaDefaults(blank, []).sourceReferenceId).toBe(0)
    })

    it('does not guess a regional category when the mapping is ambiguous', () => {
        const ambiguous = { ...refs, regionalMotifTypes: [motif, { ...motif, localName: 'OtherMotif' }],
            regionByRegionalMotif: { ...refs.regionByRegionalMotif, OtherMotif: 'ElhovoRegion' } }
        expect(changeArchiveItemField(item, 'ontologyRegionLocalName', 'ElhovoRegion', ambiguous).ontologyRegionalMotifLocalName).toBeNull()
    })
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

    it('preserves compatible embroidery and automatically resolves the regional motif', () => {
        const before: ArchiveItemWriteDto = { ...item, archiveType: 'EMBROIDERY_SAMPLE',
            ontologyRegionalMotifIri: null, ontologyRegionalMotifLocalName: null,
            ontologyRegionalEmbroideryIri: embroidery.iri, ontologyRegionalEmbroideryLocalName: embroidery.localName }
        const after = changeArchiveItemField(before, 'archiveType', 'MOTIF_EXAMPLE', refs)
        expect(after.ontologyRegionalEmbroideryIri).toBe(embroidery.iri)
        expect(after.ontologyRegionalMotifLocalName).toBe(motif.localName)
        expect(validateArchiveClassification(after, refs)).toBeNull()
        expect(validateArchiveClassification({ ...after, ontologyRegionalMotifIri: motif.iri, ontologyRegionalMotifLocalName: motif.localName }, refs)).toBeNull()
        expect(validateArchiveClassification(item, refs)).toBeNull()
    })

    it('validates optional embroidery and clears only incompatible classifications on region change', () => {
        const before = { ...item, ontologyRegionalEmbroideryIri: embroidery.iri, ontologyRegionalEmbroideryLocalName: embroidery.localName }
        expect(validateArchiveClassification(before, refs)).toBeNull()
        expect(validateArchiveClassification(before, { ...refs, regionByRegionalEmbroidery: { ElhovoEmbroidery: 'OtherRegion' } }))
            .toBe('curator.validation.regionalEmbroideryRegion')
        expect(changeArchiveItemField(before, 'ontologyRegionLocalName', 'ElhovoRegion', refs)).toEqual(before)
        const after = changeArchiveItemField(before, 'ontologyRegionLocalName', 'OtherRegion', refs)
        expect(after).toEqual({ ...before, ontologyRegionIri: null, ontologyRegionLocalName: 'OtherRegion', ontologyRegionalEmbroideryIri: null,
            ontologyRegionalEmbroideryLocalName: null, ontologyRegionalMotifIri: null, ontologyRegionalMotifLocalName: null })
    })

    it('builds motif payloads without features and preserves legacy and observed feature metadata', () => {
        const selections = { ORNAMENT: [], TECHNIQUE: [], COLOR: [] }
        expect(buildArchiveEntryPayload(item, selections, [], []).features).toEqual([])
        const saved: ArchiveItemFeatureDetails = { id: 8, archiveItemId: 1, featureType: 'ORNAMENT',
            ontologyIri: 'urn:Bird', ontologyLocalName: 'Bird', confidence: 0.6, validated: false,
            notes: 'Evidence note', sourceReferenceId: 7, createdAt: '2026-09-07', updatedAt: '2026-09-07' }
        const legacy: ArchiveItemFeatureDetails = { ...saved, id: 9, featureType: 'MOTIF', ontologyIri: 'urn:Legacy', ontologyLocalName: 'Legacy' }
        const payload = buildArchiveEntryPayload(item, { ...selections, ORNAMENT: [{ iri: 'urn:Bird', localName: 'Bird', label: 'Bird' }] }, [saved, legacy], [])
        expect(payload.features).toEqual([
            expect.objectContaining({ id: 9, featureType: 'MOTIF', notes: 'Evidence note', sourceReferenceId: 7 }),
            expect.objectContaining({ id: 8, confidence: 0.6, validated: false, notes: 'Evidence note', sourceReferenceId: 7 }),
        ])
        expect(payload.features[0]).not.toHaveProperty('archiveItemId')
        expect(payload.features[0]).not.toHaveProperty('createdAt')
    })
})

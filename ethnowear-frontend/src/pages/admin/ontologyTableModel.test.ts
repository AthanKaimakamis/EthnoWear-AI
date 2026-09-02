import { describe, expect, it } from 'vitest'
import type { OntologyEntity } from '../../types/ontologyAdmin'
import { filterOntologyEntities, ontologyProfileScore, ontologyRelations } from './ontologyTableModel'

const complete: OntologyEntity = {
    iri: 'urn:bird', localName: 'BirdOrnament', labelBg: 'Птица', labelEn: 'Bird',
    altLabelsBg: [], altLabelsEn: [], commentBg: 'Описание', commentEn: 'Description',
    typeLocalNames: ['AnimalOrnament'], characteristicRegionLocalNames: ['ShoplukRegion'],
}
const incomplete: OntologyEntity = {
    ...complete, iri: 'urn:cross', localName: 'CrossOrnament', labelEn: null,
    commentEn: null, typeLocalNames: ['GeometricOrnament'], characteristicRegionLocalNames: [],
}

describe('ontology table model', () => {
    it('counts profile fields and relationships', () => {
        expect(ontologyProfileScore(complete)).toBe(4)
        expect(ontologyProfileScore(incomplete)).toBe(2)
        expect(ontologyRelations(complete)).toEqual(['ShoplukRegion'])
    })

    it('combines category, profile and relationship filters', () => {
        expect(filterOntologyEntities([complete, incomplete], 'AnimalOrnament', 'complete', 'linked')).toEqual([complete])
        expect(filterOntologyEntities([complete, incomplete], '', 'missing-labels', 'unlinked')).toEqual([incomplete])
        expect(filterOntologyEntities([complete, incomplete], '', 'missing-descriptions', 'all')).toEqual([incomplete])
    })
})

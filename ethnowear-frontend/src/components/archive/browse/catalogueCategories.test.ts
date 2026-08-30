import { describe, expect, it } from 'vitest'
import type { EntityCardDetails } from '../../../types/catalogue'
import { buildCatalogueCategories } from './catalogueCategories'

describe('buildCatalogueCategories', () => {
    it('does not present the ontology root type as a category', () => {
        const item: EntityCardDetails = {
            entityType: 'TECHNIQUE', iri: '#BackStitch', localName: 'BackStitch', label: 'Бод зад игла', comment: null,
            categories: [{ targetEntityType: 'TECHNIQUE', iri: '#Technique', localName: 'Technique', label: 'Техника' }],
            evidenceCount: 2,
            representativeMediaAssetId: 17,
        }

        const sections = buildCatalogueCategories([item], 'Без категория', ['Technique'])

        expect(sections).toHaveLength(1)
        expect(sections[0].category.localName).toBe('Uncategorized')
        expect(sections[0].items[0].localName).toBe('BackStitch')
        expect(sections[0].items[0].evidenceCount).toBe(2)
        expect(sections[0].items[0].representativeMediaAssetId).toBe(17)
    })
})

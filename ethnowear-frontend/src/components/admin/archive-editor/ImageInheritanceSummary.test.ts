import { describe, expect, it } from 'vitest'
import { inheritImageObservations } from './ImageInheritanceSummary'
import type { MediaDraft } from './ArchiveEditorSections'
import type { MediaEntityLinkDetails } from '../../../types/archive'

describe('image observations', () => {
    const media = [1, 2].map(id => ({ asset: { id, fileName: `${id}.jpg`, sourceReferenceId: id } }) as MediaDraft)
    const links = [1, 2].map(id => ({ mediaAssetId: id, entityType: 'ORNAMENT', ontologyIri: 'urn:flower', ontologyLocalName: 'Flower' }) as MediaEntityLinkDetails)
    it('combines confirmed links and keeps both citations', () => {
        expect(inheritImageObservations(media, links)).toEqual([{ featureType: 'ORNAMENT', ontologyIri: 'urn:flower', ontologyLocalName: 'Flower', origins: [{ mediaAssetId: 1, fileName: '1.jpg', sourceReferenceId: 1 }, { mediaAssetId: 2, fileName: '2.jpg', sourceReferenceId: 2 }] }])
    })
    it('removes only the detached image contribution and excludes motif features', () => {
        expect(inheritImageObservations(media.slice(0, 1), links)[0].origins).toHaveLength(1)
        expect(inheritImageObservations([], links)).toEqual([])
        expect(inheritImageObservations(media, [{ ...links[0], entityType: 'MOTIF' }])).toEqual([])
    })
})

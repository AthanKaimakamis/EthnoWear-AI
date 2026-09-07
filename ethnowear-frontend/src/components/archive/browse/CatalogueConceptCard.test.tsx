import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderApp } from '../../../test/render'
import CatalogueConceptCard from './CatalogueConceptCard'

vi.mock('@tanstack/react-query', () => ({ useQuery: () => ({ data: { evidence: { content: [1, 2, 3, 4].map(id => ({ previewMedia: { mediaAssetId: id, mediaType: 'IMAGE' } })) } } }) }))

beforeEach(() => {
    vi.stubGlobal('IntersectionObserver', class { observe() {} disconnect() {} })
})

describe('catalogue image mosaic', () => {
    it('deduplicates the representative image and shows at most three previews', () => {
        renderApp(<CatalogueConceptCard entityType="ORNAMENT" item={{ iri: 'urn:flower', localName: 'Flower', label: 'Flower', representativeMediaAssetId: 1, evidenceCount: 4 }} />)
        expect(screen.getAllByRole('img')).toHaveLength(3)
        expect(screen.getByText('+1')).toBeVisible()
        expect(screen.getByRole('link')).toHaveAttribute('href', '/archive/ornaments/Flower')
    })
})

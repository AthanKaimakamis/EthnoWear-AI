import { describe, expect, it } from 'vitest'
import type { MediaAssetDetails } from '../../types/archive'
import { approvedLibraryAssets, canDeleteMediaAsset } from './mediaLibraryModel'

function asset(id: number, reviewState?: 'PENDING' | 'APPROVED' | 'REJECTED' | 'OUTDATED'): MediaAssetDetails {
    return {
        id, sourceReferenceId: null, fileName: `asset-${id}.jpg`, filePath: null, storageUrl: null, mimeType: 'image/jpeg', mediaType: 'IMAGE', width: 100, height: 100, sizeBytes: 10, checksum: null, thumbnailPath: null, description: null, createdAt: '', updatedAt: '',
        documentFigure: reviewState ? { documentId: 7, documentPageId: 11, pageSequence: 2, figureId: id, caption: 'Figure', printedFigureNumber: null, sourceReferenceId: 9, reviewState } : null,
    }
}

describe('approved Media Library figures', () => {
    it('preserves manual uploads and includes only approved document figures', () => {
        expect(approvedLibraryAssets([
            asset(1), asset(2, 'APPROVED'), asset(3, 'PENDING'), asset(4, 'REJECTED'), asset(5, 'OUTDATED'),
        ]).map(value => value.id)).toEqual([1, 2])
    })

    it('allows figure deletion while protecting linked manual media', () => {
        expect(canDeleteMediaAsset(asset(1), 0)).toBe(true)
        expect(canDeleteMediaAsset(asset(1), 1)).toBe(false)
        expect(canDeleteMediaAsset(asset(2, 'APPROVED'), 1)).toBe(true)
    })
})

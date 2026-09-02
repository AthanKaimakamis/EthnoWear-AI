import type { MediaAssetDetails } from '../../types/archive'

export function approvedLibraryAssets(assets: MediaAssetDetails[]) {
    return assets.filter(asset => !asset.documentFigure || asset.documentFigure.reviewState === 'APPROVED')
}

export function canDeleteMediaAsset(asset: MediaAssetDetails, usageCount: number) {
    return Boolean(asset.documentFigure) || usageCount === 0
}

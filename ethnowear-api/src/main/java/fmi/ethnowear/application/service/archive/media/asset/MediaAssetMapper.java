package fmi.ethnowear.application.service.archive.media.asset;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaAssetMetadataWriteDto;
import fmi.ethnowear.application.dto.archive.media.DocumentFigureMediaLinkDetails;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class MediaAssetMapper {

    public void applyMetadata(
            @NonNull MediaAsset asset,
            @NonNull MediaAssetMetadataWriteDto input,
            SourceReference reference
    ) {
        asset.setSourceReference(reference);
        asset.setDescription(input.description());
        asset.updateRights(
                input.rightsStatus(),
                input.license(),
                input.publicDisplayAllowed()
        );
    }

    public MediaAssetDetails toDetails(@NonNull MediaAsset asset) {
        return toDetails(asset, null);
    }

    public MediaAssetDetails toDetails(
            @NonNull MediaAsset asset,
            DocumentFigureMediaLinkDetails documentFigure
    ) {
        Long sourceReferenceId = asset.getSourceReference() == null
                ? null
                : asset.getSourceReference().getId();

        return new MediaAssetDetails(
                asset.getId(), sourceReferenceId, asset.getFileName(),
                asset.getFilePath(), asset.getStorageUrl(), asset.getMimeType(),
                asset.getMediaType(), asset.getWidth(), asset.getHeight(),
                asset.getSizeBytes(), asset.getChecksum(), asset.getThumbnailPath(),
                asset.getDescription(), asset.getRightsStatus(), asset.getLicense(),
                asset.isPublicDisplayAllowed(), asset.getCreatedAt(), asset.getUpdatedAt(),
                documentFigure
        );
    }
}

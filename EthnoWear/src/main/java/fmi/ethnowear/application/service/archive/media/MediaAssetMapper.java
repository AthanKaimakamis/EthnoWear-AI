package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaAssetMetadataWriteDto;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class MediaAssetMapper {
    public void applyMetadata(
            @NonNull MediaAsset asset,
            @NonNull MediaAssetMetadataWriteDto input,
            SourceReference reference) {
        asset.setSourceReference(reference);
        asset.setDescription(input.description());
    }

    public MediaAssetDetails toDetails(@NonNull MediaAsset asset) {
        Long sourceReferenceId = asset.getSourceReference() == null
                ? null
                : asset.getSourceReference().getId();

        return new MediaAssetDetails(
                asset.getId(),
                sourceReferenceId,
                asset.getFileName(),
                asset.getFilePath(),
                asset.getStorageUrl(),
                asset.getMimeType(),
                asset.getMediaType(),
                asset.getWidth(),
                asset.getHeight(),
                asset.getSizeBytes(),
                asset.getChecksum(),
                asset.getThumbnailPath(),
                asset.getDescription(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }
}

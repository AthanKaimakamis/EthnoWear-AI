package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.api.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.api.dto.archive.media.MediaAssetWriteDto;
import fmi.ethnowear.dal.entity.MediaAsset;
import fmi.ethnowear.dal.entity.SourceReference;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class MediaAssetMapper {
    public void apply(@NonNull MediaAsset asset, @NonNull MediaAssetWriteDto input, SourceReference reference) {
        asset.setSourceReference(reference);
        asset.setFileName(input.fileName());
        asset.setFilePath(input.filePath());
        asset.setStorageUrl(input.storageUrl());
        asset.setMimeType(input.mimeType());
        asset.setMediaType(input.mediaType());
        asset.setWidth(input.width());
        asset.setHeight(input.height());
        asset.setSizeBytes(input.sizeBytes());
        asset.setChecksum(input.checksum());
    }

    public MediaAssetDetails toDetails(@NonNull MediaAsset asset) {
        Long refId = asset.getSourceReference() == null
                ? null
                : asset.getSourceReference().getId();

        return new MediaAssetDetails(
                asset.getId(),
                refId,
                asset.getFileName(),
                asset.getFilePath(),
                asset.getStorageUrl(),
                asset.getMimeType(),
                asset.getMediaType(),
                asset.getWidth(),
                asset.getHeight(),
                asset.getSizeBytes(),
                asset.getChecksum(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }
}

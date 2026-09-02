package fmi.ethnowear.application.dto.archive.media;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.rights.RightsStatus;

import java.time.LocalDateTime;

public record MediaAssetDetails(
        Long id,
        Long sourceReferenceId,
        String fileName,
        String filePath,
        String storageUrl,
        String mimeType,
        MediaType mediaType,
        Integer width,
        Integer height,
        Long sizeBytes,
        String checksum,
        String thumbnailPath,
        String description,
        RightsStatus rightsStatus,
        String license,
        boolean publicDisplayAllowed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        DocumentFigureMediaLinkDetails documentFigure
) implements IdentifiableDto {

    public MediaAssetDetails(
            Long id,
            Long sourceReferenceId,
            String fileName,
            String filePath,
            String storageUrl,
            String mimeType,
            MediaType mediaType,
            Integer width,
            Integer height,
            Long sizeBytes,
            String checksum,
            String thumbnailPath,
            String description,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            DocumentFigureMediaLinkDetails documentFigure
    ) {
        this(
                id, sourceReferenceId, fileName, filePath, storageUrl,
                mimeType, mediaType, width, height, sizeBytes, checksum,
                thumbnailPath, description,
                RightsStatus.UNKNOWN, null, false,
                createdAt, updatedAt, documentFigure
        );
    }
}

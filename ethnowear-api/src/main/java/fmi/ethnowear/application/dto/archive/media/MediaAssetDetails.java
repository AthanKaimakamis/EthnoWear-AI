package fmi.ethnowear.application.dto.archive.media;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.archive.MediaType;

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
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        DocumentFigureMediaLinkDetails documentFigure
) implements IdentifiableDto {
}

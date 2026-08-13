package fmi.ethnowear.api.dto.archive.media;

import fmi.ethnowear.application.enums.MediaType;

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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

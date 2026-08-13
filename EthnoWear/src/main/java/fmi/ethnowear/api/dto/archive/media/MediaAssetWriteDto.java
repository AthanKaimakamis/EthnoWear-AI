package fmi.ethnowear.api.dto.archive.media;

import fmi.ethnowear.application.enums.MediaType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record MediaAssetWriteDto(
        Long sourceReferenceId,
        String fileName,
        String filePath,
        String storageUrl,
        String mimeType,

        @NotNull
        MediaType mediaType,

        @Positive
        Integer width,

        @Positive
        Integer height,

        @PositiveOrZero
        Long sizeBytes,

        String checksum
) {
}

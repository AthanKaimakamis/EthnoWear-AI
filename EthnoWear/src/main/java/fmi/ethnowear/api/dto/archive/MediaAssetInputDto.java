package fmi.ethnowear.api.dto.archive;

import fmi.ethnowear.application.enums.MediaRole;
import fmi.ethnowear.application.enums.MediaType;
import jakarta.validation.constraints.NotNull;

public record MediaAssetInputDto(
        String fileName,
        String filePath,
        String storageUrl,
        String mimeType,

        @NotNull
        MediaType mediaType,

        @NotNull
        MediaRole role,

        Integer width,
        Integer height,
        Long sizeBytes,
        String checksum,
        String captionBg,
        String captionEn
) {
}

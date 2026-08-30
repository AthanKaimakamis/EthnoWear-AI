package fmi.ethnowear.application.dto.archive.media;

import jakarta.validation.constraints.Size;

public record MediaAssetMetadataWriteDto(
        Long sourceReferenceId,

        @Size(max = 2000)
        String description
) {
}

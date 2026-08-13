package fmi.ethnowear.api.dto.archive.media;

import fmi.ethnowear.application.enums.MediaRole;
import jakarta.validation.constraints.NotNull;

public record ArchiveItemMediaWriteDto(
        @NotNull
        Long archiveItemId,

        @NotNull
        Long mediaAssetId,

        @NotNull
        MediaRole role,

        String captionBg,
        String captionEn
) {
}

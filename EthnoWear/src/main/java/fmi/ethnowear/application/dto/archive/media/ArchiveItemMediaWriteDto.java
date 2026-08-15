package fmi.ethnowear.application.dto.archive.media;

import fmi.ethnowear.domain.model.archive.MediaRole;
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

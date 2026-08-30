package fmi.ethnowear.application.dto.archive.workflow;

import fmi.ethnowear.domain.model.archive.MediaRole;
import jakarta.validation.constraints.NotNull;

public record ArchiveEntryMediaWriteDto(
        Long id,

        @NotNull
        Long mediaAssetId,

        @NotNull
        MediaRole role,

        String captionBg,

        String captionEn
) {
}

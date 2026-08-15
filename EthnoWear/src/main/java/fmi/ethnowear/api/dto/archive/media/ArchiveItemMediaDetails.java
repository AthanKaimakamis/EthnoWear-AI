package fmi.ethnowear.api.dto.archive.media;

import fmi.ethnowear.api.dto.IdentifiableDto;
import fmi.ethnowear.application.enums.MediaRole;

import java.time.LocalDateTime;

public record ArchiveItemMediaDetails(
        Long id,
        Long archiveItemId,
        Long mediaAssetId,
        MediaRole role,
        String captionBg,
        String captionEn,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements IdentifiableDto {
}

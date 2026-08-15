package fmi.ethnowear.application.dto.archive.media;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.archive.MediaRole;

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

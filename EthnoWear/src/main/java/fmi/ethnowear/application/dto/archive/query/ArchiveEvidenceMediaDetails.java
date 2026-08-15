package fmi.ethnowear.application.dto.archive.query;

import fmi.ethnowear.domain.model.archive.MediaRole;
import fmi.ethnowear.domain.model.archive.MediaType;

public record ArchiveEvidenceMediaDetails(
        Long archiveItemMediaId,
        Long mediaAssetId,
        MediaRole role,
        String fileName,
        String filePath,
        String storageUrl,
        String mimeType,
        MediaType mediaType,
        String captionBg,
        String captionEn
) {
}

package fmi.ethnowear.api.dto.archive.query;

import fmi.ethnowear.application.enums.MediaRole;
import fmi.ethnowear.application.enums.MediaType;

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

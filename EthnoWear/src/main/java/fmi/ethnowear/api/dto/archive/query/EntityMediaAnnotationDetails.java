package fmi.ethnowear.api.dto.archive.query;

import fmi.ethnowear.application.enums.MediaFeatureAnnotationType;
import fmi.ethnowear.application.enums.MediaRole;
import fmi.ethnowear.application.enums.MediaType;

import java.math.BigDecimal;

public record EntityMediaAnnotationDetails(
        Long annotationId,
        Long archiveItemId,
        Long archiveItemMediaId,
        Long mediaAssetId,
        MediaRole role,
        MediaType mediaType,
        String fileName,
        String filePath,
        String storageUrl,
        String mimeType,
        String captionBg,
        String captionEn,
        MediaFeatureAnnotationType annotationType,
        BigDecimal x,
        BigDecimal y,
        BigDecimal width,
        BigDecimal height,
        String note
) {
}
package fmi.ethnowear.application.dto.archive.query;

import fmi.ethnowear.domain.model.archive.MediaFeatureAnnotationType;
import fmi.ethnowear.domain.model.archive.MediaRole;
import fmi.ethnowear.domain.model.archive.MediaType;

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
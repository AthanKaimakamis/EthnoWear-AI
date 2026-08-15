package fmi.ethnowear.api.dto.archive.media;

import fmi.ethnowear.api.dto.IdentifiableDto;
import fmi.ethnowear.application.enums.MediaFeatureAnnotationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MediaFeatureAnnotationDetails(
        Long id,
        Long archiveItemMediaId,
        Long archiveItemFeatureId,
        MediaFeatureAnnotationType annotationType,
        BigDecimal x,
        BigDecimal y,
        BigDecimal width,
        BigDecimal height,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements IdentifiableDto {
}

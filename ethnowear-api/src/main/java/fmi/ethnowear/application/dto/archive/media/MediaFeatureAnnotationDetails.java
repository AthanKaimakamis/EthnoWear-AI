package fmi.ethnowear.application.dto.archive.media;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.archive.MediaFeatureAnnotationType;

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

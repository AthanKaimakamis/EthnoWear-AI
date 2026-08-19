package fmi.ethnowear.application.dto.document.query;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.document.DocumentPageRenditionType;

import java.time.LocalDateTime;

public record DocumentPageMediaDetails(
        Long id,
        Long mediaAssetId,
        String fileName,
        String mimeType,
        DocumentPageRenditionType renditionType,
        boolean original,
        boolean preferredOcrInput,
        Long derivativeOfDocumentPageMediaId,
        Long producingJobId,
        Integer displayOrder,
        Integer width,
        Integer height,
        Integer dpi,
        String colorMode,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements IdentifiableDto {
}

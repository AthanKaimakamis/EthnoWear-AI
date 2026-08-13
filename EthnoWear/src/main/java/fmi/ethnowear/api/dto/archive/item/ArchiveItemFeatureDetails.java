package fmi.ethnowear.api.dto.archive.item;

import fmi.ethnowear.application.enums.FeatureType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ArchiveItemFeatureDetails(
        Long id,
        Long archiveItemId,
        FeatureType featureType,
        String ontologyIri,
        String ontologyLocalName,
        BigDecimal confidence,
        boolean validated,
        String notes,
        Long sourceReferenceId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

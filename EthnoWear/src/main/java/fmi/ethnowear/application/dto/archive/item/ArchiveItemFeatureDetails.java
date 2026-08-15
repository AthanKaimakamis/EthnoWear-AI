package fmi.ethnowear.application.dto.archive.item;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.ontology.FeatureType;

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
) implements IdentifiableDto {
}

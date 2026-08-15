package fmi.ethnowear.application.dto.archive.item;

import fmi.ethnowear.domain.model.ontology.FeatureType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ArchiveItemFeatureWriteDto(
        @NotNull
        Long archiveItemId,

        @NotNull
        FeatureType featureType,

        @NotBlank
        String ontologyIri,

        @NotBlank
        String ontologyLocalName,

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        BigDecimal confidence,

        boolean validated,

        String notes,

        Long sourceReferenceId
) {
}

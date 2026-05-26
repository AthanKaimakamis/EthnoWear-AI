package fmi.ethnowear.api.dto.archive;

import fmi.ethnowear.application.enums.FeatureType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ArchiveFeatureInputDto(
        @NotNull
        FeatureType featureType,

        @NotBlank
        String ontologyIri,

        @NotBlank
        String ontologyLocalName,

        BigDecimal confidence,
        boolean validated,
        String notes
) {
}

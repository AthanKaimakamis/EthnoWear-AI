package fmi.ethnowear.application.dto.archive.media;

import fmi.ethnowear.domain.model.ontology.FeatureType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MediaEntityLinkWriteDto(
        @NotNull Long mediaAssetId,
        @NotNull FeatureType entityType,
        @NotBlank String ontologyIri,
        @NotBlank String ontologyLocalName,
        String description
) { }

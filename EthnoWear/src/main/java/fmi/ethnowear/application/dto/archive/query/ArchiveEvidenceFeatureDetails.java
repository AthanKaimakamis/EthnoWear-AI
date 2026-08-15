package fmi.ethnowear.application.dto.archive.query;

import fmi.ethnowear.domain.model.ontology.FeatureType;

import java.math.BigDecimal;

public record ArchiveEvidenceFeatureDetails(
        Long id,
        FeatureType featureType,
        String ontologyIri,
        String ontologyLocalName,
        BigDecimal confidence,
        String notes,
        Long sourceReferenceId
) {
}

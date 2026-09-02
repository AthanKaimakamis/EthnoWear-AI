package fmi.ethnowear.application.dto.ontology.admin;

import fmi.ethnowear.domain.model.ontology.OntologyVersionStatus;

import java.time.LocalDateTime;

public record OntologyVersionDetails(
        Long id,
        long versionNumber,
        LocalDateTime createdAt,
        Long createdByUserId,
        String createdByUsername,
        String changeReason,
        String contentHash,
        String fileName,
        String ontologyNamespace,
        boolean valid,
        String validationMessage,
        Long previousVersionId,
        Long previousVersionNumber,
        Long restoredFromVersionId,
        Long restoredFromVersionNumber,
        OntologyVersionStatus status
) {
}

package fmi.ethnowear.application.dto.document.query.history;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.document.review.ProvenanceEventType;
import fmi.ethnowear.domain.model.document.review.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.review.ProvenanceTrustState;

import java.time.LocalDateTime;

public record DocumentPageProvenanceEventDetails(
        Long id,
        Long documentPageId,
        ProvenanceEventType eventType,
        Long previousSourceReferenceId,
        Long newSourceReferenceId,
        ProvenanceStatus previousProvenanceStatus,
        ProvenanceStatus newProvenanceStatus,
        ProvenanceTrustState previousTrustState,
        ProvenanceTrustState newTrustState,
        Long previousCanonicalDocumentPageId,
        Long newCanonicalDocumentPageId,
        String reviewedBy,
        String reason,
        LocalDateTime createdAt
) implements IdentifiableDto {
}

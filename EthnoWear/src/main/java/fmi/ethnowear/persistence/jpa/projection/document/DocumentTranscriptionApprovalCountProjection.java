package fmi.ethnowear.persistence.jpa.projection.document;

import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;

public interface DocumentTranscriptionApprovalCountProjection {

    Long getDocumentId();

    TranscriptionApprovalState getState();

    long getTotal();
}

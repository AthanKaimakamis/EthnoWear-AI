package fmi.ethnowear.persistence.jpa.projection.document;

import fmi.ethnowear.domain.model.document.processing.ProcessingState;

public interface DocumentProcessingStateCountProjection {

    Long getDocumentId();

    ProcessingState getState();

    long getTotal();
}

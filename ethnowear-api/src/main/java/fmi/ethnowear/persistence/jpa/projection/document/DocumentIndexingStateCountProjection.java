package fmi.ethnowear.persistence.jpa.projection.document;

import fmi.ethnowear.domain.model.document.indexing.IndexingState;

public interface DocumentIndexingStateCountProjection {

    Long getDocumentId();

    IndexingState getState();

    long getTotal();
}

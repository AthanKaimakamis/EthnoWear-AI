package fmi.ethnowear.persistence.jpa.projection.document;

import fmi.ethnowear.domain.model.document.indexing.IndexingState;

public interface DocumentPageIndexingStateCountProjection {

    Long getPageId();

    IndexingState getState();

    long getTotal();
}

package fmi.ethnowear.persistence.jpa.projection.document;

import fmi.ethnowear.domain.model.document.review.ReviewState;

public interface DocumentReviewStateCountProjection {

    Long getDocumentId();

    ReviewState getState();

    long getTotal();
}

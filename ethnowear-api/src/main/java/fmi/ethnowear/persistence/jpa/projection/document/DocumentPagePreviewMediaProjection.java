package fmi.ethnowear.persistence.jpa.projection.document;

import fmi.ethnowear.domain.model.document.DocumentPageRenditionType;

public interface DocumentPagePreviewMediaProjection {

    Long getDocumentPageId();

    Long getMediaAssetId();

    DocumentPageRenditionType getRenditionType();

    boolean isPreferredOcrInput();

    Integer getDisplayOrder();
}

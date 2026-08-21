package fmi.ethnowear.application.dto.worker.rendition;

import fmi.ethnowear.domain.model.document.DocumentPageRenditionType;

public enum WorkerPageRenditionType {

    PDF_PAGE_RENDER;

    public DocumentPageRenditionType toDomainType() {
        return DocumentPageRenditionType.valueOf(name());
    }
}
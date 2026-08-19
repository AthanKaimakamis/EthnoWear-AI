package fmi.ethnowear.domain.model.document.processing;

public enum JobType {
    PAGE_EXTRACTION,
    OCR,
    OCR_QUALITY_ASSESSMENT,
    CHUNK_GENERATION,
    INDEX_CHUNK,
    REINDEX_DOCUMENT,
    REMOVE_VECTOR,
    GENERATE_THUMBNAIL
}

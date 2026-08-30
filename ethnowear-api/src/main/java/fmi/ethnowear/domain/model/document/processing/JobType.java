package fmi.ethnowear.domain.model.document.processing;

public enum JobType {
    PAGE_EXTRACTION,
    OCR,
    EXTRACT_PAGE_FIGURES,
    OCR_QUALITY_ASSESSMENT,
    VISION_OCR_ASSESSMENT,
    CHUNK_GENERATION,
    INDEX_CHUNK,
    REINDEX_DOCUMENT,
    REMOVE_VECTOR,
    GENERATE_THUMBNAIL,
    MEDIA_CLEANUP
}

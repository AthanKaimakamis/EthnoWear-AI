package fmi.ethnowear.application.dto.worker.job;

import fmi.ethnowear.domain.model.document.processing.JobType;

public enum WorkerJobType {

    PAGE_EXTRACTION,
    OCR,
    EXTRACT_PAGE_FIGURES,
    OCR_QUALITY_ASSESSMENT,
    VISION_OCR_ASSESSMENT,
    INDEX_CHUNK;

    public JobType toDomainType() {
        return JobType.valueOf(name());
    }
}

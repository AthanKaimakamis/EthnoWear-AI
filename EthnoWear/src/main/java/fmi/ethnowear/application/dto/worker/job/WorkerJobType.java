package fmi.ethnowear.application.dto.worker.job;

import fmi.ethnowear.domain.model.document.processing.JobType;

public enum WorkerJobType {

    PAGE_EXTRACTION;

    public JobType toDomainType() {
        return JobType.valueOf(name());
    }
}

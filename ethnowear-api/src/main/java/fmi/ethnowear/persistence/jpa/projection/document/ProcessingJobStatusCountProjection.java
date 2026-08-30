package fmi.ethnowear.persistence.jpa.projection.document;

import fmi.ethnowear.domain.model.document.processing.JobStatus;

public interface ProcessingJobStatusCountProjection {

    JobStatus getStatus();

    long getTotal();
}

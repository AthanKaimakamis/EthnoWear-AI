package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingJobCapabilitiesPolicyTest {

    private final ProcessingJobCapabilitiesPolicy policy =
            new ProcessingJobCapabilitiesPolicy();

    @Test
    void exposesRetryAndReplacementOnlyForEligibleTerminalOcrJobs() {
        DocumentProcessingJob job = job(JobType.OCR, JobStatus.FAILED);

        var capabilities = policy.capabilities(job);

        assertTrue(capabilities.retryable());
        assertTrue(capabilities.cloneable());
        assertFalse(capabilities.cancellable());
        assertTrue(capabilities.deletable());
    }

    @Test
    void neverRetriesSucceededJobsAndDisablesRetiredJobs() {
        DocumentProcessingJob succeeded = job(JobType.OCR, JobStatus.SUCCEEDED);
        assertFalse(policy.capabilities(succeeded).retryable());
        assertTrue(policy.capabilities(succeeded).cloneable());
        assertFalse(policy.capabilities(succeeded).deletable());

        DocumentProcessingJob retired = job(JobType.OCR, JobStatus.FAILED);
        retired.retire(LocalDateTime.now(), "admin", "Obsolete failed run");

        var retiredCapabilities = policy.capabilities(retired);
        assertFalse(retiredCapabilities.retryable());
        assertFalse(retiredCapabilities.cloneable());
        assertFalse(retiredCapabilities.cancellable());
        assertFalse(retiredCapabilities.deletable());
    }

    private DocumentProcessingJob job(JobType type, JobStatus status) {
        DocumentProcessingJob job = new DocumentProcessingJob();
        job.setJobType(type);
        job.setStatus(status);
        return job;
    }
}

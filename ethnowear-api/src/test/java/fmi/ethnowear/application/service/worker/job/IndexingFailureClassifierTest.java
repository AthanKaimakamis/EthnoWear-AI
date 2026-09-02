package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.failure.WorkerJobFailureCommand;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndexingFailureClassifierTest {

    private final IndexingFailureClassifier classifier = new IndexingFailureClassifier();

    @Test
    void classifiesStableIndexingFailuresWithoutWorkerDiagnostics() {
        assertClassification("VECTOR_CONTRACT_INVALID", "Rejected response", "INDEXING_CONTRACT_MISMATCH");
        assertClassification("CONTENT_HASH_MISMATCH", "Stale chunk", "INDEXING_STALE_CONTENT");
        assertClassification("CHUNK_NOT_ELIGIBLE", "Approval changed", "INDEXING_ELIGIBILITY_CHANGED");
        assertClassification("WORKER_INTERNAL_ERROR", "The worker API rejected the indexing operation", "INDEXING_INFRASTRUCTURE_FAILURE");
    }

    @Test
    void preservesNonIndexingFailureContract() {
        DocumentProcessingJob job = new DocumentProcessingJob();
        job.setJobType(JobType.OCR);
        WorkerJobFailureCommand command = new WorkerJobFailureCommand(
                "OCR_FAILED",
                "OCR failed safely",
                true
        );

        assertThat(classifier.normalize(job, command)).isSameAs(command);
    }

    private void assertClassification(String workerCode, String workerMessage, String expectedCode) {
        DocumentProcessingJob job = new DocumentProcessingJob();
        job.setJobType(JobType.INDEX_CHUNK);

        WorkerJobFailureCommand result = classifier.normalize(
                job,
                new WorkerJobFailureCommand(workerCode, workerMessage, true)
        );

        assertThat(result.errorCode()).isEqualTo(expectedCode);
        assertThat(result.safeErrorMessage())
                .doesNotContain(workerCode)
                .doesNotContain(workerMessage);
        assertThat(result.retryable()).isTrue();
    }
}

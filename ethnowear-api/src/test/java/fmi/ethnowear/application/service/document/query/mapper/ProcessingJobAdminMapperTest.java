package fmi.ethnowear.application.service.document.query.mapper;

import fmi.ethnowear.application.dto.document.query.processing.ProcessingJobResultDetails;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJobAttempt;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProcessingJobAdminMapperTest {

    @Test
    void exposesStructuredTargetAndSafeAttemptHistory() {
        Document document = new Document();
        EntityTestUtils.setId(document, 3L);
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 4L);
        page.setDocument(document);

        DocumentProcessingJob job = new DocumentProcessingJob();
        EntityTestUtils.setId(job, 5L);
        DocumentProcessingJob previousJob = new DocumentProcessingJob();
        EntityTestUtils.setId(previousJob, 2L);
        job.linkPreviousJob(previousJob);
        job.setJobType(JobType.OCR);
        job.setStatus(JobStatus.FAILED);
        job.setDocument(document);
        job.setDocumentPage(page);
        job.setAttemptCount(1);
        job.setMaxAttempts(3);

        DocumentProcessingJobAttempt attempt =
                new DocumentProcessingJobAttempt();
        EntityTestUtils.setId(attempt, 6L);
        attempt.setProcessingJob(job);
        attempt.setExecutionNumber(2);
        attempt.setAttemptNumber(1);
        attempt.setStatus(JobStatus.FAILED);
        attempt.setClaimedAt(LocalDateTime.parse("2026-08-24T01:00:00"));
        attempt.setErrorCode("OCR_FAILED");
        attempt.setSafeErrorMessage("OCR failed safely");

        var result = new ProcessingJobAdminMapper().toDetails(
                job,
                List.of(attempt),
                new ProcessingJobResultDetails(List.of(8L), 9L, null)
        );

        assertEquals("DOCUMENT_PAGE", result.target().type());
        assertEquals(2L, result.previousJobId());
        assertEquals(4L, result.target().documentPageId());
        assertEquals(2, result.attempts().getFirst().executionNumber());
        assertEquals("OCR_FAILED", result.attempts().getFirst().error().code());
        assertEquals(9L, result.result().ocrResultId());
    }
}

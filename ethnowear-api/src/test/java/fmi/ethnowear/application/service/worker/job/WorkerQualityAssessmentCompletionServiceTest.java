package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.completion.WorkerJobCompletionDetails;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobKeyFactory;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.quality.AssessmentType;
import fmi.ethnowear.domain.model.document.quality.QualityStatus;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.*;
import fmi.ethnowear.persistence.jpa.repository.document.*;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import fmi.ethnowear.domain.model.document.quality.QualitySignalSeverity;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.WorkerTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkerQualityAssessmentCompletionServiceTest {

    @Test
    void selectsAssessmentWithoutAutomaticallyQueueingVisionReview() {
        Document document = document(7L);
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 21L);
        page.setDocument(document);
        page.setReviewState(ReviewState.APPROVED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.APPROVED);

        MediaAsset input = new MediaAsset();
        EntityTestUtils.setId(input, 31L);
        input.setMediaType(MediaType.IMAGE);

        DocumentPageMedia pageMedia = new DocumentPageMedia();
        EntityTestUtils.setId(pageMedia, 41L);
        pageMedia.setDocumentPage(page);
        pageMedia.setMediaAsset(input);

        DocumentPageOcrResult ocrResult = new DocumentPageOcrResult();
        EntityTestUtils.setId(ocrResult, 51L);
        ocrResult.setDocumentPage(page);
        ocrResult.setDocumentPageMedia(pageMedia);
        ocrResult.setCurrent(true);

        DocumentProcessingJob job = activePageExtractionJob(11L, document);
        job.setJobType(JobType.OCR_QUALITY_ASSESSMENT);
        job.setDocumentPage(page);
        job.setInputMediaAsset(input);
        job.assignJobKey("OCR_QUALITY_ASSESSMENT:OCR_RESULT:51");

        DocumentPageQualityAssessment accepted = assessment(
                61L,
                page,
                pageMedia,
                ocrResult,
                job,
                false
        );
        DocumentPageQualityAssessment previous = assessment(
                60L,
                page,
                pageMedia,
                ocrResult,
                null,
                true
        );
        accepted.setQualityStatus(QualityStatus.POOR_QUALITY);

        WorkerJobCompletionService service = service(job, accepted, previous);
        WorkerJobCompletionDetails completed = service.complete(11L, credentials());
        WorkerJobCompletionDetails repeated = service.complete(11L, credentials());

        assertEquals(JobStatus.SUCCEEDED, job.getStatus());
        assertTrue(accepted.isCurrent());
        assertFalse(previous.isCurrent());
        assertEquals(ReviewState.REVIEW_REQUIRED, page.getReviewState());
        assertEquals(TranscriptionApprovalState.PENDING, page.getTranscriptionApprovalState());
        assertEquals(IndexingState.NOT_ELIGIBLE, page.getIndexingState());
        assertEquals(0, completed.queuedVisionAssessmentJobs());
        assertFalse(completed.existing());
        assertTrue(repeated.existing());
    }

    private WorkerJobCompletionService service(
            DocumentProcessingJob job,
            DocumentPageQualityAssessment accepted,
            DocumentPageQualityAssessment previous
    ) {
        DocumentProcessingJobRepository jobs = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByIdForUpdate" -> Optional.of(job);
                    case "saveAndFlush" -> arguments[0];
                    default -> throw new AssertionError("Unexpected job call: " + method.getName());
                }
        );
        DocumentPageRepository pages = proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("save"))
                        return arguments[0];

                    throw new AssertionError("Unexpected page call: " + method.getName());
                }
        );
        DocumentPageQualityAssessmentRepository assessments = proxy(
                DocumentPageQualityAssessmentRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByProcessingJob_Id" -> Optional.of(accepted);
                    case "findByDocumentPage_IdAndDocumentPageMedia_IdAndAssessmentTypeAndCurrentTrue" ->
                            previous.isCurrent() ? Optional.of(previous) : Optional.empty();
                    case "saveAndFlush" -> arguments[0];
                    default -> throw new AssertionError(
                            "Unexpected assessment call: " + method.getName()
                    );
                }
        );
        DocumentPageQualitySignalRepository signals = proxy(
                DocumentPageQualitySignalRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("findByAssessment_IdOrderBySignalOrdinalAscIdAsc")) {
                        DocumentPageQualitySignal signal = new DocumentPageQualitySignal();
                        signal.setSeverity(QualitySignalSeverity.INFO);
                        return List.of(signal);
                    }

                    throw new AssertionError("Unexpected signal call: " + method.getName());
                }
        );

        return new WorkerJobCompletionService(
                new fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader(
                        jobs,
                        new fmi.ethnowear.application.service.worker.security.WorkerClaimValidator(),
                        Clock.fixed(NOW, ZoneOffset.UTC)
                ),
                jobs,
                pages,
                null,
                null,
                assessments,
                signals,
                null,
                null,
                null,
                null,
                org.mockito.Mockito.mock(
                        fmi.ethnowear.application.service.document.processing.DocumentProcessingStateReconciler.class
                ),
                new DocumentProcessingJobKeyFactory(),
                org.mockito.Mockito.mock(WorkerIndexingService.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                fmi.ethnowear.support.ManagementEventTestSupport.events(),
                org.mockito.Mockito.mock(fmi.ethnowear.application.service.document.figure.DocumentPageFigureLifecycleService.class),
                org.mockito.Mockito.mock(fmi.ethnowear.application.service.document.figure.FigureExtractionSchedulingService.class)
        );
    }

    private DocumentPageQualityAssessment assessment(
            long id,
            DocumentPage page,
            DocumentPageMedia pageMedia,
            DocumentPageOcrResult ocrResult,
            DocumentProcessingJob job,
            boolean current
    ) {
        DocumentPageQualityAssessment assessment = new DocumentPageQualityAssessment();
        EntityTestUtils.setId(assessment, id);
        assessment.setDocumentPage(page);
        assessment.setDocumentPageMedia(pageMedia);
        assessment.setDocumentPageOcrResult(ocrResult);
        assessment.setProcessingJob(job);
        assessment.setAssessmentType(AssessmentType.COMBINED_OCR_QUALITY);
        assessment.setCurrent(current);
        return assessment;
    }
}

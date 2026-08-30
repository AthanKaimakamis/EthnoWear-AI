package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobKeyFactory;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobScheduler;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.quality.AssessmentType;
import fmi.ethnowear.domain.model.document.quality.AssessorType;
import fmi.ethnowear.domain.model.document.quality.QualitySignalSeverity;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.*;
import fmi.ethnowear.persistence.jpa.repository.document.*;
import fmi.ethnowear.testutil.EntityTestUtils;
import fmi.ethnowear.util.ContentHashUtils;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static fmi.ethnowear.support.WorkerTestFixtures.credentials;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkerVisionOcrCompletionServiceTest {

    @Test
    void completesAdvisoryVisionResultWithoutAcceptedCorrection() {
        Document document = new Document();
        EntityTestUtils.setId(document, 7L);
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 21L);
        page.setDocument(document);
        page.setReviewState(ReviewState.APPROVED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.APPROVED);
        page.setIndexingState(IndexingState.INDEXED);
        MediaAsset input = new MediaAsset();
        EntityTestUtils.setId(input, 31L);
        input.setMediaType(MediaType.IMAGE);
        DocumentPageMedia pageMedia = new DocumentPageMedia();
        EntityTestUtils.setId(pageMedia, 41L);
        pageMedia.setDocumentPage(page);
        pageMedia.setMediaAsset(input);
        pageMedia.setPreferredOcrInput(true);
        DocumentPageOcrResult ocr = new DocumentPageOcrResult();
        EntityTestUtils.setId(ocr, 51L);
        ocr.setDocumentPage(page);
        ocr.setDocumentPageMedia(pageMedia);
        ocr.setRawText("Raw OCR text");
        ocr.setCurrent(true);

        DocumentProcessingJob job = new DocumentProcessingJob();
        EntityTestUtils.setId(job, 11L);
        job.setJobType(JobType.VISION_OCR_ASSESSMENT);
        job.setStatus(JobStatus.RUNNING);
        job.setDocument(document);
        job.setDocumentPage(page);
        job.setInputMediaAsset(input);
        job.assignJobKey(
                "VISION_OCR_ASSESSMENT:OCR_RESULT:51:QUALITY_ASSESSMENT:62"
        );

        DocumentPageQualityAssessment deterministic =
                new DocumentPageQualityAssessment();
        EntityTestUtils.setId(deterministic, 62L);
        deterministic.setDocumentPage(page);
        deterministic.setDocumentPageMedia(pageMedia);
        deterministic.setDocumentPageOcrResult(ocr);
        deterministic.setAssessmentType(AssessmentType.COMBINED_OCR_QUALITY);
        deterministic.setCurrent(true);

        DocumentPageQualityAssessment assessment =
                new DocumentPageQualityAssessment();
        EntityTestUtils.setId(assessment, 61L);
        assessment.setDocumentPage(page);
        assessment.setDocumentPageMedia(pageMedia);
        assessment.setDocumentPageOcrResult(ocr);
        assessment.setProcessingJob(job);
        assessment.setAssessmentType(AssessmentType.VISION_TEXT_COMPARISON);
        assessment.setAssessorType(AssessorType.VISION_MODEL);

        DocumentPageTextSuggestion suggestion = new DocumentPageTextSuggestion();
        EntityTestUtils.setId(suggestion, 71L);
        suggestion.setDocumentPage(page);
        suggestion.setDocumentPageMedia(pageMedia);
        suggestion.setDocumentPageOcrResult(ocr);
        suggestion.setProcessingJob(job);
        suggestion.setSuggestedText("Raw OCR text");
        suggestion.setSuggestedTextHash(ContentHashUtils.sha256("Raw OCR text"));

        WorkerClaimedJobLoader loader = mock(WorkerClaimedJobLoader.class);
        when(loader.loadForUpdate(11L)).thenReturn(job);
        DocumentProcessingJobRepository jobs = mock(
                DocumentProcessingJobRepository.class
        );
        when(jobs.saveAndFlush(any())).thenAnswer(value -> value.getArgument(0));
        DocumentPageOcrResultRepository ocrResults = mock(
                DocumentPageOcrResultRepository.class
        );
        when(ocrResults.findByDocumentPage_IdAndCurrentTrue(21L))
                .thenReturn(Optional.of(ocr));
        DocumentPageQualityAssessmentRepository assessments = mock(
                DocumentPageQualityAssessmentRepository.class
        );
        when(assessments.findByProcessingJob_Id(11L))
                .thenReturn(Optional.of(assessment));
        when(assessments
                .findByDocumentPage_IdAndDocumentPageMedia_IdAndAssessmentTypeAndCurrentTrue(
                        21L,
                        41L,
                        AssessmentType.VISION_TEXT_COMPARISON
                )).thenReturn(Optional.empty());
        when(assessments
                .findByDocumentPage_IdAndDocumentPageMedia_IdAndAssessmentTypeAndCurrentTrue(
                        21L,
                        41L,
                        AssessmentType.COMBINED_OCR_QUALITY
                )).thenReturn(Optional.of(deterministic));
        when(assessments.saveAndFlush(any())).thenAnswer(value -> value.getArgument(0));
        DocumentPageQualitySignal signal = new DocumentPageQualitySignal();
        signal.setSeverity(QualitySignalSeverity.WARNING);
        DocumentPageQualitySignalRepository signals = mock(
                DocumentPageQualitySignalRepository.class
        );
        when(signals.findByAssessment_IdOrderBySignalOrdinalAscIdAsc(61L))
                .thenReturn(List.of(signal));
        DocumentPageTextSuggestionRepository suggestions = mock(
                DocumentPageTextSuggestionRepository.class
        );
        when(suggestions.findByProcessingJob_Id(11L))
                .thenReturn(Optional.of(suggestion));

        WorkerJobCompletionService service = new WorkerJobCompletionService(
                loader,
                jobs,
                mock(DocumentPageRepository.class),
                mock(DocumentPageMediaRepository.class),
                ocrResults,
                assessments,
                signals,
                suggestions,
                mock(fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureRepository.class),
                mock(DocumentRepository.class),
                mock(DocumentProcessingJobScheduler.class),
                mock(fmi.ethnowear.application.service.document.processing.DocumentProcessingStateReconciler.class),
                new DocumentProcessingJobKeyFactory(),
                mock(WorkerIndexingService.class),
                Clock.fixed(Instant.parse("2026-08-26T08:00:00Z"), ZoneOffset.UTC),
                mock(ManagementEventPublisher.class),
                mock(fmi.ethnowear.application.service.document.figure.DocumentPageFigureLifecycleService.class),
                mock(fmi.ethnowear.application.service.document.figure.FigureExtractionSchedulingService.class)
        );

        var completed = service.complete(11L, credentials());
        var repeated = service.complete(11L, credentials());

        assertFalse(completed.existing());
        assertTrue(repeated.existing());
        assertEquals(JobStatus.SUCCEEDED, job.getStatus());
        assertTrue(assessment.isCurrent());
        assertEquals(ReviewState.APPROVED, page.getReviewState());
        assertEquals(
                TranscriptionApprovalState.APPROVED,
                page.getTranscriptionApprovalState()
        );
        assertEquals(IndexingState.INDEXED, page.getIndexingState());
        verify(loader, times(1)).validateActive(job, credentials());
    }
}

package fmi.ethnowear.application.service.document.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.document.command.review.TextSuggestionIssueApplyCommand;
import fmi.ethnowear.application.dto.document.command.review.TextSuggestionApplyCommand;
import fmi.ethnowear.application.dto.worker.vision.WorkerVisionIssueCommand;
import fmi.ethnowear.application.exception.InvalidDocumentPageReviewTransitionException;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.quality.AssessmentType;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.document.*;
import fmi.ethnowear.persistence.jpa.repository.document.*;
import fmi.ethnowear.testutil.EntityTestUtils;
import fmi.ethnowear.util.ContentHashUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentPageTextSuggestionReviewServiceTest {

    @Test
    void appliesOneExactIssueWithoutApprovingThePage() throws Exception {
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 21L);
        page.setProcessingState(ProcessingState.COMPLETED);
        page.setReviewState(ReviewState.REVIEW_REQUIRED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);
        page.setCorrectedText("Raw OCR text");
        page.setCorrectedTextHash(ContentHashUtils.sha256("Raw OCR text"));

        DocumentPageOcrResult ocr = new DocumentPageOcrResult();
        EntityTestUtils.setId(ocr, 31L);
        ocr.setDocumentPage(page);
        ocr.setRawText("Raw OCR text");
        ocr.setCurrent(true);

        DocumentProcessingJob job = new DocumentProcessingJob();
        EntityTestUtils.setId(job, 41L);
        job.setStatus(JobStatus.SUCCEEDED);

        DocumentPageTextSuggestion suggestion = new DocumentPageTextSuggestion();
        EntityTestUtils.setId(suggestion, 51L);
        suggestion.setDocumentPage(page);
        suggestion.setDocumentPageOcrResult(ocr);
        suggestion.setProcessingJob(job);
        suggestion.setSuggestedText("Raw ОСР text");
        suggestion.setSuggestedTextHash(ContentHashUtils.sha256("Raw ОСР text"));
        suggestion.setIssuesJson(new ObjectMapper().writeValueAsString(List.of(
                new WorkerVisionIssueCommand(
                        "OCR_WORD",
                        "Разпознатата дума е неточна",
                        new BigDecimal("0.9500"),
                        "OCR",
                        "Raw OCR text",
                        "ОСР",
                        "Raw ОСР text",
                        4,
                        7,
                        true
                )
        )));

        DocumentPageQualityAssessment assessment =
                new DocumentPageQualityAssessment();
        assessment.setAssessmentType(AssessmentType.VISION_TEXT_COMPARISON);
        assessment.setCurrent(true);

        DocumentPageRepository pages = mock(DocumentPageRepository.class);
        when(pages.findByIdForUpdate(21L)).thenReturn(Optional.of(page));
        when(pages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DocumentPageOcrResultRepository ocrResults = mock(
                DocumentPageOcrResultRepository.class
        );
        when(ocrResults.findByDocumentPage_IdAndCurrentTrue(21L))
                .thenReturn(Optional.of(ocr));
        DocumentPageTextSuggestionRepository suggestions = mock(
                DocumentPageTextSuggestionRepository.class
        );
        when(suggestions.findById(51L)).thenReturn(Optional.of(suggestion));
        when(suggestions
                .findFirstByDocumentPage_IdAndDocumentPageOcrResult_IdAndProcessingJob_StatusOrderByCreatedAtDescIdDesc(
                        21L,
                        31L,
                        JobStatus.SUCCEEDED
                )).thenReturn(Optional.of(suggestion));
        DocumentPageQualityAssessmentRepository assessments = mock(
                DocumentPageQualityAssessmentRepository.class
        );
        when(assessments.findByProcessingJob_Id(41L))
                .thenReturn(Optional.of(assessment));
        DocumentPageReviewRepository reviews = mock(
                DocumentPageReviewRepository.class
        );
        when(reviews
                .findBySourceTextSuggestion_IdAndSourceTextSuggestionIssueOrdinal(
                        51L,
                        0
                )).thenReturn(Optional.empty());
        AtomicReference<DocumentPageReview> savedReview = new AtomicReference<>();
        when(reviews.saveAndFlush(any())).thenAnswer(invocation -> {
            DocumentPageReview review = invocation.getArgument(0);
            EntityTestUtils.setId(review, 61L);
            savedReview.set(review);
            return review;
        });
        DocumentPageChunkInvalidator invalidator = mock(
                DocumentPageChunkInvalidator.class
        );
        DocumentPageReviewService service = new DocumentPageReviewService(
                pages,
                ocrResults,
                reviews,
                assessments,
                suggestions,
                invalidator,
                mock(fmi.ethnowear.application.service.document.chunk.DocumentChunkGenerationEligibilityService.class),
                mock(fmi.ethnowear.application.service.document.chunk.DocumentChunkGenerationService.class),
                new DocumentHistoryMapper(),
                mock(ManagementEventPublisher.class)
        );

        var result = service.applyTextSuggestionIssue(
                21L,
                51L,
                0,
                new TextSuggestionIssueApplyCommand(
                        true,
                        ContentHashUtils.sha256("Raw OCR text")
                ),
                "editor"
        );

        assertEquals("Raw ОСР text", page.getCorrectedText());
        assertEquals(ReviewState.REVIEW_REQUIRED, page.getReviewState());
        assertEquals(TranscriptionApprovalState.PENDING,
                page.getTranscriptionApprovalState());
        assertEquals(0, result.sourceTextSuggestionIssueOrdinal());
        assertEquals(0, savedReview.get().getSourceTextSuggestionIssueOrdinal());
        verify(invalidator).invalidate(page);
    }

    @Test
    void appliesCurrentSuggestionRevokesApprovalAndIsIdempotent() {
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 21L);
        page.setProcessingState(ProcessingState.COMPLETED);
        page.setReviewState(ReviewState.APPROVED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.APPROVED);
        page.setCorrectedText("Old corrected text");
        page.setCorrectedTextHash(ContentHashUtils.sha256("Old corrected text"));

        DocumentPageOcrResult ocr = new DocumentPageOcrResult();
        EntityTestUtils.setId(ocr, 31L);
        ocr.setDocumentPage(page);
        ocr.setCurrent(true);

        DocumentProcessingJob job = new DocumentProcessingJob();
        EntityTestUtils.setId(job, 41L);
        job.setStatus(JobStatus.SUCCEEDED);

        DocumentPageTextSuggestion suggestion = new DocumentPageTextSuggestion();
        EntityTestUtils.setId(suggestion, 51L);
        suggestion.setDocumentPage(page);
        suggestion.setDocumentPageOcrResult(ocr);
        suggestion.setProcessingJob(job);
        suggestion.setSuggestedText("Vision corrected text");
        suggestion.setSuggestedTextHash(
                ContentHashUtils.sha256("Vision corrected text")
        );

        DocumentPageQualityAssessment assessment =
                new DocumentPageQualityAssessment();
        assessment.setAssessmentType(AssessmentType.VISION_TEXT_COMPARISON);
        assessment.setCurrent(true);

        DocumentPageRepository pages = mock(DocumentPageRepository.class);
        when(pages.findByIdForUpdate(21L)).thenReturn(Optional.of(page));
        when(pages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentPageOcrResultRepository ocrResults = mock(
                DocumentPageOcrResultRepository.class
        );
        when(ocrResults.findByDocumentPage_IdAndCurrentTrue(21L))
                .thenReturn(Optional.of(ocr));

        DocumentPageTextSuggestionRepository suggestions = mock(
                DocumentPageTextSuggestionRepository.class
        );
        when(suggestions.findById(51L)).thenReturn(Optional.of(suggestion));
        when(suggestions
                .findFirstByDocumentPage_IdAndDocumentPageOcrResult_IdAndProcessingJob_StatusOrderByCreatedAtDescIdDesc(
                        21L,
                        31L,
                        JobStatus.SUCCEEDED
                )).thenReturn(Optional.of(suggestion));

        DocumentPageQualityAssessmentRepository assessments = mock(
                DocumentPageQualityAssessmentRepository.class
        );
        when(assessments.findByProcessingJob_Id(41L))
                .thenReturn(Optional.of(assessment));

        AtomicReference<DocumentPageReview> applied = new AtomicReference<>();
        DocumentPageReviewRepository reviews = mock(
                DocumentPageReviewRepository.class
        );
        when(reviews.findBySourceTextSuggestion_IdAndSourceTextSuggestionIssueOrdinalIsNull(51L)).thenAnswer(
                ignored -> Optional.ofNullable(applied.get())
        );
        when(reviews.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviews.saveAndFlush(any())).thenAnswer(invocation -> {
            DocumentPageReview value = invocation.getArgument(0);
            EntityTestUtils.setId(value, 61L);
            applied.set(value);
            return value;
        });

        DocumentPageChunkInvalidator invalidator = mock(
                DocumentPageChunkInvalidator.class
        );
        DocumentPageReviewService service = new DocumentPageReviewService(
                pages,
                ocrResults,
                reviews,
                assessments,
                suggestions,
                invalidator,
                mock(fmi.ethnowear.application.service.document.chunk.DocumentChunkGenerationEligibilityService.class),
                mock(fmi.ethnowear.application.service.document.chunk.DocumentChunkGenerationService.class),
                new DocumentHistoryMapper(),
                mock(ManagementEventPublisher.class)
        );

        var first = service.applyTextSuggestion(
                21L,
                51L,
                new TextSuggestionApplyCommand(true),
                "editor"
        );
        var repeated = service.applyTextSuggestion(
                21L,
                51L,
                new TextSuggestionApplyCommand(true),
                "editor"
        );

        assertEquals(51L, first.sourceTextSuggestionId());
        assertEquals(first.id(), repeated.id());
        assertEquals("Vision corrected text", page.getCorrectedText());
        assertEquals(ReviewState.REVIEW_REQUIRED, page.getReviewState());
        assertEquals(
                TranscriptionApprovalState.PENDING,
                page.getTranscriptionApprovalState()
        );
        assertSame(suggestion, applied.get().getSourceTextSuggestion());
        verify(invalidator, times(1)).invalidate(page);
    }

    @Test
    void rejectsSuggestionForStaleOcrResult() {
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 21L);
        DocumentPageOcrResult oldOcr = new DocumentPageOcrResult();
        EntityTestUtils.setId(oldOcr, 31L);
        DocumentPageOcrResult currentOcr = new DocumentPageOcrResult();
        EntityTestUtils.setId(currentOcr, 32L);
        DocumentProcessingJob job = new DocumentProcessingJob();
        job.setStatus(JobStatus.SUCCEEDED);
        DocumentPageTextSuggestion suggestion = new DocumentPageTextSuggestion();
        EntityTestUtils.setId(suggestion, 51L);
        suggestion.setDocumentPage(page);
        suggestion.setDocumentPageOcrResult(oldOcr);
        suggestion.setProcessingJob(job);
        suggestion.setSuggestedText("text");
        suggestion.setSuggestedTextHash(ContentHashUtils.sha256("text"));

        DocumentPageRepository pages = mock(DocumentPageRepository.class);
        when(pages.findByIdForUpdate(21L)).thenReturn(Optional.of(page));
        DocumentPageOcrResultRepository ocrResults = mock(
                DocumentPageOcrResultRepository.class
        );
        when(ocrResults.findByDocumentPage_IdAndCurrentTrue(21L))
                .thenReturn(Optional.of(currentOcr));
        DocumentPageTextSuggestionRepository suggestions = mock(
                DocumentPageTextSuggestionRepository.class
        );
        when(suggestions.findById(51L)).thenReturn(Optional.of(suggestion));

        DocumentPageReviewService service = new DocumentPageReviewService(
                pages,
                ocrResults,
                mock(DocumentPageReviewRepository.class),
                mock(DocumentPageQualityAssessmentRepository.class),
                suggestions,
                mock(DocumentPageChunkInvalidator.class),
                mock(fmi.ethnowear.application.service.document.chunk.DocumentChunkGenerationEligibilityService.class),
                mock(fmi.ethnowear.application.service.document.chunk.DocumentChunkGenerationService.class),
                new DocumentHistoryMapper(),
                mock(ManagementEventPublisher.class)
        );

        assertThrows(
                InvalidDocumentPageReviewTransitionException.class,
                () -> service.applyTextSuggestion(
                        21L,
                        51L,
                        new TextSuggestionApplyCommand(true),
                        "editor"
                )
        );
    }
}

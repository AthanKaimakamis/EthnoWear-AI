package fmi.ethnowear.application.service.worker.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.worker.quality.WorkerQualityAssessmentCommand;
import fmi.ethnowear.application.dto.worker.quality.WorkerQualitySignalCommand;
import fmi.ethnowear.application.dto.worker.quality.WorkerQualityStatus;
import fmi.ethnowear.application.dto.worker.vision.WorkerVisionAssessmentCommand;
import fmi.ethnowear.application.dto.worker.vision.WorkerVisionIssueCommand;
import fmi.ethnowear.application.dto.worker.vision.WorkerVisionUncertainPassageCommand;
import fmi.ethnowear.application.exception.WorkerManifestConflictException;
import fmi.ethnowear.application.exception.WorkerVisionAssessmentConflictException;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobKeyFactory;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.quality.AssessmentType;
import fmi.ethnowear.domain.model.document.quality.QualitySignalSeverity;
import fmi.ethnowear.domain.model.document.quality.QualityStatus;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.*;
import fmi.ethnowear.persistence.jpa.repository.document.*;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static fmi.ethnowear.support.WorkerTestFixtures.credentials;
import static fmi.ethnowear.support.WorkerTestFixtures.properties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkerVisionOcrAssessmentServiceTest {

    @Test
    void returnsContextAndAcceptsIdenticalRetry() {
        Fixture fixture = fixture();

        var context = fixture.service.context(11L, credentials());
        var accepted = fixture.service.accept(11L, credentials(), command("Suggested text"));
        var repeated = fixture.service.accept(11L, credentials(), command("Suggested text"));

        assertEquals(51L, context.ocrResultId());
        assertEquals(61L, context.deterministicAssessmentId());
        assertEquals(21L, context.documentPageId());
        assertEquals(41L, context.documentPageMediaId());
        assertEquals(TranscriptionApprovalState.PENDING, context.transcriptionApprovalState());
        assertEquals(ReviewState.REVIEW_REQUIRED, context.reviewState());
        assertEquals(IndexingState.NOT_ELIGIBLE, context.indexingState());
        assertEquals("Raw OCR text", context.rawOcrText());
        assertFalse(accepted.existing());
        assertTrue(repeated.existing());
        assertEquals("Suggested text", fixture.suggestion.get().getSuggestedText());
        assertEquals(64, fixture.suggestion.get().getSuggestedTextHash().length());
        assertTrue(fixture.suggestion.get().isRequiresReview());
        assertTrue(fixture.suggestion.get().getIssuesJson().contains("0.9000"));
        assertTrue(fixture.suggestion.get().getUncertainPassagesJson().contains(
                "Uncertain line"
        ));
        assertFalse(fixture.visionAssessment.get().isCurrent());
    }

    @Test
    void acceptsPartiallyRejectedResultAndPersistsRemainingGuidance() {
        Fixture fixture = fixture();
        WorkerVisionAssessmentCommand base = command("Raw ОСР text");
        WorkerVisionIssueCommand validRemainingIssue = new WorkerVisionIssueCommand(
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
        );
        WorkerVisionAssessmentCommand command = degradedCommand(
                base,
                "Raw ОСР text",
                List.of(validRemainingIssue),
                "issue_excerpt"
        );

        fixture.service.accept(11L, credentials(), command);

        assertTrue(fixture.suggestion.get().getIssuesJson().contains("OCR_WORD"));
        assertTrue(fixture.visionSignals.stream().anyMatch(signal ->
                signal.getSignalType().equals("VISION_ISSUES_REJECTED")
        ));
        assertTrue(fixture.visionSignals.stream().anyMatch(signal ->
                signal.getSignalType().equals("VISION_MODEL_DIAGNOSTICS")
        ));
    }

    @Test
    void acceptsFullyRejectedCorrectionAsAdvisoryAssessment() {
        Fixture fixture = fixture();
        WorkerVisionAssessmentCommand base = command("Raw OCR text");
        WorkerVisionAssessmentCommand command = degradedCommand(
                base,
                "Raw OCR text",
                List.of(),
                "number_grounding"
        );

        var accepted = fixture.service.accept(11L, credentials(), command);

        assertFalse(accepted.existing());
        assertEquals("Raw OCR text", fixture.suggestion.get().getSuggestedText());
        assertEquals("[]", fixture.suggestion.get().getIssuesJson());
        assertTrue(fixture.visionSignals.stream().anyMatch(signal ->
                signal.getSignalType().equals("VISION_SUGGESTION_REJECTED")
        ));
    }

    @Test
    void rejectsConflictingRetryAndStaleOcrResult() {
        Fixture fixture = fixture();
        fixture.service.accept(11L, credentials(), command("First suggestion"));

        assertThrows(
                WorkerVisionAssessmentConflictException.class,
                () -> fixture.service.accept(
                        11L,
                        credentials(),
                        command("Different suggestion")
                )
        );

        fixture.ocrResult.setCurrent(false);
        assertThrows(
                WorkerManifestConflictException.class,
                () -> fixture.service.context(11L, credentials())
        );
    }

    @Test
    void rejectsChangedDeterministicAssessment() {
        Fixture fixture = fixture();
        EntityTestUtils.setId(fixture.deterministicAssessment(), 62L);

        assertThrows(
                WorkerManifestConflictException.class,
                () -> fixture.service.context(11L, credentials())
        );
    }

    @Test
    void rejectsRenditionThatIsNoLongerPreferred() {
        Fixture fixture = fixture();
        fixture.ocrResult().getDocumentPageMedia().setPreferredOcrInput(false);

        assertThrows(
                fmi.ethnowear.application.exception.UnprocessableDocumentEvidenceException.class,
                () -> fixture.service.context(11L, credentials())
        );
    }

    @Test
    void requiresReviewWhenStructuredFindingsExist() {
        Fixture fixture = fixture();
        WorkerVisionAssessmentCommand valid = command("Suggested text");
        WorkerVisionAssessmentCommand invalid = new WorkerVisionAssessmentCommand(
                valid.assessment(),
                false,
                valid.suggestedText(),
                valid.modelName(),
                valid.modelVersion(),
                valid.promptVersion(),
                valid.issues(),
                valid.uncertainPassages()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.accept(11L, credentials(), invalid)
        );
    }

    @Test
    void rejectsInvalidConfidenceAndConflictingStructuredRetry() {
        Fixture fixture = fixture();
        WorkerVisionAssessmentCommand valid = command("Suggested text");
        fixture.service.accept(11L, credentials(), valid);

        WorkerVisionAssessmentCommand conflicting = new WorkerVisionAssessmentCommand(
                valid.assessment(),
                true,
                valid.suggestedText(),
                valid.modelName(),
                valid.modelVersion(),
                valid.promptVersion(),
                valid.issues(),
                List.of(new WorkerVisionUncertainPassageCommand(
                        "Different passage",
                        "Low contrast",
                        new BigDecimal("0.6000")
                ))
        );
        assertThrows(
                WorkerVisionAssessmentConflictException.class,
                () -> fixture.service.accept(11L, credentials(), conflicting)
        );

        WorkerVisionAssessmentCommand invalidConfidence = new WorkerVisionAssessmentCommand(
                valid.assessment(),
                true,
                valid.suggestedText(),
                valid.modelName(),
                valid.modelVersion(),
                valid.promptVersion(),
                List.of(new WorkerVisionIssueCommand(
                        "OCR_WORD",
                        "Raw",
                        "Suggested",
                        "Possible OCR correction",
                        new BigDecimal("1.00001")
                )),
                List.of()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.accept(
                        11L,
                        credentials(),
                        invalidConfidence
                )
        );
    }

    @Test
    void acceptsExactActionableIssueAndRejectsMismatchedOffsets() {
        Fixture fixture = fixture();
        WorkerVisionAssessmentCommand base = command("Suggested text");
        WorkerVisionIssueCommand actionable = new WorkerVisionIssueCommand(
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
        );
        WorkerVisionAssessmentCommand valid = new WorkerVisionAssessmentCommand(
                base.assessment(),
                true,
                base.suggestedText(),
                base.modelName(),
                base.modelVersion(),
                base.promptVersion(),
                List.of(actionable),
                List.of()
        );

        fixture.service.accept(11L, credentials(), valid);
        assertTrue(fixture.suggestion.get().getIssuesJson().contains(
                "safelyApplicable"
        ));

        Fixture invalidFixture = fixture();
        WorkerVisionIssueCommand mismatched = new WorkerVisionIssueCommand(
                "OCR_WORD",
                "Неточно съвпадение",
                new BigDecimal("0.9000"),
                "OCR",
                null,
                "ОСР",
                null,
                0,
                3,
                true
        );
        WorkerVisionAssessmentCommand invalid = new WorkerVisionAssessmentCommand(
                base.assessment(),
                true,
                base.suggestedText(),
                base.modelName(),
                base.modelVersion(),
                base.promptVersion(),
                List.of(mismatched),
                List.of()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> invalidFixture.service.accept(
                        11L,
                        credentials(),
                        invalid
                )
        );
    }

    @Test
    void enforcesStructuredCollectionAndTextLimits() {
        Fixture fixture = fixture();
        WorkerVisionAssessmentCommand valid = command("Suggested text");
        List<WorkerVisionIssueCommand> tooManyIssues = java.util.stream.IntStream
                .range(0, 101)
                .mapToObj(index -> valid.issues().getFirst())
                .toList();

        WorkerVisionAssessmentCommand excessiveIssues =
                new WorkerVisionAssessmentCommand(
                        valid.assessment(),
                        true,
                        valid.suggestedText(),
                        valid.modelName(),
                        valid.modelVersion(),
                        valid.promptVersion(),
                        tooManyIssues,
                        List.of()
                );
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.accept(
                        11L,
                        credentials(),
                        excessiveIssues
                )
        );

        WorkerVisionAssessmentCommand excessiveExcerpt =
                new WorkerVisionAssessmentCommand(
                        valid.assessment(),
                        true,
                        valid.suggestedText(),
                        valid.modelName(),
                        valid.modelVersion(),
                        valid.promptVersion(),
                        List.of(),
                        List.of(new WorkerVisionUncertainPassageCommand(
                                "x".repeat(501),
                                "Low contrast",
                                new BigDecimal("0.5000")
                        ))
                );
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.accept(
                        11L,
                        credentials(),
                        excessiveExcerpt
                )
        );
    }

    private Fixture fixture() {
        Document document = new Document();
        EntityTestUtils.setId(document, 7L);
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 21L);
        page.setDocument(document);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);
        page.setReviewState(ReviewState.REVIEW_REQUIRED);
        page.setIndexingState(IndexingState.NOT_ELIGIBLE);

        MediaAsset input = new MediaAsset();
        EntityTestUtils.setId(input, 31L);
        input.setMediaType(MediaType.IMAGE);
        input.setMimeType("image/png");
        input.setSizeBytes(2048L);
        input.setWidth(1200);
        input.setHeight(1800);

        DocumentPageMedia pageMedia = new DocumentPageMedia();
        EntityTestUtils.setId(pageMedia, 41L);
        pageMedia.setDocumentPage(page);
        pageMedia.setMediaAsset(input);
        pageMedia.setWidth(1200);
        pageMedia.setHeight(1800);
        pageMedia.setDpi(300);
        pageMedia.setPreferredOcrInput(true);

        DocumentPageOcrResult ocrResult = new DocumentPageOcrResult();
        EntityTestUtils.setId(ocrResult, 51L);
        ocrResult.setDocumentPage(page);
        ocrResult.setDocumentPageMedia(pageMedia);
        ocrResult.setRawText("Raw OCR text");
        ocrResult.setOcrLanguage("bul");
        ocrResult.setOcrConfidence(new BigDecimal("0.7500"));
        ocrResult.setStructuredOutputJson("{\"words\":[]}");
        ocrResult.setCurrent(true);

        DocumentProcessingJob job = new DocumentProcessingJob();
        EntityTestUtils.setId(job, 11L);
        job.setJobType(JobType.VISION_OCR_ASSESSMENT);
        job.setStatus(fmi.ethnowear.domain.model.document.processing.JobStatus.RUNNING);
        job.setDocument(document);
        job.setDocumentPage(page);
        job.setInputMediaAsset(input);
        job.assignJobKey(
                "VISION_OCR_ASSESSMENT:OCR_RESULT:51:QUALITY_ASSESSMENT:61"
        );

        DocumentPageQualityAssessment deterministic = new DocumentPageQualityAssessment();
        EntityTestUtils.setId(deterministic, 61L);
        deterministic.setDocumentPage(page);
        deterministic.setDocumentPageMedia(pageMedia);
        deterministic.setDocumentPageOcrResult(ocrResult);
        deterministic.setAssessmentType(AssessmentType.COMBINED_OCR_QUALITY);
        deterministic.setQualityStatus(QualityStatus.REVIEW_REQUIRED);
        deterministic.setOverallScore(new BigDecimal("0.5000"));
        deterministic.setCurrent(true);

        WorkerClaimedJobLoader loader = mock(WorkerClaimedJobLoader.class);
        when(loader.requireActive(11L, credentials())).thenReturn(job);

        DocumentPageOcrResultRepository ocrResults = mock(
                DocumentPageOcrResultRepository.class
        );
        when(ocrResults.findById(51L)).thenReturn(Optional.of(ocrResult));

        AtomicReference<DocumentPageQualityAssessment> visionAssessment =
                new AtomicReference<>();
        DocumentPageQualityAssessmentRepository assessments = mock(
                DocumentPageQualityAssessmentRepository.class
        );
        when(assessments
                .findByDocumentPage_IdAndDocumentPageMedia_IdAndAssessmentTypeAndCurrentTrue(
                        21L,
                        41L,
                        AssessmentType.COMBINED_OCR_QUALITY
                )).thenReturn(Optional.of(deterministic));
        when(assessments.findByProcessingJob_Id(11L)).thenAnswer(
                ignored -> Optional.ofNullable(visionAssessment.get())
        );
        when(assessments.saveAndFlush(any())).thenAnswer(invocation -> {
            DocumentPageQualityAssessment value = invocation.getArgument(0);
            EntityTestUtils.setId(value, 71L);
            visionAssessment.set(value);
            return value;
        });

        List<DocumentPageQualitySignal> deterministicSignals = List.of(
                signal(deterministic, 1, "OCR_LAYOUT_WARNINGS")
        );
        List<DocumentPageQualitySignal> visionSignals = new ArrayList<>();
        DocumentPageQualitySignalRepository signals = mock(
                DocumentPageQualitySignalRepository.class
        );
        when(signals.findByAssessment_IdOrderBySignalOrdinalAscIdAsc(61L))
                .thenReturn(deterministicSignals);
        when(signals.findByAssessment_IdOrderBySignalOrdinalAscIdAsc(71L))
                .thenAnswer(ignored -> List.copyOf(visionSignals));
        when(signals.saveAllAndFlush(any())).thenAnswer(invocation -> {
            visionSignals.clear();
            ((Iterable<?>) invocation.getArgument(0)).forEach(value ->
                    visionSignals.add((DocumentPageQualitySignal) value)
            );
            return invocation.getArgument(0);
        });

        AtomicReference<DocumentPageTextSuggestion> suggestion =
                new AtomicReference<>();
        DocumentPageTextSuggestionRepository suggestions = mock(
                DocumentPageTextSuggestionRepository.class
        );
        when(suggestions.findByProcessingJob_Id(11L)).thenAnswer(
                ignored -> Optional.ofNullable(suggestion.get())
        );
        when(suggestions.saveAndFlush(any())).thenAnswer(invocation -> {
            DocumentPageTextSuggestion value = invocation.getArgument(0);
            EntityTestUtils.setId(value, 81L);
            suggestion.set(value);
            return value;
        });

        return new Fixture(
                new WorkerVisionOcrAssessmentService(
                        loader,
                        ocrResults,
                        assessments,
                        signals,
                        suggestions,
                        new DocumentProcessingJobKeyFactory(),
                        properties(),
                        new ObjectMapper()
                ),
                ocrResult,
                deterministic,
                visionAssessment,
                suggestion,
                visionSignals
        );
    }

    private DocumentPageQualitySignal signal(
            DocumentPageQualityAssessment assessment,
            int ordinal,
            String type
    ) {
        DocumentPageQualitySignal signal = new DocumentPageQualitySignal();
        signal.setAssessment(assessment);
        signal.setSignalOrdinal(ordinal);
        signal.setSignalType(type);
        signal.setSignalValueText("warning");
        signal.setSeverity(QualitySignalSeverity.WARNING);
        signal.setWeight(new BigDecimal("0.5000"));
        signal.setMessage("Review layout");
        return signal;
    }

    private WorkerVisionAssessmentCommand command(String suggestedText) {
        return new WorkerVisionAssessmentCommand(
                new WorkerQualityAssessmentCommand(
                        "vision-model",
                        "1.0",
                        "vision-score-v1",
                        new BigDecimal("0.8000"),
                        WorkerQualityStatus.WARNING,
                        "Potential OCR corrections",
                        "Advisory result",
                        List.of(new WorkerQualitySignalCommand(
                                "TEXT_DIFFERENCE",
                                new BigDecimal("0.200000"),
                                null,
                                QualitySignalSeverity.WARNING,
                                new BigDecimal("0.5000"),
                                "Text differs"
                        ))
                ),
                true,
                suggestedText,
                "vision-model",
                "1.0",
                "prompt-v1",
                List.of(new WorkerVisionIssueCommand(
                        "OCR_WORD",
                        "Raw",
                        "Suggested",
                        "Possible OCR correction",
                        new BigDecimal("0.9000")
                )),
                List.of(new WorkerVisionUncertainPassageCommand(
                        "Uncertain line",
                        "Low contrast",
                        new BigDecimal("0.6000")
                ))
        );
    }

    private WorkerVisionAssessmentCommand degradedCommand(
            WorkerVisionAssessmentCommand base,
            String suggestedText,
            List<WorkerVisionIssueCommand> issues,
            String rejectedStage
    ) {
        List<WorkerQualitySignalCommand> signals = new ArrayList<>(
                base.assessment().signals()
        );
        signals.add(new WorkerQualitySignalCommand(
                "VISION_MODEL_DIAGNOSTICS",
                null,
                "{\"doneReason\":\"stop\",\"responseBytes\":1200}",
                QualitySignalSeverity.INFO,
                BigDecimal.ONE,
                "Bounded vision model execution diagnostics"
        ));
        signals.add(new WorkerQualitySignalCommand(
                issues.isEmpty()
                        ? "VISION_SUGGESTION_REJECTED"
                        : "VISION_ISSUES_REJECTED",
                null,
                rejectedStage,
                QualitySignalSeverity.WARNING,
                BigDecimal.ONE,
                "Invalid vision guidance was discarded"
        ));
        WorkerQualityAssessmentCommand assessment = new WorkerQualityAssessmentCommand(
                base.assessment().assessorName(),
                base.assessment().assessorVersion(),
                base.assessment().scoreVersion(),
                base.assessment().overallScore(),
                WorkerQualityStatus.WARNING,
                base.assessment().summary(),
                base.assessment().limitations(),
                signals
        );

        return new WorkerVisionAssessmentCommand(
                assessment,
                true,
                suggestedText,
                base.modelName(),
                base.modelVersion(),
                base.promptVersion(),
                issues,
                List.of()
        );
    }

    private record Fixture(
            WorkerVisionOcrAssessmentService service,
            DocumentPageOcrResult ocrResult,
            DocumentPageQualityAssessment deterministicAssessment,
            AtomicReference<DocumentPageQualityAssessment> visionAssessment,
            AtomicReference<DocumentPageTextSuggestion> suggestion,
            List<DocumentPageQualitySignal> visionSignals
    ) {
    }
}

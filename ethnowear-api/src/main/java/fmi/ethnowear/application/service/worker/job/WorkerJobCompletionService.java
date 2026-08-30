package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.completion.WorkerJobCompletionDetails;
import fmi.ethnowear.application.dto.worker.job.WorkerJobType;
import fmi.ethnowear.application.exception.UnprocessableDocumentEvidenceException;
import fmi.ethnowear.application.exception.WorkerClaimConflictException;
import fmi.ethnowear.application.exception.WorkerManifestConflictException;
import fmi.ethnowear.application.exception.WorkerPreferredOcrInputConflictException;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobKeyFactory;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobScheduler;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingStateReconciler;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.domain.model.document.DocumentPageRenditionType;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.quality.AssessmentType;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.domain.model.document.figure.FigureExtractionState;
import fmi.ethnowear.application.service.document.figure.DocumentPageFigureLifecycleService;
import fmi.ethnowear.application.service.document.figure.FigureExtractionSchedulingService;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageQualityAssessment;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageTextSuggestion;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualityAssessmentRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualitySignalRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageTextSuggestionRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkerJobCompletionService {

    private static final String PREFERRED_OCR_INPUT_CONSTRAINT =
            "UQ_DocumentPageMedia_PreferredOcrInput";

    private final WorkerClaimedJobLoader jobLoader;
    private final DocumentProcessingJobRepository jobRepository;
    private final DocumentPageRepository pageRepository;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final DocumentPageOcrResultRepository ocrResultRepository;
    private final DocumentPageQualityAssessmentRepository assessmentRepository;
    private final DocumentPageQualitySignalRepository signalRepository;
    private final DocumentPageTextSuggestionRepository suggestionRepository;
    private final DocumentPageFigureRepository figureRepository;
    private final DocumentRepository documentRepository;
    private final DocumentProcessingJobScheduler jobScheduler;
    private final DocumentProcessingStateReconciler processingStateReconciler;
    private final DocumentProcessingJobKeyFactory jobKeyFactory;
    private final WorkerIndexingService indexingService;
    private final Clock clock;
    private final ManagementEventPublisher managementEvents;
    private final DocumentPageFigureLifecycleService figureLifecycleService;
    private final FigureExtractionSchedulingService figureSchedulingService;

    @Transactional
    public WorkerJobCompletionDetails complete(
            Long jobId,
            WorkerClaimCredentials credentials
    ) {
        DocumentProcessingJob job = jobLoader.loadForUpdate(jobId);

        return switch (job.getJobType()) {
            case PAGE_EXTRACTION -> job.getDocumentPage() == null
                    ? completePageExtraction(job, credentials)
                    : completePageImageExtraction(job, credentials);
            case OCR -> completeOcr(job, credentials);
            case EXTRACT_PAGE_FIGURES -> completeFigureExtraction(job, credentials);
            case OCR_QUALITY_ASSESSMENT -> completeQualityAssessment(job, credentials);
            case VISION_OCR_ASSESSMENT ->
                    completeVisionOcrAssessment(job, credentials);
            case INDEX_CHUNK -> indexingService.complete(job, credentials);
            default -> throw new WorkerManifestConflictException(
                    "Job type does not support worker completion"
            );
        };
    }

    private WorkerJobCompletionDetails completePageImageExtraction(
            @NonNull DocumentProcessingJob job,
            WorkerClaimCredentials credentials
    ) {
        DocumentPage page = requireOcrPage(job);
        Document document = page.getDocument();

        if (job.getStatus() == JobStatus.SUCCEEDED)
            return new WorkerJobCompletionDetails(
                    job.getId(),
                    WorkerJobType.PAGE_EXTRACTION,
                    document.getId(),
                    page.getId(),
                    1,
                    0,
                    0,
                    true
            );

        validateActive(job, credentials);

        DocumentPageMedia rendition = pageMediaRepository
                .findByDocumentPage_IdAndRenditionTypeAndProducingJob_IdAndProducingAttempt(
                        page.getId(),
                        DocumentPageRenditionType.PDF_PAGE_RENDER,
                        job.getId(),
                        job.getAttemptCount()
                )
                .orElseThrow(() -> new WorkerManifestConflictException(
                        "Page image-extraction job has no accepted rendition"
                ));

        validateCurrentAttemptRendition(job, page, rendition);
        switchPreferredOcrInput(page, rendition);
        page.setProcessingState(ProcessingState.PENDING);

        boolean queuedOcr = queueOcrIfMissing(
                page,
                rendition.getMediaAsset()
        );

        finish(job);
        pageRepository.saveAndFlush(page);
        processingStateReconciler.reconcile(document.getId());
        jobRepository.saveAndFlush(job);
        publishCompleted(job, document, page);

        return new WorkerJobCompletionDetails(
                job.getId(),
                WorkerJobType.PAGE_EXTRACTION,
                document.getId(),
                page.getId(),
                1,
                queuedOcr ? 1 : 0,
                0,
                false
        );
    }

    private void validateCurrentAttemptRendition(
            @NonNull DocumentProcessingJob job,
            @NonNull DocumentPage page,
            @NonNull DocumentPageMedia rendition
    ) {
        if (rendition.getDocumentPage() == null
                || !Objects.equals(rendition.getDocumentPage().getId(), page.getId())
                || rendition.getProducingJob() == null
                || !Objects.equals(rendition.getProducingJob().getId(), job.getId())
                || !Objects.equals(rendition.getProducingAttempt(), job.getAttemptCount()))
            throw new WorkerManifestConflictException(
                    "Accepted rendition does not belong to the current page-extraction attempt"
            );
    }

    private void switchPreferredOcrInput(
            @NonNull DocumentPage page,
            @NonNull DocumentPageMedia rendition
    ) {
        pageMediaRepository
                .findByDocumentPage_IdAndPreferredOcrInputTrue(page.getId())
                .filter(current -> !Objects.equals(current.getId(), rendition.getId()))
                .ifPresent(current -> {
                    current.setPreferredOcrInput(false);
                    pageMediaRepository.saveAndFlush(current);
                });

        if (rendition.isPreferredOcrInput())
            return;

        rendition.setPreferredOcrInput(true);

        try {
            pageMediaRepository.saveAndFlush(rendition);
        } catch (DataIntegrityViolationException ex) {
            if (isPreferredOcrInputConflict(ex))
                throw new WorkerPreferredOcrInputConflictException();

            throw ex;
        }
    }

    private boolean isPreferredOcrInputConflict(Throwable error) {
        Throwable current = error;

        while (current != null) {
            if (current instanceof SQLException sqlException
                    && (sqlException.getErrorCode() == 2601
                    || sqlException.getErrorCode() == 2627)
                    && containsPreferredOcrInputConstraint(sqlException.getMessage()))
                return true;

            if (containsPreferredOcrInputConstraint(current.getMessage()))
                return true;

            current = current.getCause();
        }

        return false;
    }

    private boolean containsPreferredOcrInputConstraint(String message) {
        return message != null
                && message.contains(PREFERRED_OCR_INPUT_CONSTRAINT);
    }

    private WorkerJobCompletionDetails completePageExtraction(
            @NonNull DocumentProcessingJob job,
            WorkerClaimCredentials credentials
    ) {
        Document document = requireDocument(job);

        if (job.getStatus() == JobStatus.SUCCEEDED)
            return existingPageExtractionResult(job, document);

        validateActive(job, credentials);

        List<DocumentPage> pages = pageRepository
                .findByDocument_IdOrderByPageSequenceAsc(document.getId());

        if (pages.isEmpty())
            throw new WorkerManifestConflictException("Document has no extracted pages");

        List<Long> pageIds = pages.stream()
                .map(DocumentPage::getId)
                .toList();

        Set<Long> renderedPageIds = new HashSet<>(
                pageMediaRepository.findPageIdsWithRendition(
                        pageIds,
                        DocumentPageRenditionType.PDF_PAGE_RENDER
                )
        );

        if (renderedPageIds.size() != pages.size())
            throw new WorkerManifestConflictException(
                    "Not every document page has a rendered rendition"
            );

        Map<Long, DocumentPageMedia> preferredInputs = pageMediaRepository
                .findByDocumentPage_IdInAndPreferredOcrInputTrue(pageIds)
                .stream()
                .collect(Collectors.toMap(
                        media -> media.getDocumentPage().getId(),
                        Function.identity()
                ));

        if (preferredInputs.size() != pages.size())
            throw new WorkerManifestConflictException(
                    "Not every document page has a preferred OCR input"
            );

        int queuedOcrJobs = 0;

        for (DocumentPage page : pages) {
            page.setProcessingState(ProcessingState.PENDING);

            MediaAsset input = preferredInputs
                    .get(page.getId())
                    .getMediaAsset();

            if (queueOcrIfMissing(page, input))
                queuedOcrJobs++;
        }

        document.setPageCount(pages.size());
        document.setProcessingState(ProcessingState.PROCESSING);
        finish(job);

        pageRepository.saveAll(pages);
        documentRepository.save(document);
        jobRepository.saveAndFlush(job);
        publishCompleted(job, document, null);

        return new WorkerJobCompletionDetails(
                job.getId(),
                WorkerJobType.PAGE_EXTRACTION,
                document.getId(),
                null,
                pages.size(),
                queuedOcrJobs,
                0,
                false
        );
    }

    private WorkerJobCompletionDetails completeOcr(
            @NonNull DocumentProcessingJob job,
            WorkerClaimCredentials credentials
    ) {
        DocumentPage page = requireOcrPage(job);
        Document document = page.getDocument();

        if (job.getStatus() == JobStatus.SUCCEEDED)
            return existingOcrResult(job, document, page);

        validateActive(job, credentials);

        DocumentPageOcrResult result = ocrResultRepository
                .findByProcessingJob_Id(job.getId())
                .orElseThrow(() -> new UnprocessableDocumentEvidenceException(
                        "OCR job has no accepted result"
                ));

        validateOcrResult(job, page, result);
        figureLifecycleService.markPreviousResultsOutdated(page.getId(), null);
        selectCurrentResult(page, result);
        updatePageSnapshot(page, result);

        int queuedAssessments = queueQualityAssessmentIfMissing(
                page,
                job.getInputMediaAsset(),
                result
        ) ? 1 : 0;

        finish(job);

        ocrResultRepository.saveAndFlush(result);
        pageRepository.saveAndFlush(page);
        processingStateReconciler.reconcile(document.getId());
        jobRepository.saveAndFlush(job);
        publishCompleted(job, document, page);

        if (result.getFigureExtractionState() == FigureExtractionState.PENDING)
            figureSchedulingService.scheduleAfterCommit(result.getId());

        return new WorkerJobCompletionDetails(
                job.getId(),
                WorkerJobType.OCR,
                document.getId(),
                page.getId(),
                1,
                0,
                queuedAssessments,
                false
        );
    }

    private WorkerJobCompletionDetails completeFigureExtraction(
            @NonNull DocumentProcessingJob job,
            WorkerClaimCredentials credentials
    ) {
        DocumentPage page = requireOcrPage(job);
        Document document = page.getDocument();

        if (job.getStatus() == JobStatus.SUCCEEDED)
            return existingFigureExtraction(job, document, page);

        validateActive(job, credentials);

        Long ocrResultId = jobKeyFactory.figureOcrResultId(job.getJobKey());
        DocumentPageOcrResult ocrResult = ocrResultRepository.findById(ocrResultId)
                .orElseThrow(() -> new WorkerManifestConflictException(
                        "Figure-extraction job has no OCR-result target"
                ));

        validateFigureExtractionEvidence(job, page, ocrResult);
        figureLifecycleService.markPreviousResultsOutdated(page.getId(), job.getId());
        ocrResult.setFigureExtractionState(FigureExtractionState.COMPLETED);
        ocrResult.setFigureExtractionMessage(null);
        finish(job);

        ocrResultRepository.saveAndFlush(ocrResult);
        jobRepository.saveAndFlush(job);
        publishCompleted(job, document, page);

        return new WorkerJobCompletionDetails(
                job.getId(),
                WorkerJobType.EXTRACT_PAGE_FIGURES,
                document.getId(),
                page.getId(),
                figureRepository
                        .findByProcessingJob_IdAndProducingAttemptOrderByFigureOrdinalAsc(
                                job.getId(),
                                job.getAttemptCount()
                        ).size(),
                0,
                0,
                false
        );
    }

    private void validateFigureExtractionEvidence(
            DocumentProcessingJob job,
            DocumentPage page,
            DocumentPageOcrResult ocrResult
    ) {
        if (!ocrResult.isCurrent()
                || ocrResult.getDocumentPage() == null
                || !Objects.equals(ocrResult.getDocumentPage().getId(), page.getId())
                || ocrResult.getDocumentPageMedia() == null
                || ocrResult.getDocumentPageMedia().getMediaAsset() == null
                || job.getInputMediaAsset() == null
                || !Objects.equals(
                ocrResult.getDocumentPageMedia().getMediaAsset().getId(),
                job.getInputMediaAsset().getId()
        ))
            throw new WorkerManifestConflictException(
                    "Figure-extraction evidence is no longer current"
            );
    }

    private WorkerJobCompletionDetails existingFigureExtraction(
            DocumentProcessingJob job,
            Document document,
            DocumentPage page
    ) {
        Long ocrResultId = jobKeyFactory.figureOcrResultId(job.getJobKey());
        DocumentPageOcrResult result = ocrResultRepository.findById(ocrResultId)
                .orElseThrow(() -> new WorkerManifestConflictException(
                        "Completed figure-extraction job has no OCR result"
                ));

        if (result.getFigureExtractionState() != FigureExtractionState.COMPLETED)
            throw new WorkerManifestConflictException(
                    "Completed figure-extraction job has inconsistent workflow state"
            );

        return new WorkerJobCompletionDetails(
                job.getId(),
                WorkerJobType.EXTRACT_PAGE_FIGURES,
                document.getId(),
                page.getId(),
                figureRepository
                        .findByProcessingJob_IdAndProducingAttemptOrderByFigureOrdinalAsc(
                                job.getId(),
                                job.getAttemptCount()
                        ).size(),
                0,
                0,
                true
        );
    }

    private void validateActive(
            DocumentProcessingJob job,
            WorkerClaimCredentials credentials
    ) {
        jobLoader.validateActive(job, credentials);

        if (job.getStatus() == JobStatus.CANCEL_REQUESTED)
            throw new WorkerClaimConflictException();
    }

    private WorkerJobCompletionDetails completeQualityAssessment(
            @NonNull DocumentProcessingJob job,
            WorkerClaimCredentials credentials
    ) {
        DocumentPage page = requireQualityAssessmentPage(job);
        Document document = page.getDocument();

        if (job.getStatus() == JobStatus.SUCCEEDED)
            return existingQualityAssessment(job, document, page);

        validateActive(job, credentials);

        DocumentPageQualityAssessment assessment =
                assessmentRepository.findByProcessingJob_Id(job.getId())
                        .orElseThrow(() -> new UnprocessableDocumentEvidenceException(
                                "Quality-assessment job has no accepted assessment"
                        ));

        validateQualityAssessment(job, page, assessment);

        var qualitySignals = signalRepository
                .findByAssessment_IdOrderBySignalOrdinalAscIdAsc(assessment.getId());

        if (qualitySignals.isEmpty())
            throw new UnprocessableDocumentEvidenceException(
                    "Accepted quality assessment has no signals"
            );

        assessmentRepository
                .findByDocumentPage_IdAndDocumentPageMedia_IdAndAssessmentTypeAndCurrentTrue(
                        page.getId(),
                        assessment.getDocumentPageMedia().getId(),
                        AssessmentType.COMBINED_OCR_QUALITY
                )
                .filter(current -> !Objects.equals(current.getId(), assessment.getId()))
                .ifPresent(current -> {
                    current.setCurrent(false);
                    assessmentRepository.saveAndFlush(current);
                });

        assessment.setCurrent(true);
        page.setReviewState(ReviewState.REVIEW_REQUIRED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);
        page.setIndexingState(IndexingState.NOT_ELIGIBLE);
        page.setCurrentQualityAssessment(assessment);
        page.setCurrentQualityScore(assessment.getOverallScore());
        page.setCurrentQualityStatus(assessment.getQualityStatus());
        page.setCurrentQualityPassedChecks((int) qualitySignals.stream()
                .filter(signal -> signal.getSeverity()
                        == fmi.ethnowear.domain.model.document.quality.QualitySignalSeverity.INFO)
                .count());
        page.setCurrentQualityFailedChecks((int) qualitySignals.stream()
                .filter(signal -> signal.getSeverity()
                        != fmi.ethnowear.domain.model.document.quality.QualitySignalSeverity.INFO)
                .count());
        page.setCurrentQualityAssessedAt(assessment.getCreatedAt());
        finish(job);

        assessmentRepository.saveAndFlush(assessment);
        pageRepository.save(page);
        jobRepository.saveAndFlush(job);
        publishCompleted(job, document, page);

        return new WorkerJobCompletionDetails(
                job.getId(),
                WorkerJobType.OCR_QUALITY_ASSESSMENT,
                document.getId(),
                page.getId(),
                1,
                0,
                0,
                0,
                false
        );
    }

    private WorkerJobCompletionDetails completeVisionOcrAssessment(
            @NonNull DocumentProcessingJob job,
            WorkerClaimCredentials credentials
    ) {
        DocumentPage page = requireQualityAssessmentPage(job);
        Document document = page.getDocument();

        if (job.getStatus() == JobStatus.SUCCEEDED)
            return existingVisionAssessment(job, document, page);

        validateActive(job, credentials);

        DocumentPageQualityAssessment assessment = assessmentRepository
                .findByProcessingJob_Id(job.getId())
                .orElseThrow(() -> new UnprocessableDocumentEvidenceException(
                        "Vision job has no accepted assessment"
                ));
        DocumentPageTextSuggestion suggestion = suggestionRepository
                .findByProcessingJob_Id(job.getId())
                .orElseThrow(() -> new UnprocessableDocumentEvidenceException(
                        "Vision job has no accepted text suggestion"
                ));
        Long ocrResultId = jobKeyFactory.visionOcrResultId(job.getJobKey());
        Long deterministicAssessmentId = jobKeyFactory
                .visionDeterministicAssessmentId(job.getJobKey());
        DocumentPageOcrResult currentOcr = ocrResultRepository
                .findByDocumentPage_IdAndCurrentTrue(page.getId())
                .orElseThrow(() -> new WorkerManifestConflictException(
                        "Vision job page has no current OCR result"
                ));

        if (assessment.getDocumentPageMedia() == null)
            throw new WorkerManifestConflictException(
                    "Accepted vision assessment has no exact rendition"
            );

        validateVisionAssessment(
                job,
                page,
                assessment,
                suggestion,
                currentOcr,
                ocrResultId,
                deterministicAssessmentId
        );

        if (signalRepository
                .findByAssessment_IdOrderBySignalOrdinalAscIdAsc(
                        assessment.getId()
                )
                .isEmpty())
            throw new UnprocessableDocumentEvidenceException(
                    "Accepted vision assessment has no signals"
            );

        assessmentRepository
                .findByDocumentPage_IdAndDocumentPageMedia_IdAndAssessmentTypeAndCurrentTrue(
                        page.getId(),
                        assessment.getDocumentPageMedia().getId(),
                        AssessmentType.VISION_TEXT_COMPARISON
                )
                .filter(current -> !Objects.equals(current.getId(), assessment.getId()))
                .ifPresent(current -> {
                    current.setCurrent(false);
                    assessmentRepository.saveAndFlush(current);
                });

        assessment.setCurrent(true);

        if (page.getTranscriptionApprovalState()
                != TranscriptionApprovalState.APPROVED) {
            page.setReviewState(ReviewState.REVIEW_REQUIRED);
            page.setIndexingState(IndexingState.NOT_ELIGIBLE);
            pageRepository.save(page);
        }

        finish(job);
        assessmentRepository.saveAndFlush(assessment);
        jobRepository.saveAndFlush(job);
        publishCompleted(job, document, page);

        return new WorkerJobCompletionDetails(
                job.getId(),
                WorkerJobType.VISION_OCR_ASSESSMENT,
                document.getId(),
                page.getId(),
                1,
                0,
                0,
                0,
                false
        );
    }

    private void validateVisionAssessment(
            DocumentProcessingJob job,
            DocumentPage page,
            DocumentPageQualityAssessment assessment,
            DocumentPageTextSuggestion suggestion,
            DocumentPageOcrResult currentOcr,
            Long expectedOcrResultId,
            Long expectedDeterministicAssessmentId
    ) {
        var currentDeterministicAssessment = assessmentRepository
                .findByDocumentPage_IdAndDocumentPageMedia_IdAndAssessmentTypeAndCurrentTrue(
                        page.getId(),
                        assessment.getDocumentPageMedia().getId(),
                        AssessmentType.COMBINED_OCR_QUALITY
                );

        if (!Objects.equals(currentOcr.getId(), expectedOcrResultId)
                || currentDeterministicAssessment.isEmpty()
                || !Objects.equals(
                        currentDeterministicAssessment.get().getId(),
                        expectedDeterministicAssessmentId
                )
                || assessment.getAssessmentType()
                != AssessmentType.VISION_TEXT_COMPARISON
                || assessment.getAssessorType()
                != fmi.ethnowear.domain.model.document.quality.AssessorType.VISION_MODEL
                || assessment.getDocumentPage() == null
                || !Objects.equals(assessment.getDocumentPage().getId(), page.getId())
                || assessment.getDocumentPageOcrResult() == null
                || !Objects.equals(
                        assessment.getDocumentPageOcrResult().getId(),
                        currentOcr.getId()
                )
                || currentOcr.getDocumentPageMedia() == null
                || !currentOcr.getDocumentPageMedia().isPreferredOcrInput()
                || !Objects.equals(
                        currentOcr.getDocumentPageMedia().getId(),
                        assessment.getDocumentPageMedia().getId()
                )
                || assessment.getProcessingJob() == null
                || !Objects.equals(assessment.getProcessingJob().getId(), job.getId())
                || assessment.getDocumentPageMedia() == null
                || suggestion.getDocumentPageMedia() == null
                || !Objects.equals(
                        assessment.getDocumentPageMedia().getId(),
                        suggestion.getDocumentPageMedia().getId()
                )
                || job.getInputMediaAsset() == null
                || assessment.getDocumentPageMedia().getMediaAsset() == null
                || !Objects.equals(
                        assessment.getDocumentPageMedia().getMediaAsset().getId(),
                        job.getInputMediaAsset().getId()
                )
                || suggestion.getDocumentPage() == null
                || !Objects.equals(suggestion.getDocumentPage().getId(), page.getId())
                || suggestion.getDocumentPageOcrResult() == null
                || !Objects.equals(
                        suggestion.getDocumentPageOcrResult().getId(),
                        currentOcr.getId()
                )
                || suggestion.getProcessingJob() == null
                || !Objects.equals(suggestion.getProcessingJob().getId(), job.getId())
                || !fmi.ethnowear.util.ContentHashUtils.sha256(
                        suggestion.getSuggestedText()
                ).equals(suggestion.getSuggestedTextHash()))
            throw new WorkerManifestConflictException(
                    "Accepted vision result does not match the current job context"
            );
    }

    private @NonNull DocumentPage requireQualityAssessmentPage(
            @NonNull DocumentProcessingJob job
    ) {
        DocumentPage page = job.getDocumentPage();

        if (page == null || page.getDocument() == null)
            throw new WorkerManifestConflictException(
                    "Quality-assessment job has no document page"
            );

        if (job.getDocument() != null
                && !Objects.equals(job.getDocument().getId(), page.getDocument().getId()))
            throw new WorkerManifestConflictException(
                    "Quality-assessment job document and page are inconsistent"
            );

        return page;
    }

    private void validateQualityAssessment(
            @NonNull DocumentProcessingJob job,
            @NonNull DocumentPage page,
            @NonNull DocumentPageQualityAssessment assessment
    ) {
        if (assessment.getDocumentPage() == null
                || !Objects.equals(assessment.getDocumentPage().getId(), page.getId())
                || assessment.getProcessingJob() == null
                || !Objects.equals(assessment.getProcessingJob().getId(), job.getId())
                || assessment.getDocumentPageOcrResult() == null
                || !Objects.equals(
                        assessment.getDocumentPageOcrResult().getId(),
                        jobKeyFactory.ocrResultId(job.getJobKey())
                )
                || assessment.getDocumentPageMedia() == null
                || assessment.getDocumentPageMedia().getMediaAsset() == null
                || job.getInputMediaAsset() == null
                || !Objects.equals(
                        assessment.getDocumentPageMedia().getMediaAsset().getId(),
                        job.getInputMediaAsset().getId()
                )
                || assessment.getAssessmentType() != AssessmentType.COMBINED_OCR_QUALITY)
            throw new WorkerManifestConflictException(
                    "Accepted quality assessment does not match the claimed job context"
            );
    }

    private @NonNull Document requireDocument(@NonNull DocumentProcessingJob job) {
        if (job.getDocument() == null)
            throw new WorkerManifestConflictException("Job has no document");

        return job.getDocument();
    }

    private @NonNull DocumentPage requireOcrPage(
            @NonNull DocumentProcessingJob job
    ) {
        DocumentPage page = job.getDocumentPage();

        if (page == null || page.getDocument() == null)
            throw new WorkerManifestConflictException("OCR job has no document page");

        if (job.getDocument() != null
                && !Objects.equals(job.getDocument().getId(), page.getDocument().getId()))
            throw new WorkerManifestConflictException(
                    "OCR job document and page are inconsistent"
            );

        return page;
    }

    private void validateOcrResult(
            @NonNull DocumentProcessingJob job,
            @NonNull DocumentPage page,
            @NonNull DocumentPageOcrResult result
    ) {
        if (result.getDocumentPage() == null
                || !Objects.equals(result.getDocumentPage().getId(), page.getId())
                || result.getProcessingJob() == null
                || !Objects.equals(result.getProcessingJob().getId(), job.getId())
                || result.getDocumentPageMedia() == null
                || result.getDocumentPageMedia().getMediaAsset() == null
                || job.getInputMediaAsset() == null
                || !Objects.equals(
                        result.getDocumentPageMedia().getMediaAsset().getId(),
                        job.getInputMediaAsset().getId()
                ))
            throw new WorkerManifestConflictException(
                    "Accepted OCR result does not match the claimed job input"
            );
    }

    private void selectCurrentResult(
            @NonNull DocumentPage page,
            @NonNull DocumentPageOcrResult result
    ) {
        ocrResultRepository.findByDocumentPage_IdAndCurrentTrue(page.getId())
                .filter(current -> !Objects.equals(current.getId(), result.getId()))
                .ifPresent(current -> {
                    current.setCurrent(false);
                    ocrResultRepository.saveAndFlush(current);
                });

        result.setCurrent(true);
    }

    private void updatePageSnapshot(
            @NonNull DocumentPage page,
            @NonNull DocumentPageOcrResult result
    ) {
        page.setRawOcrText(result.getRawText());
        page.setOcrEngine(result.getOcrEngine());
        page.setOcrEngineVersion(result.getOcrEngineVersion());
        page.setOcrLanguage(result.getOcrLanguage());
        page.setOcrConfidence(result.getOcrConfidence());
        page.setProcessingState(ProcessingState.COMPLETED);
        page.setReviewState(ReviewState.REVIEW_REQUIRED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);
        page.setIndexingState(IndexingState.NOT_ELIGIBLE);
    }

    private boolean queueOcrIfMissing(
            @NonNull DocumentPage page,
            MediaAsset input
    ) {
        String activeJobKey = jobKeyFactory.forPage(JobType.OCR, page.getId());

        if (jobRepository.findByActiveJobKey(activeJobKey).isPresent())
            return false;

        jobScheduler.queueOcr(page, input);
        return true;
    }

    private boolean queueQualityAssessmentIfMissing(
            @NonNull DocumentPage page,
            MediaAsset input,
            DocumentPageOcrResult ocrResult
    ) {
        if (input == null)
            throw new WorkerManifestConflictException("OCR job has no input media");

        String activeJobKey = jobKeyFactory.forOcrResult(ocrResult.getId());

        if (jobRepository.findByActiveJobKey(activeJobKey).isPresent())
            return false;

        jobScheduler.queueOcrQualityAssessment(page, input, ocrResult);
        return true;
    }

    private void finish(@NonNull DocumentProcessingJob job) {
        LocalDateTime now = LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC
        );

        job.setStatus(JobStatus.SUCCEEDED);
        job.setFinishedAt(now);
        job.setErrorCode(null);
        job.setSafeErrorMessage(null);
        job.setErrorDetailsJson(null);
        job.setCancellationReason(null);
        job.clearClaimOwnership();
        job.clearActiveJobKey();
    }

    private void publishCompleted(
            DocumentProcessingJob job,
            Document document,
            DocumentPage page
    ) {
        managementEvents.document(document, ManagementEvent.Action.STATUS_CHANGED);

        if (page != null)
            managementEvents.page(page, ManagementEvent.Action.STATUS_CHANGED);

        managementEvents.processingJob(job, ManagementEvent.Action.STATUS_CHANGED);
    }

    private WorkerJobCompletionDetails existingPageExtractionResult(
            @NonNull DocumentProcessingJob job,
            @NonNull Document document
    ) {
        int pageCount = Math.toIntExact(
                pageRepository.countByDocument_Id(document.getId())
        );

        return new WorkerJobCompletionDetails(
                job.getId(),
                WorkerJobType.PAGE_EXTRACTION,
                document.getId(),
                null,
                pageCount,
                0,
                0,
                true
        );
    }

    private WorkerJobCompletionDetails existingOcrResult(
            @NonNull DocumentProcessingJob job,
            @NonNull Document document,
            @NonNull DocumentPage page
    ) {
        if (ocrResultRepository.findByProcessingJob_Id(job.getId()).isEmpty())
            throw new WorkerManifestConflictException(
                    "Completed OCR job has no persisted result"
            );

        return new WorkerJobCompletionDetails(
                job.getId(),
                WorkerJobType.OCR,
                document.getId(),
                page.getId(),
                1,
                0,
                0,
                true
        );
    }

    private WorkerJobCompletionDetails existingQualityAssessment(
            @NonNull DocumentProcessingJob job,
            @NonNull Document document,
            @NonNull DocumentPage page
    ) {
        DocumentPageQualityAssessment assessment =
                assessmentRepository.findByProcessingJob_Id(job.getId())
                        .orElseThrow(() -> new WorkerManifestConflictException(
                                "Completed quality-assessment job has no persisted assessment"
                        ));

        if (!assessment.isCurrent())
            throw new WorkerManifestConflictException(
                    "Completed quality-assessment job has no current assessment"
            );

        return new WorkerJobCompletionDetails(
                job.getId(),
                WorkerJobType.OCR_QUALITY_ASSESSMENT,
                document.getId(),
                page.getId(),
                1,
                0,
                0,
                true
        );
    }

    private WorkerJobCompletionDetails existingVisionAssessment(
            @NonNull DocumentProcessingJob job,
            @NonNull Document document,
            @NonNull DocumentPage page
    ) {
        DocumentPageQualityAssessment assessment = assessmentRepository
                .findByProcessingJob_Id(job.getId())
                .orElseThrow(() -> new WorkerManifestConflictException(
                        "Completed vision-assessment job has no persisted assessment"
                ));

        DocumentPageTextSuggestion suggestion = suggestionRepository
                .findByProcessingJob_Id(job.getId())
                .orElseThrow(() -> new WorkerManifestConflictException(
                        "Completed vision-assessment job has no accepted result"
                ));
        DocumentPageOcrResult currentOcr = ocrResultRepository
                .findByDocumentPage_IdAndCurrentTrue(page.getId())
                .orElseThrow(() -> new WorkerManifestConflictException(
                        "Completed vision-assessment job has no current OCR result"
                ));

        if (assessment.getDocumentPageMedia() == null)
            throw new WorkerManifestConflictException(
                    "Completed vision assessment has no exact rendition"
            );

        validateVisionAssessment(
                job,
                page,
                assessment,
                suggestion,
                currentOcr,
                jobKeyFactory.visionOcrResultId(job.getJobKey()),
                jobKeyFactory.visionDeterministicAssessmentId(job.getJobKey())
        );

        return new WorkerJobCompletionDetails(
                job.getId(),
                WorkerJobType.VISION_OCR_ASSESSMENT,
                document.getId(),
                page.getId(),
                1,
                0,
                0,
                0,
                true
        );
    }
}

package fmi.ethnowear.application.service.document.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.document.command.review.CorrectedTextSaveCommand;
import fmi.ethnowear.application.dto.document.command.review.CorrectedTextResetCommand;
import fmi.ethnowear.application.dto.document.command.review.PageApprovalCommand;
import fmi.ethnowear.application.dto.document.command.review.PageRejectionCommand;
import fmi.ethnowear.application.dto.document.command.review.TextSuggestionApplyCommand;
import fmi.ethnowear.application.dto.document.command.review.TextSuggestionIssueApplyCommand;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageReviewDetails;
import fmi.ethnowear.application.dto.worker.vision.WorkerVisionIssueCommand;
import fmi.ethnowear.application.exception.InvalidDocumentPageReviewTransitionException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.application.service.document.chunk.DocumentChunkGenerationEligibilityService;
import fmi.ethnowear.application.service.document.chunk.DocumentChunkGenerationService;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.quality.AssessmentType;
import fmi.ethnowear.domain.model.document.review.ReviewAction;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageReview;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageTextSuggestion;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageReviewRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualityAssessmentRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageTextSuggestionRepository;
import fmi.ethnowear.util.ContentHashUtils;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
public class DocumentPageReviewService {

    private final DocumentPageRepository pageRepository;
    private final DocumentPageOcrResultRepository ocrResultRepository;
    private final DocumentPageReviewRepository reviewRepository;
    private final DocumentPageQualityAssessmentRepository assessmentRepository;
    private final DocumentPageTextSuggestionRepository suggestionRepository;
    private final DocumentPageChunkInvalidator chunkInvalidator;
    private final DocumentChunkGenerationEligibilityService chunkGenerationEligibilityService;
    private final DocumentChunkGenerationService chunkGenerationService;
    private final DocumentHistoryMapper historyMapper;
    private final ManagementEventPublisher managementEvents;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public DocumentPageReviewDetails saveCorrectedText(
            Long pageId,
            CorrectedTextSaveCommand command,
            String reviewer
    ) {
        validatePageId(pageId);
        validateReviewer(reviewer);

        if (command == null || isBlank(command.correctedText()))
            throw new IllegalArgumentException("Corrected text is required");

        DocumentPage page = requirePage(pageId);
        String hash = ContentHashUtils.sha256(command.correctedText());
        boolean textChanged = !hash.equals(page.getCorrectedTextHash());
        ReviewTransition previous = textChanged
                ? revokeApprovalIfRequired(
                        page,
                        reviewer,
                        ReviewState.IN_REVIEW,
                        "Corrected text changed"
                )
                : new ReviewTransition(
                        page.getReviewState(),
                        page.getTranscriptionApprovalState()
                );

        page.setCorrectedText(command.correctedText());
        page.setCorrectedTextHash(hash);

        if (textChanged) {
            page.setReviewState(ReviewState.IN_REVIEW);
            page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);
            chunkInvalidator.invalidate(page);
        }

        page.setReviewer(reviewer.trim());
        page.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC));
        pageRepository.save(page);

        DocumentPageReview event = reviewRepository.saveAndFlush(
                createEvent(
                        page,
                        ReviewAction.CORRECTED_TEXT_SAVED,
                        reviewer,
                        command.correctedText(),
                        hash,
                        previous.reviewState(),
                        page.getReviewState(),
                        previous.approvalState(),
                        page.getTranscriptionApprovalState(),
                        null
                )
        );

        managementEvents.page(page, ManagementEvent.Action.STATUS_CHANGED);

        return historyMapper.toDetails(event);
    }

    @Transactional
    public DocumentPageReviewDetails resetFromCurrentOcr(
            Long pageId,
            CorrectedTextResetCommand command,
            String reviewer
    ) {
        validatePageId(pageId);
        validateReviewer(reviewer);

        if(command == null || !command.confirmed())
            throw new IllegalArgumentException(
                    "Corrected-text reset must be explicitly confirmed"
            );

        DocumentPage page = requirePage(pageId);
        DocumentPageOcrResult currentOcr = ocrResultRepository
                .findByDocumentPage_IdAndCurrentTrue(pageId)
                .orElseThrow(() -> new InvalidDocumentPageReviewTransitionException(
                        "Document page has no current OCR result"
                ));

        if(isBlank(currentOcr.getRawText()))
            throw new InvalidDocumentPageReviewTransitionException(
                    "Current OCR result has no text"
            );

        String previousCorrectedText = page.getCorrectedText();
        String previousCorrectedTextHash = page.getCorrectedTextHash();
        String correctedText = currentOcr.getRawText();
        String correctedTextHash = ContentHashUtils.sha256(correctedText);
        boolean textChanged = !correctedTextHash.equals(previousCorrectedTextHash);
        ReviewTransition previous = textChanged
                ? revokeApprovalIfRequired(
                        page,
                        reviewer,
                        ReviewState.REVIEW_REQUIRED,
                        "Corrected text reset from current OCR"
                )
                : new ReviewTransition(
                        page.getReviewState(),
                        page.getTranscriptionApprovalState()
                );

        page.setCorrectedText(correctedText);
        page.setCorrectedTextHash(correctedTextHash);

        if (textChanged) {
            page.setReviewState(ReviewState.REVIEW_REQUIRED);
            page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);
            chunkInvalidator.invalidate(page);
        }

        page.setReviewer(reviewer.trim());
        page.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC));
        page.setReviewNotes(null);

        pageRepository.save(page);

        DocumentPageReview event = reviewRepository.saveAndFlush(
                createEvent(
                        page,
                        ReviewAction.RESET_FROM_CURRENT_OCR,
                        reviewer,
                        previousCorrectedText,
                        previousCorrectedTextHash,
                        previous.reviewState(),
                        page.getReviewState(),
                        previous.approvalState(),
                        page.getTranscriptionApprovalState(),
                        "Corrected text reset from current OCR result "
                                + currentOcr.getId()
                )
        );

        managementEvents.page(page, ManagementEvent.Action.STATUS_CHANGED);

        return historyMapper.toDetails(event);
    }

    @Transactional
    public DocumentPageReviewDetails applyTextSuggestion(
            Long pageId,
            Long suggestionId,
            TextSuggestionApplyCommand command,
            String reviewer
    ) {
        validatePageId(pageId);
        validateReviewer(reviewer);

        if (suggestionId == null)
            throw new IllegalArgumentException("Text suggestion id is required");

        if (command == null || !command.confirmed())
            throw new IllegalArgumentException(
                    "Text suggestion application must be explicitly confirmed"
            );

        DocumentPage page = requirePage(pageId);
        DocumentPageTextSuggestion suggestion = suggestionRepository
                .findById(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document page text suggestion",
                        suggestionId
                ));

        validateCurrentSuggestion(page, suggestion);

        var previousApplication = reviewRepository
                .findBySourceTextSuggestion_IdAndSourceTextSuggestionIssueOrdinalIsNull(
                        suggestionId
                );

        if (previousApplication.isPresent()) {
            if (suggestion.getSuggestedTextHash().equals(
                    page.getCorrectedTextHash()
            ))
                return historyMapper.toDetails(previousApplication.get());

            throw new InvalidDocumentPageReviewTransitionException(
                    "Text suggestion has already been applied and is now stale"
            );
        }

        boolean textChanged = !suggestion.getSuggestedTextHash().equals(
                page.getCorrectedTextHash()
        );
        ReviewTransition previous = textChanged
                ? revokeApprovalIfRequired(
                        page,
                        reviewer,
                        ReviewState.REVIEW_REQUIRED,
                        "Vision text suggestion applied"
                )
                : new ReviewTransition(
                        page.getReviewState(),
                        page.getTranscriptionApprovalState()
                );

        page.setCorrectedText(suggestion.getSuggestedText());
        page.setCorrectedTextHash(suggestion.getSuggestedTextHash());

        if (textChanged) {
            page.setReviewState(ReviewState.REVIEW_REQUIRED);
            page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);
            chunkInvalidator.invalidate(page);
        }

        page.setReviewer(reviewer.trim());
        page.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC));
        page.setReviewNotes(null);

        pageRepository.save(page);

        DocumentPageReview event = reviewRepository.saveAndFlush(
                createEvent(
                        page,
                        suggestion,
                        ReviewAction.CORRECTED_TEXT_SAVED,
                        reviewer,
                        suggestion.getSuggestedText(),
                        suggestion.getSuggestedTextHash(),
                        previous.reviewState(),
                        page.getReviewState(),
                        previous.approvalState(),
                        page.getTranscriptionApprovalState(),
                        "Applied vision text suggestion " + suggestion.getId()
                )
        );

        managementEvents.page(page, ManagementEvent.Action.STATUS_CHANGED);
        return historyMapper.toDetails(event);
    }

    @Transactional
    public DocumentPageReviewDetails applyTextSuggestionIssue(
            Long pageId,
            Long suggestionId,
            int issueIndex,
            TextSuggestionIssueApplyCommand command,
            String reviewer
    ) {
        validatePageId(pageId);
        validateReviewer(reviewer);

        if (suggestionId == null)
            throw new IllegalArgumentException("Text suggestion id is required");

        if (issueIndex < 0)
            throw new IllegalArgumentException("Text suggestion issue index is invalid");

        if (command == null || !command.confirmed())
            throw new IllegalArgumentException(
                    "Text suggestion issue application must be explicitly confirmed"
            );

        DocumentPage page = requirePage(pageId);
        DocumentPageTextSuggestion suggestion = suggestionRepository
                .findById(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document page text suggestion",
                        suggestionId
                ));

        validateCurrentSuggestion(page, suggestion);
        var issues = parseIssues(suggestion.getIssuesJson());

        if (issueIndex >= issues.size())
            throw new IllegalArgumentException("Text suggestion issue index is invalid");

        WorkerVisionIssueCommand issue = issues.get(issueIndex);

        if (!issue.safelyApplicable()
                || issue.startOffset() == null
                || issue.endOffset() == null
                || issue.originalText() == null
                || issue.suggestedText() == null)
            throw new InvalidDocumentPageReviewTransitionException(
                    "Text suggestion issue cannot be applied safely"
            );

        DocumentPageOcrResult currentOcr = suggestion.getDocumentPageOcrResult();
        String rawOcrText = currentOcr.getRawText();
        validateExactRange(rawOcrText, issue);

        var previousApplication = reviewRepository
                .findBySourceTextSuggestion_IdAndSourceTextSuggestionIssueOrdinal(
                        suggestionId,
                        issueIndex
                );

        if (previousApplication.isPresent()) {
            DocumentPageReview applied = previousApplication.get();

            if (Objects.equals(
                    applied.getCorrectedTextHash(),
                    page.getCorrectedTextHash()
            ))
                return historyMapper.toDetails(applied);

            throw new InvalidDocumentPageReviewTransitionException(
                    "Text suggestion issue has already been applied"
            );
        }

        String editableText = isBlank(page.getCorrectedText())
                ? rawOcrText
                : page.getCorrectedText();
        String editableHash = ContentHashUtils.sha256(editableText);

        if (!editableHash.equals(command.expectedCorrectedTextHash()))
            throw new InvalidDocumentPageReviewTransitionException(
                    "Corrected text changed after the suggestion was displayed"
            );

        validateExactRange(editableText, issue);

        String correctedText = editableText.substring(0, issue.startOffset())
                + issue.suggestedText()
                + editableText.substring(issue.endOffset());
        String correctedTextHash = ContentHashUtils.sha256(correctedText);
        ReviewTransition previous = revokeApprovalIfRequired(
                page,
                reviewer,
                ReviewState.REVIEW_REQUIRED,
                "Vision text suggestion issue applied"
        );

        page.setCorrectedText(correctedText);
        page.setCorrectedTextHash(correctedTextHash);
        page.setReviewState(ReviewState.REVIEW_REQUIRED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);
        page.setReviewer(reviewer.trim());
        page.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC));
        page.setReviewNotes(null);
        chunkInvalidator.invalidate(page);
        pageRepository.save(page);

        DocumentPageReview event = createEvent(
                page,
                suggestion,
                ReviewAction.CORRECTED_TEXT_SAVED,
                reviewer,
                correctedText,
                correctedTextHash,
                previous.reviewState(),
                page.getReviewState(),
                previous.approvalState(),
                page.getTranscriptionApprovalState(),
                "Applied vision issue " + issueIndex + " (" + issue.issueType() + ")"
        );
        event.setSourceTextSuggestionIssueOrdinal(issueIndex);
        DocumentPageReview saved = reviewRepository.saveAndFlush(event);

        managementEvents.page(page, ManagementEvent.Action.STATUS_CHANGED);
        return historyMapper.toDetails(saved);
    }

    private void validateExactRange(
            String text,
            WorkerVisionIssueCommand issue
    ) {
        if (text == null
                || issue.startOffset() < 0
                || issue.endOffset() <= issue.startOffset()
                || issue.endOffset() > text.length()
                || !text.substring(issue.startOffset(), issue.endOffset())
                .equals(issue.originalText()))
            throw new InvalidDocumentPageReviewTransitionException(
                    "Text suggestion issue no longer matches the assessed text"
            );
    }

    private java.util.List<WorkerVisionIssueCommand> parseIssues(String issuesJson) {
        try {
            return objectMapper.readValue(issuesJson, new TypeReference<>() { });
        } catch (JsonProcessingException ex) {
            throw new InvalidDocumentPageReviewTransitionException(
                    "Stored text suggestion issues are invalid"
            );
        }
    }

    private void validateCurrentSuggestion(
            DocumentPage page,
            DocumentPageTextSuggestion suggestion
    ) {
        if (suggestion.getDocumentPage() == null
                || !page.getId().equals(suggestion.getDocumentPage().getId())
                || suggestion.getDocumentPageOcrResult() == null
                || suggestion.getProcessingJob() == null
                || suggestion.getProcessingJob().getStatus() != JobStatus.SUCCEEDED
                || !ContentHashUtils.sha256(suggestion.getSuggestedText())
                .equals(suggestion.getSuggestedTextHash()))
            throw new InvalidDocumentPageReviewTransitionException(
                    "Text suggestion is inconsistent with the document page"
            );

        DocumentPageOcrResult currentOcr = ocrResultRepository
                .findByDocumentPage_IdAndCurrentTrue(page.getId())
                .orElseThrow(() -> new InvalidDocumentPageReviewTransitionException(
                        "Document page has no current OCR result"
                ));

        if (!currentOcr.getId().equals(
                suggestion.getDocumentPageOcrResult().getId()
        ))
            throw new InvalidDocumentPageReviewTransitionException(
                    "Text suggestion does not target the current OCR result"
            );

        DocumentPageTextSuggestion currentSuggestion = suggestionRepository
                .findFirstByDocumentPage_IdAndDocumentPageOcrResult_IdAndProcessingJob_StatusOrderByCreatedAtDescIdDesc(
                        page.getId(),
                        currentOcr.getId(),
                        JobStatus.SUCCEEDED
                )
                .orElseThrow(() -> new InvalidDocumentPageReviewTransitionException(
                        "Document page has no current completed text suggestion"
                ));

        if (!currentSuggestion.getId().equals(suggestion.getId()))
            throw new InvalidDocumentPageReviewTransitionException(
                    "Text suggestion has been superseded"
            );

        var assessment = assessmentRepository
                .findByProcessingJob_Id(suggestion.getProcessingJob().getId())
                .orElseThrow(() -> new InvalidDocumentPageReviewTransitionException(
                        "Text suggestion has no vision assessment"
                ));

        if (!assessment.isCurrent()
                || assessment.getAssessmentType()
                != AssessmentType.VISION_TEXT_COMPARISON)
            throw new InvalidDocumentPageReviewTransitionException(
                    "Text suggestion assessment is no longer current"
            );
    }

    private ReviewTransition revokeApprovalIfRequired(
            @NonNull DocumentPage page,
            String reviewer,
            ReviewState nextReviewState,
            String reason
    ) {
        ReviewState previousReview = page.getReviewState();
        TranscriptionApprovalState previousApproval = page
                .getTranscriptionApprovalState();

        if(previousApproval != TranscriptionApprovalState.APPROVED)
            return new ReviewTransition(previousReview, previousApproval);

        reviewRepository.save(createEvent(
                page,
                ReviewAction.APPROVAL_REVOKED,
                reviewer,
                page.getCorrectedText(),
                page.getCorrectedTextHash(),
                previousReview,
                nextReviewState,
                previousApproval,
                TranscriptionApprovalState.PENDING,
                reason
        ));

        return new ReviewTransition(
                nextReviewState,
                TranscriptionApprovalState.PENDING
        );
    }

    private record ReviewTransition(
            ReviewState reviewState,
            TranscriptionApprovalState approvalState
    ) {
    }

    @Transactional
    public DocumentPageReviewDetails approve(
            Long pageId,
            PageApprovalCommand command,
            String reviewer
    ) {
        validatePageId(pageId);
        validateReviewer(reviewer);

        String notes = command == null ? null : command.notes();

        if (notes != null && notes.length() > 1000)
            throw new IllegalArgumentException(
                    "Approval notes cannot exceed 1000 characters"
            );

        DocumentPage page = requirePage(pageId);
        validateApproval(page);

        ReviewState previousReview = page.getReviewState();
        TranscriptionApprovalState previousApproval = page
                .getTranscriptionApprovalState();

        page.setReviewState(ReviewState.APPROVED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.APPROVED);
        page.setReviewer(reviewer.trim());
        page.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC));
        page.setReviewNotes(notes);
        pageRepository.save(page);
        if (chunkGenerationEligibilityService.isPageEligible(
                page.getDocument().getId(),
                page.getId()
        ))
            chunkGenerationService.generatePageIfRequired(
                    page.getDocument().getId(),
                    page.getId()
            );

        DocumentPageReview event = reviewRepository.saveAndFlush(
                createEvent(
                        page,
                        ReviewAction.APPROVED,
                        reviewer,
                        page.getCorrectedText(),
                        page.getCorrectedTextHash(),
                        previousReview,
                        ReviewState.APPROVED,
                        previousApproval,
                        TranscriptionApprovalState.APPROVED,
                        notes
                )
        );

        managementEvents.page(page, ManagementEvent.Action.STATUS_CHANGED);

        return historyMapper.toDetails(event);
    }

    @Transactional
    public DocumentPageReviewDetails reject(
            Long pageId,
            PageRejectionCommand command,
            String reviewer
    ) {
        validatePageId(pageId);
        validateReviewer(reviewer);

        if (command == null || isBlank(command.reason()))
            throw new IllegalArgumentException("Rejection reason is required");

        if (command.reason().length() > 1000)
            throw new IllegalArgumentException("Rejection reason cannot exceed 1000 characters");

        DocumentPage page = requirePage(pageId);
        ReviewState previousReview = page.getReviewState();
        TranscriptionApprovalState previousApproval = page
                .getTranscriptionApprovalState();

        page.setReviewState(ReviewState.REJECTED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.REJECTED);
        page.setReviewer(reviewer.trim());
        page.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC));
        page.setReviewNotes(command.reason().trim());

        chunkInvalidator.invalidate(page);
        pageRepository.save(page);

        DocumentPageReview event = reviewRepository.saveAndFlush(
                createEvent(
                        page,
                        ReviewAction.REJECTED,
                        reviewer,
                        page.getCorrectedText(),
                        page.getCorrectedTextHash(),
                        previousReview,
                        ReviewState.REJECTED,
                        previousApproval,
                        TranscriptionApprovalState.REJECTED,
                        command.reason().trim()
                )
        );

        managementEvents.page(page, ManagementEvent.Action.STATUS_CHANGED);

        return historyMapper.toDetails(event);
    }

    private void validateApproval(@NonNull DocumentPage page) {
        if (page.getProcessingState() != ProcessingState.COMPLETED)
            throw new InvalidDocumentPageReviewTransitionException(
                    "Only a completely processed page can be approved"
            );

        useRawOcrWhenUnedited(page);

        String expectedHash = ContentHashUtils.sha256(
                page.getCorrectedText()
        );

        if (!expectedHash.equals(page.getCorrectedTextHash()))
            throw new InvalidDocumentPageReviewTransitionException(
                    "Corrected text hash does not match the current text"
            );

        if (page.getTranscriptionApprovalState()
                == TranscriptionApprovalState.APPROVED)
            throw new InvalidDocumentPageReviewTransitionException(
                    "Document page transcription is already approved"
            );
    }

    private void useRawOcrWhenUnedited(@NonNull DocumentPage page) {
        if (!isBlank(page.getCorrectedText()))
            return;

        if (isBlank(page.getRawOcrText()))
            throw new InvalidDocumentPageReviewTransitionException(
                    "OCR or corrected text is required before approval"
            );

        page.setCorrectedText(page.getRawOcrText());
        page.setCorrectedTextHash(
                ContentHashUtils.sha256(page.getRawOcrText())
        );
    }

    private @NonNull DocumentPage requirePage(Long pageId) {
        return pageRepository.findByIdForUpdate(pageId)
                .orElseThrow(() -> new ResourceNotFoundException("Document page", pageId));
    }

    private @NonNull DocumentPageReview createEvent(
            DocumentPage page,
            ReviewAction action,
            @NonNull String reviewer,
            String correctedText,
            String correctedTextHash,
            ReviewState previousReview,
            ReviewState newReview,
            TranscriptionApprovalState previousApproval,
            TranscriptionApprovalState newApproval,
            String reason
    ) {
        return createEvent(
                page,
                null,
                action,
                reviewer,
                correctedText,
                correctedTextHash,
                previousReview,
                newReview,
                previousApproval,
                newApproval,
                reason
        );
    }

    private @NonNull DocumentPageReview createEvent(
            DocumentPage page,
            DocumentPageTextSuggestion sourceTextSuggestion,
            ReviewAction action,
            @NonNull String reviewer,
            String correctedText,
            String correctedTextHash,
            ReviewState previousReview,
            ReviewState newReview,
            TranscriptionApprovalState previousApproval,
            TranscriptionApprovalState newApproval,
            String reason
    ) {
        DocumentPageReview event = new DocumentPageReview();

        event.setDocumentPage(page);
        event.setSourceTextSuggestion(sourceTextSuggestion);
        event.setReviewAction(action);
        event.setReviewer(reviewer.trim());
        event.setCorrectedTextSnapshot(correctedText);
        event.setCorrectedTextHash(correctedTextHash);
        event.setPreviousReviewState(previousReview);
        event.setNewReviewState(newReview);
        event.setPreviousApprovalState(previousApproval);
        event.setNewApprovalState(newApproval);
        event.setReason(reason);

        return event;
    }

    private void validatePageId(Long pageId) {
        if (pageId == null)
            throw new IllegalArgumentException("Document page id is required");
    }

    private void validateReviewer(String reviewer) {
        if (isBlank(reviewer))
            throw new IllegalArgumentException("Authenticated reviewer is required");

        if (reviewer.trim().length() > 150)
            throw new IllegalArgumentException("Reviewer cannot exceed 150 characters");
    }
}

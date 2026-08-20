package fmi.ethnowear.application.service.document.review;

import fmi.ethnowear.application.dto.document.command.review.CorrectedTextSaveCommand;
import fmi.ethnowear.application.dto.document.command.review.PageApprovalCommand;
import fmi.ethnowear.application.dto.document.command.review.PageRejectionCommand;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageReviewDetails;
import fmi.ethnowear.application.exception.InvalidDocumentPageReviewTransitionException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.review.ReviewAction;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageReview;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageReviewRepository;
import fmi.ethnowear.util.ContentHashUtils;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
public class DocumentPageReviewService {

    private final DocumentPageRepository pageRepository;
    private final DocumentPageReviewRepository reviewRepository;
    private final DocumentPageChunkInvalidator chunkInvalidator;
    private final DocumentHistoryMapper historyMapper;

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
        ReviewState previousReview = page.getReviewState();
        TranscriptionApprovalState previousApproval = page
                .getTranscriptionApprovalState();

        if (previousApproval == TranscriptionApprovalState.APPROVED) {
            reviewRepository.save(createEvent(
                    page,
                    ReviewAction.APPROVAL_REVOKED,
                    reviewer,
                    page.getCorrectedText(),
                    page.getCorrectedTextHash(),
                    previousReview,
                    ReviewState.IN_REVIEW,
                    previousApproval,
                    TranscriptionApprovalState.PENDING,
                    "Corrected text changed"
            ));

            previousReview = ReviewState.IN_REVIEW;
            previousApproval = TranscriptionApprovalState.PENDING;
        }

        String hash = ContentHashUtils.sha256(command.correctedText());

        page.setCorrectedText(command.correctedText());
        page.setCorrectedTextHash(hash);
        page.setReviewState(ReviewState.IN_REVIEW);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);
        page.setReviewer(reviewer.trim());
        page.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC));

        chunkInvalidator.invalidate(page);
        pageRepository.save(page);

        DocumentPageReview event = reviewRepository.saveAndFlush(
                createEvent(
                        page,
                        ReviewAction.CORRECTED_TEXT_SAVED,
                        reviewer,
                        command.correctedText(),
                        hash,
                        previousReview,
                        ReviewState.IN_REVIEW,
                        previousApproval,
                        TranscriptionApprovalState.PENDING,
                        null
                )
        );

        return historyMapper.toDetails(event);
    }

    @Transactional
    public DocumentPageReviewDetails approve(
            Long pageId,
            PageApprovalCommand command,
            String reviewer
    ) {
        validatePageId(pageId);
        validateReviewer(reviewer);

        if (command == null)
            throw new IllegalArgumentException("Page approval command is required");

        if (command.notes() != null && command.notes().length() > 1000)
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
        page.setReviewNotes(command.notes());
        pageRepository.save(page);

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
                        command.notes()
                )
        );

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

        return historyMapper.toDetails(event);
    }

    private void validateApproval(@NonNull DocumentPage page) {
        if (page.getProcessingState() != ProcessingState.COMPLETED)
            throw new InvalidDocumentPageReviewTransitionException(
                    "Only a completely processed page can be approved"
            );

        if (isBlank(page.getCorrectedText()))
            throw new InvalidDocumentPageReviewTransitionException(
                    "Corrected text is required before approval"
            );

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
        DocumentPageReview event = new DocumentPageReview();

        event.setDocumentPage(page);
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

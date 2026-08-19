package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.dto.document.query.history.*;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.persistence.jpa.repository.document.*;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static fmi.ethnowear.util.PageableUtils.boundedUnsorted;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentHistoryQueryService {

    private static final int MAX_HISTORY_PAGE_SIZE = 100;
    private static final int MAX_RECENT_JOB_LIMIT = 25;

    private final DocumentQueryGuard queryGuard;
    private final DocumentPageOcrResultRepository ocrResultRepository;
    private final DocumentPageReviewRepository reviewRepository;
    private final DocumentPageProvenanceEventRepository provenanceEventRepository;
    private final DocumentProcessingJobRepository processingJobRepository;
    private final DocumentHistoryMapper historyMapper;

    public Page<DocumentPageOcrResultDetails> findOcrHistory(
            Long documentId,
            Long pageId,
            Pageable pageable
    ) {
        queryGuard.requirePage(documentId, pageId);

        return ocrResultRepository
                .findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
                        pageId,
                        historyPageable(pageable)
                )
                .map(historyMapper::toDetails);
    }

    public Optional<DocumentPageOcrResultDetails> findCurrentOcrResult(
            Long documentId,
            Long pageId
    ) {
        queryGuard.requirePage(documentId, pageId);

        return ocrResultRepository
                .findByDocumentPage_IdAndCurrentTrue(pageId)
                .map(historyMapper::toDetails);
    }

    public Page<DocumentPageReviewDetails> findReviewHistory(
            Long documentId,
            Long pageId,
            Pageable pageable
    ) {
        queryGuard.requirePage(documentId, pageId);

        return reviewRepository
                .findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
                        pageId,
                        historyPageable(pageable)
                )
                .map(historyMapper::toDetails);
    }

    public Page<DocumentPageProvenanceEventDetails> findProvenanceHistory(
            Long documentId,
            Long pageId,
            Pageable pageable
    ) {
        queryGuard.requirePage(documentId, pageId);

        return provenanceEventRepository
                .findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
                        pageId,
                        historyPageable(pageable)
                )
                .map(historyMapper::toDetails);
    }

    public Page<DocumentProcessingJobDetails> findDocumentJobs(
            Long documentId,
            Pageable pageable) {
        queryGuard.requireDocument(documentId);

        return processingJobRepository
                .findDocumentHistory(documentId, historyPageable(pageable))
                .map(historyMapper::toDetails);
    }

    public Page<DocumentProcessingJobDetails> findPageJobs(
            Long documentId,
            Long pageId,
            Pageable pageable
    ) {
        queryGuard.requirePage(documentId, pageId);

        return processingJobRepository
                .findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
                        pageId,
                        historyPageable(pageable)
                )
                .map(historyMapper::toDetails);
    }

    public BoundedHistoryDetails<DocumentProcessingJobDetails> findRecentDocumentJobs(
            Long documentId,
            int limit
    ) {
        queryGuard.requireDocument(documentId);
        validateRecentJobLimit(limit);

        return historyMapper.toBoundedJobHistory(
                processingJobRepository.findDocumentHistory(
                        documentId,
                        PageRequest.of(0, limit)
                )
        );
    }

    @Contract("null -> fail")
    private @NonNull Pageable historyPageable(Pageable pageable) {
        return boundedUnsorted(
                pageable,
                MAX_HISTORY_PAGE_SIZE,
                "History"
        );
    }

    private void validateRecentJobLimit(int limit) {
        if (limit <= 0 || limit > MAX_RECENT_JOB_LIMIT)
            throw new IllegalArgumentException("Recent job limit must be between 1 and " + MAX_RECENT_JOB_LIMIT);
    }
}

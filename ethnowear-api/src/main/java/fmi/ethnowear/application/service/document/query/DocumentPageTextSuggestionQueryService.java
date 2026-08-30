package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.dto.document.query.suggestion.DocumentPageTextSuggestionDetails;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.document.query.mapper.DocumentPageTextSuggestionMapper;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageTextSuggestionRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

import static fmi.ethnowear.util.PageableUtils.boundedUnsorted;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentPageTextSuggestionQueryService {

    private static final int MAX_HISTORY_PAGE_SIZE = 100;

    private final DocumentPageRepository pageRepository;
    private final DocumentPageOcrResultRepository ocrResultRepository;
    private final DocumentPageTextSuggestionRepository suggestionRepository;
    private final DocumentPageReviewRepository reviewRepository;
    private final DocumentPageTextSuggestionMapper mapper;

    public Optional<DocumentPageTextSuggestionDetails> findCurrent(Long pageId) {
        requirePage(pageId);

        return ocrResultRepository.findByDocumentPage_IdAndCurrentTrue(pageId)
                .flatMap(ocr -> suggestionRepository
                        .findFirstByDocumentPage_IdAndDocumentPageOcrResult_IdAndProcessingJob_StatusOrderByCreatedAtDescIdDesc(
                                pageId,
                                ocr.getId(),
                                JobStatus.SUCCEEDED
                        ))
                .map(suggestion -> mapper.toDetails(
                        suggestion,
                        reviewRepository.findBySourceTextSuggestion_IdAndSourceTextSuggestionIssueOrdinalIsNull(
                                suggestion.getId()
                        ).isPresent()
                ));
    }

    public Page<DocumentPageTextSuggestionDetails> findHistory(
            Long pageId,
            Pageable pageable
    ) {
        requirePage(pageId);

        Page<fmi.ethnowear.persistence.jpa.entity.document.DocumentPageTextSuggestion>
                suggestions = suggestionRepository
                .findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
                        pageId,
                        boundedUnsorted(
                                pageable,
                                MAX_HISTORY_PAGE_SIZE,
                                "Text suggestion history"
                        )
                );
        Set<Long> appliedIds = suggestions.isEmpty()
                ? Set.of()
                : reviewRepository.findAppliedSuggestionIds(
                        suggestions.stream().map(suggestion -> suggestion.getId()).toList()
                );

        return suggestions.map(suggestion -> mapper.toDetails(
                suggestion,
                appliedIds.contains(suggestion.getId())
        ));
    }

    private void requirePage(Long pageId) {
        if (pageId == null)
            throw new IllegalArgumentException("Document page id is required");

        if (!pageRepository.existsById(pageId))
            throw new ResourceNotFoundException("Document page", pageId);
    }
}

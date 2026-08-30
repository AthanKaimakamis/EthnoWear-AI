package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.dto.document.query.DocumentPageDetails;
import fmi.ethnowear.application.dto.document.query.DocumentPageSummaryDetails;
import fmi.ethnowear.application.dto.document.query.DocumentPageQueryDto;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.document.query.mapper.DocumentPageQueryMapper;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.util.Set;
import fmi.ethnowear.domain.model.media.MediaStorageState;

import static fmi.ethnowear.util.IdentifierUtils.requireId;
import static fmi.ethnowear.util.PageableUtils.boundedUnsorted;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentPageQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORTS = Set.of(
            "pageSequence",
            "pdfPageIndex",
            "printedPageSort",
            "processingState",
            "reviewState",
            "currentQualityScore",
            "currentQualityStatus",
            "currentQualityPassedChecks",
            "currentQualityFailedChecks",
            "currentQualityAssessedAt",
            "createdAt",
            "updatedAt"
    );

    private final DocumentQueryGuard queryGuard;
    private final DocumentPageRepository pageRepository;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final DocumentPageQueryMapper pageMapper;

    public Page<DocumentPageSummaryDetails> findByDocumentId(
            Long documentId,
            DocumentPageQueryDto query,
            Pageable pageable
    ) {
        queryGuard.requireDocument(documentId);
        DocumentPageQueryDto safeQuery = query == null
                ? new DocumentPageQueryDto(null, null, null)
                : query;
        validateScores(safeQuery);

        Page<DocumentPage> pages =
                pageRepository.findDocumentPages(
                        documentId,
                        safeQuery.qualityLevel(),
                        safeQuery.minimumQualityScore(),
                        safeQuery.maximumQualityScore(),
                        queryPageable(pageable)
                );

        Map<Long, Long> previewMediaIds =
                findPreviewMediaIds(pages.getContent());

        return pages.map(page -> pageMapper.toSummary(
                page,
                previewMediaIds.get(page.getId())
        ));
    }

    public Page<DocumentPageSummaryDetails> findByDocumentId(
            Long documentId,
            Pageable pageable
    ) {
        return findByDocumentId(documentId, null, pageable);
    }

    public DocumentPageDetails findById(
            Long documentId,
            Long pageId
    ) {
        requireId(documentId, "Document");
        requireId(pageId, "Document page");

        DocumentPage page = pageRepository
                .findByIdAndDocument_Id(pageId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document page", pageId));

        return pageMapper.toDetails(
                page,
                pageMediaRepository.findAvailableByDocumentPageId(
                        pageId,
                        MediaStorageState.AVAILABLE
                )
        );
    }

    private Map<Long, Long> findPreviewMediaIds(@NonNull List<DocumentPage> pages) {
        if (pages.isEmpty())
            return Map.of();

        List<Long> pageIds = pages.stream()
                .map(DocumentPage::getId)
                .toList();

        return pageMapper.toPreviewMediaIds(pageMediaRepository.findPreviewCandidates(pageIds));
    }

    @Contract("null -> fail")
    private @NonNull Pageable queryPageable(Pageable pageable) {
        Pageable bounded = fmi.ethnowear.util.PageableUtils.bounded(
                pageable,
                MAX_PAGE_SIZE,
                "Document page"
        );

        bounded.getSort().forEach(order -> {
            if (!ALLOWED_SORTS.contains(order.getProperty()))
                throw new IllegalArgumentException(
                        "Unsupported document page sort: " + order.getProperty()
                );
        });

        return bounded;
    }

    private void validateScores(@NonNull DocumentPageQueryDto query) {
        validateScore(query.minimumQualityScore(), "Minimum quality score");
        validateScore(query.maximumQualityScore(), "Maximum quality score");

        if (query.minimumQualityScore() != null
                && query.maximumQualityScore() != null
                && query.minimumQualityScore().compareTo(query.maximumQualityScore()) > 0)
            throw new IllegalArgumentException(
                    "Minimum quality score cannot exceed maximum quality score"
            );
    }

    private void validateScore(BigDecimal score, String field) {
        if (score != null
                && (score.signum() < 0 || score.compareTo(BigDecimal.ONE) > 0))
            throw new IllegalArgumentException(field + " must be between 0 and 1");
    }
}

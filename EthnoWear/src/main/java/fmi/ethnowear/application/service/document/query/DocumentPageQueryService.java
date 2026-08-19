package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.dto.document.query.DocumentPageDetails;
import fmi.ethnowear.application.dto.document.query.DocumentPageSummaryDetails;
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

import static fmi.ethnowear.util.IdentifierUtils.requireId;
import static fmi.ethnowear.util.PageableUtils.boundedUnsorted;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentPageQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentQueryGuard queryGuard;
    private final DocumentPageRepository pageRepository;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final DocumentPageQueryMapper pageMapper;

    public Page<DocumentPageSummaryDetails> findByDocumentId(
            Long documentId,
            Pageable pageable
    ) {
        queryGuard.requireDocument(documentId);

        Page<DocumentPage> pages =
                pageRepository.findByDocument_IdOrderByPageSequenceAsc(
                        documentId,
                        queryPageable(pageable)
                );

        Map<Long, Long> previewMediaIds =
                findPreviewMediaIds(pages.getContent());

        return pages.map(page -> pageMapper.toSummary(
                page,
                previewMediaIds.get(page.getId())
        ));
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
                pageMediaRepository.findByDocumentPage_IdOrderByDisplayOrderAscIdAsc(pageId)
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
        return boundedUnsorted(
                pageable,
                MAX_PAGE_SIZE,
                "Document page"
        );
    }
}

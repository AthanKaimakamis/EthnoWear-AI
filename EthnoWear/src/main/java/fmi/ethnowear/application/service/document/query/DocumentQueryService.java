package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.dto.document.query.DocumentDetails;
import fmi.ethnowear.application.dto.document.query.DocumentProgressDetails;
import fmi.ethnowear.application.dto.document.query.DocumentQueryDto;
import fmi.ethnowear.application.dto.document.query.DocumentSummaryDetails;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.document.query.mapper.DocumentQueryMapper;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static fmi.ethnowear.util.IdentifierUtils.requireId;
import static fmi.ethnowear.util.PageableUtils.bounded;
import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int RECENT_JOB_LIMIT = 10;

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id",
            "title",
            "author",
            "publisher",
            "publicationYear",
            "language",
            "documentType",
            "provenanceStatus",
            "processingState",
            "reviewState",
            "indexingState",
            "createdAt",
            "updatedAt"
    );

    private final DocumentRepository documentRepository;
    private final DocumentProgressQueryService progressQueryService;
    private final DocumentIndexingQueryService indexingQueryService;
    private final DocumentHistoryQueryService historyQueryService;
    private final DocumentQueryMapper documentMapper;

    public Page<DocumentSummaryDetails> findAll(
            DocumentQueryDto query,
            Pageable pageable
    ) {
        validateQuery(query);

        Page<Document> documents = documentRepository
                .findDocuments(
                        filterValue(query.searchText()),
                        query.documentType(),
                        query.provenanceStatus(),
                        query.provenanceTrustState(),
                        query.processingState(),
                        query.reviewState(),
                        query.indexingState(),
                        filterValue(query.language()),
                        query.sourceId(),
                        queryPageable(pageable)
                );

        List<Long> documentIds = documents.getContent()
                .stream()
                .map(Document::getId)
                .toList();

        Map<Long, DocumentProgressDetails> progress = progressQueryService
                .findByDocumentIds(documentIds);

        return documents.map(document -> documentMapper.toSummary(
                document,
                progress.get(document.getId())
        ));
    }

    public DocumentDetails findById(Long documentId) {
        requireId(documentId, "Document");

        Document document = documentRepository
                .findDetailsById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        DocumentProgressDetails progress = progressQueryService
                .findByDocumentIds(List.of(documentId))
                .get(documentId);

        return documentMapper.toDetails(
                document,
                progress,
                indexingQueryService
                        .findByDocuments(List.of(document))
                        .get(documentId),
                historyQueryService.findRecentDocumentJobs(
                        documentId,
                        RECENT_JOB_LIMIT
                )
        );
    }

    private void validateQuery(DocumentQueryDto query) {
        if (query == null)
            throw new IllegalArgumentException("Document query is required");

        if (query.sourceId() != null)
            requireId(query.sourceId(), "Source");
    }

    private @NonNull Pageable queryPageable(Pageable pageable) {
        Pageable result = bounded(
                pageable,
                MAX_PAGE_SIZE,
                "Document"
        );

        result.getSort().forEach(order -> {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty()))
                throw new IllegalArgumentException("Unsupported document sort property: " + order.getProperty());
        });

        return result;
    }

    private @Nullable String filterValue(String value) {
        return isBlank(value) ? null : value.trim();
    }
}

package fmi.ethnowear.application.service.document.indexing;

import fmi.ethnowear.application.dto.document.query.DocumentIndexingReconciliationDetails;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentPageIndexingStateCountProjection;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import fmi.ethnowear.persistence.jpa.repository.document.KnowledgeChunkPageRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
public class DocumentIndexingStateReconciler {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository pageRepository;
    private final KnowledgeChunkPageRepository chunkPageRepository;
    private final ManagementEventPublisher managementEvents;

    @Transactional
    public DocumentIndexingReconciliationDetails reconcile(Long documentId) {
        Document document = lockDocument(documentId);
        return reconcileLocked(document);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public @NonNull Document lockDocument(Long documentId) {
        requireId(documentId, "Document");

        return documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document",
                        documentId
                ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentIndexingReconciliationDetails reconcileLocked(
            @NonNull Document document
    ) {
        Long documentId = document.getId();
        requireId(documentId, "Document");

        List<DocumentPage> pages = pageRepository
                .findActiveByDocumentIdOrderByPageSequence(documentId);
        Map<Long, EnumMap<IndexingState, Long>> pageCounts = pageCounts(
                documentId
        );
        List<DocumentPage> changedPages = new ArrayList<>();

        for (DocumentPage page : pages) {
            IndexingState state = aggregate(pageCounts.get(page.getId()));

            if (page.getIndexingState() == state)
                continue;

            page.setIndexingState(state);
            changedPages.add(page);
        }

        IndexingState previousDocumentState = document.getIndexingState();
        IndexingState documentState = aggregatePages(pages);
        boolean documentChanged = previousDocumentState != documentState;

        if (!changedPages.isEmpty())
            pageRepository.saveAllAndFlush(changedPages);

        if (documentChanged) {
            document.setIndexingState(documentState);
            documentRepository.saveAndFlush(document);
        }

        changedPages.forEach(managementEvents::pageIndexingStateChanged);

        if (documentChanged)
            managementEvents.documentIndexingStateChanged(document);

        return new DocumentIndexingReconciliationDetails(
                documentId,
                previousDocumentState,
                documentState,
                changedPages.size(),
                documentChanged || !changedPages.isEmpty()
        );
    }

    private Map<Long, EnumMap<IndexingState, Long>> pageCounts(Long documentId) {
        Map<Long, EnumMap<IndexingState, Long>> result = new HashMap<>();

        for (DocumentPageIndexingStateCountProjection count
                : chunkPageRepository.countCurrentIndexingStatesByPage(
                        documentId
                ))
            result.computeIfAbsent(count.getPageId(), ignored -> counts())
                    .put(count.getState(), count.getTotal());

        return result;
    }

    private IndexingState aggregatePages(List<DocumentPage> pages) {
        if (pages.isEmpty())
            return IndexingState.NOT_ELIGIBLE;

        EnumMap<IndexingState, Long> counts = counts();
        pages.forEach(page -> counts.compute(
                page.getIndexingState(),
                (ignored, total) -> total == null ? 1L : total + 1L
        ));

        if (present(counts, IndexingState.OUTDATED))
            return IndexingState.OUTDATED;

        if (present(counts, IndexingState.FAILED))
            return IndexingState.FAILED;

        if (present(counts, IndexingState.PENDING))
            return IndexingState.PENDING;

        return counts.get(IndexingState.INDEXED) == pages.size()
                ? IndexingState.INDEXED
                : IndexingState.NOT_ELIGIBLE;
    }

    private IndexingState aggregate(Map<IndexingState, Long> counts) {
        if (counts == null || eligibleTotal(counts) == 0)
            return IndexingState.NOT_ELIGIBLE;

        if (present(counts, IndexingState.OUTDATED))
            return IndexingState.OUTDATED;

        if (present(counts, IndexingState.FAILED))
            return IndexingState.FAILED;

        if (present(counts, IndexingState.PENDING))
            return IndexingState.PENDING;

        return IndexingState.INDEXED;
    }

    private long eligibleTotal(Map<IndexingState, Long> counts) {
        return counts.entrySet().stream()
                .filter(entry -> entry.getKey() != IndexingState.NOT_ELIGIBLE)
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private boolean present(
            Map<IndexingState, Long> counts,
            IndexingState state
    ) {
        return counts.getOrDefault(state, 0L) > 0;
    }

    private EnumMap<IndexingState, Long> counts() {
        EnumMap<IndexingState, Long> counts = new EnumMap<>(
                IndexingState.class
        );

        for (IndexingState state : IndexingState.values())
            counts.put(state, 0L);

        return counts;
    }
}

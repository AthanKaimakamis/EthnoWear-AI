package fmi.ethnowear.application.service.document.indexing;

import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentPageIndexingStateCountProjection;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import fmi.ethnowear.persistence.jpa.repository.document.KnowledgeChunkPageRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentIndexingStateReconcilerTest {

    @Test
    void allActivePagesIndexedMakesDocumentIndexed() {
        Fixture fixture = fixture(
                IndexingState.PENDING,
                List.of(IndexingState.PENDING, IndexingState.PENDING),
                List.of(
                        pageCount(11L, IndexingState.INDEXED, 120),
                        pageCount(12L, IndexingState.INDEXED, 123)
                )
        );

        var result = fixture.reconciler().reconcile(1L);

        assertEquals(IndexingState.INDEXED, fixture.document().getIndexingState());
        assertTrue(fixture.pages().stream().allMatch(page ->
                page.getIndexingState() == IndexingState.INDEXED
        ));
        assertEquals(2, result.changedPageCount());
        verify(fixture.events(), times(2)).pageIndexingStateChanged(any());
        verify(fixture.events()).documentIndexingStateChanged(fixture.document());
    }

    @Test
    void outstandingPageKeepsDocumentPending() {
        Fixture fixture = fixture(
                IndexingState.PENDING,
                List.of(IndexingState.PENDING, IndexingState.PENDING),
                List.of(
                        pageCount(11L, IndexingState.INDEXED, 2),
                        pageCount(12L, IndexingState.PENDING, 1)
                )
        );

        fixture.reconciler().reconcile(1L);

        assertEquals(IndexingState.PENDING, fixture.document().getIndexingState());
        assertEquals(IndexingState.INDEXED, fixture.pages().getFirst().getIndexingState());
        assertEquals(IndexingState.PENDING, fixture.pages().getLast().getIndexingState());
        verify(fixture.events(), never()).documentIndexingStateChanged(any());
    }

    @Test
    void activePageWithoutChunksPreventsPrematureDocumentIndexing() {
        Fixture fixture = fixture(
                IndexingState.PENDING,
                List.of(IndexingState.PENDING, IndexingState.PENDING),
                List.of(pageCount(11L, IndexingState.INDEXED, 1))
        );

        fixture.reconciler().reconcile(1L);

        assertEquals(IndexingState.INDEXED, fixture.pages().getFirst().getIndexingState());
        assertEquals(IndexingState.NOT_ELIGIBLE, fixture.pages().getLast().getIndexingState());
        assertEquals(IndexingState.NOT_ELIGIBLE, fixture.document().getIndexingState());
    }

    @Test
    void pageStatePrecedenceIsOutdatedThenFailedThenPending() {
        assertAggregate(
                IndexingState.OUTDATED,
                List.of(IndexingState.OUTDATED, IndexingState.FAILED, IndexingState.PENDING)
        );
        assertAggregate(
                IndexingState.FAILED,
                List.of(IndexingState.FAILED, IndexingState.PENDING)
        );
        assertAggregate(
                IndexingState.PENDING,
                List.of(IndexingState.PENDING, IndexingState.INDEXED)
        );
    }

    @Test
    void repeatedReconciliationIsIdempotent() {
        Fixture fixture = fixture(
                IndexingState.PENDING,
                List.of(IndexingState.PENDING),
                List.of(pageCount(11L, IndexingState.INDEXED, 1))
        );

        assertTrue(fixture.reconciler().reconcile(1L).changed());
        assertFalse(fixture.reconciler().reconcile(1L).changed());

        verify(fixture.events(), times(1)).pageIndexingStateChanged(any());
        verify(fixture.events(), times(1)).documentIndexingStateChanged(any());
    }

    @Test
    void documentLockUsesPessimisticWrite() throws NoSuchMethodException {
        Method method = DocumentRepository.class.getMethod(
                "findByIdForUpdate",
                Long.class
        );

        assertEquals(
                LockModeType.PESSIMISTIC_WRITE,
                method.getAnnotation(Lock.class).value()
        );
    }

    @Test
    void pageAggregateQueryExcludesSupersededChunksAndInactivePages()
            throws NoSuchMethodException {
        Method pageCounts = KnowledgeChunkPageRepository.class.getMethod(
                "countCurrentIndexingStatesByPage",
                Long.class
        );
        String query = pageCounts.getAnnotation(Query.class).value();

        assertTrue(query.contains("chunk.supersededBy IS NULL"));
        assertTrue(query.contains("EvidenceState.ACTIVE"));
    }

    private void assertAggregate(
            IndexingState expected,
            List<IndexingState> pageStates
    ) {
        List<DocumentPageIndexingStateCountProjection> counts =
                java.util.stream.IntStream.range(0, pageStates.size())
                        .mapToObj(index -> pageCount(
                                11L + index,
                                pageStates.get(index),
                                1
                        ))
                        .toList();
        Fixture fixture = fixture(
                IndexingState.INDEXED,
                java.util.Collections.nCopies(
                        pageStates.size(),
                        IndexingState.INDEXED
                ),
                counts
        );

        fixture.reconciler().reconcile(1L);

        assertEquals(expected, fixture.document().getIndexingState());
    }

    private Fixture fixture(
            IndexingState documentState,
            List<IndexingState> pageStates,
            List<DocumentPageIndexingStateCountProjection> pageCounts
    ) {
        Document document = new Document();
        EntityTestUtils.setId(document, 1L);
        document.setIndexingState(documentState);

        List<DocumentPage> pages = java.util.stream.IntStream
                .range(0, pageStates.size())
                .mapToObj(index -> {
                    DocumentPage page = new DocumentPage();
                    EntityTestUtils.setId(page, 11L + index);
                    page.setDocument(document);
                    page.setPageSequence(index + 1);
                    page.setIndexingState(pageStates.get(index));
                    return page;
                })
                .toList();

        DocumentRepository documents = mock(DocumentRepository.class);
        when(documents.findByIdForUpdate(1L)).thenReturn(Optional.of(document));
        when(documents.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        DocumentPageRepository pageRepository = mock(DocumentPageRepository.class);
        when(pageRepository.findActiveByDocumentIdOrderByPageSequence(1L))
                .thenReturn(pages);
        when(pageRepository.saveAllAndFlush(any())).thenAnswer(call ->
                call.getArgument(0)
        );

        KnowledgeChunkPageRepository links = mock(KnowledgeChunkPageRepository.class);
        when(links.countCurrentIndexingStatesByPage(1L)).thenReturn(pageCounts);

        ManagementEventPublisher events = mock(ManagementEventPublisher.class);

        return new Fixture(
                new DocumentIndexingStateReconciler(
                        documents,
                        pageRepository,
                        links,
                        events
                ),
                document,
                pages,
                events
        );
    }

    private DocumentPageIndexingStateCountProjection pageCount(
            Long pageId,
            IndexingState state,
            long total
    ) {
        return new PageCount(pageId, state, total);
    }

    private record PageCount(
            Long pageId,
            IndexingState state,
            long total
    ) implements DocumentPageIndexingStateCountProjection {

        @Override
        public Long getPageId() {
            return pageId;
        }

        @Override
        public IndexingState getState() {
            return state;
        }

        @Override
        public long getTotal() {
            return total;
        }
    }

    private record Fixture(
            DocumentIndexingStateReconciler reconciler,
            Document document,
            List<DocumentPage> pages,
            ManagementEventPublisher events
    ) {
    }
}

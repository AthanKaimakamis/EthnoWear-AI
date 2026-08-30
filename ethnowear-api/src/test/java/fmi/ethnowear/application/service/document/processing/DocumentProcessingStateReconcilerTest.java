package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentProcessingStateReconcilerTest {

    @Test
    void allActivePagesCompletedMakesDocumentCompleted() {
        Fixture fixture = fixture(
                ProcessingState.PROCESSING,
                ProcessingState.COMPLETED,
                ProcessingState.COMPLETED
        );

        assertTrue(fixture.reconciler().reconcile(1L));
        assertEquals(
                ProcessingState.COMPLETED,
                fixture.document().getProcessingState()
        );
        verify(fixture.events()).document(
                fixture.document(),
                ManagementEvent.Action.STATUS_CHANGED
        );
    }

    @Test
    void pendingPageKeepsDocumentProcessingWithoutDuplicateEvent() {
        Fixture fixture = fixture(
                ProcessingState.PROCESSING,
                ProcessingState.COMPLETED,
                ProcessingState.PENDING
        );

        assertFalse(fixture.reconciler().reconcile(1L));
        verifyNoInteractions(fixture.events());
    }

    @Test
    void terminalPageFailureTakesPrecedence() {
        Fixture fixture = fixture(
                ProcessingState.PROCESSING,
                ProcessingState.COMPLETED,
                ProcessingState.FAILED
        );

        assertTrue(fixture.reconciler().reconcile(1L));
        assertEquals(
                ProcessingState.FAILED,
                fixture.document().getProcessingState()
        );
    }

    private Fixture fixture(
            ProcessingState documentState,
            ProcessingState... pageStates
    ) {
        Document document = new Document();
        EntityTestUtils.setId(document, 1L);
        document.setProcessingState(documentState);

        List<DocumentPage> pages = java.util.stream.IntStream
                .range(0, pageStates.length)
                .mapToObj(index -> {
                    DocumentPage page = new DocumentPage();
                    EntityTestUtils.setId(page, 11L + index);
                    page.setDocument(document);
                    page.setPageSequence(index + 1);
                    page.setProcessingState(pageStates[index]);
                    return page;
                })
                .toList();

        DocumentRepository documents = mock(DocumentRepository.class);
        when(documents.findByIdForUpdate(1L)).thenReturn(Optional.of(document));
        when(documents.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        DocumentPageRepository pageRepository = mock(DocumentPageRepository.class);
        when(pageRepository.findActiveByDocumentIdOrderByPageSequence(1L))
                .thenReturn(pages);

        ManagementEventPublisher events = mock(ManagementEventPublisher.class);

        return new Fixture(
                new DocumentProcessingStateReconciler(
                        documents,
                        pageRepository,
                        events
                ),
                document,
                events
        );
    }

    private record Fixture(
            DocumentProcessingStateReconciler reconciler,
            Document document,
            ManagementEventPublisher events
    ) {
    }
}

package fmi.ethnowear.application.service.document.lifecycle;

import fmi.ethnowear.application.exception.DocumentDependencyConflictException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.archive.media.storage.MediaFileCompensation;
import fmi.ethnowear.application.service.archive.media.storage.MediaPathResolver;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.application.port.retrieval.VectorDeletionGateway;
import fmi.ethnowear.application.exception.RetrievalUnavailableException;
import fmi.ethnowear.persistence.jdbc.document.DocumentDeletionStore;
import fmi.ethnowear.persistence.jdbc.document.DocumentDeletionStore.DeletedMediaStorageKeys;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DocumentDeletionServiceTest {

    private DocumentRepository documentRepository;
    private DocumentDeletionStore deletionStore;
    private MediaPathResolver paths;
    private MediaFileCompensation fileCompensation;
    private VectorDeletionGateway vectorDeletionGateway;
    private ManagementEventPublisher managementEvents;
    private DocumentDeletionService service;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DocumentRepository.class);
        deletionStore = mock(DocumentDeletionStore.class);
        paths = mock(MediaPathResolver.class);
        fileCompensation = mock(MediaFileCompensation.class);
        vectorDeletionGateway = mock(VectorDeletionGateway.class);
        managementEvents = mock(ManagementEventPublisher.class);
        service = new DocumentDeletionService(
                documentRepository,
                deletionStore,
                paths,
                fileCompensation,
                vectorDeletionGateway,
                managementEvents
        );
    }

    @Test
    void deletesAggregateAndSchedulesUniqueFilesAfterCommit() throws Exception {
        when(documentRepository.existsById(7L)).thenReturn(true);
        when(deletionStore.hasActiveJobs(7L)).thenReturn(false);
        when(deletionStore.findIndexedKnowledgeChunkIds(7L))
                .thenReturn(List.of(101L, 102L));
        when(deletionStore.delete(7L)).thenReturn(List.of(
                new DeletedMediaStorageKeys(
                        "documents/7/original/book.pdf",
                        null
                ),
                new DeletedMediaStorageKeys(
                        "documents/7/pages/page.jpg",
                        "documents/7/thumbnails/page.jpg"
                )
        ));
        Path original = Path.of("/media/book.pdf");
        Path page = Path.of("/media/page.jpg");
        Path thumbnail = Path.of("/media/thumbnail.jpg");
        when(paths.resolveExisting("documents/7/original/book.pdf"))
                .thenReturn(original);
        when(paths.resolveExisting("documents/7/pages/page.jpg"))
                .thenReturn(page);
        when(paths.resolveExisting("documents/7/thumbnails/page.jpg"))
                .thenReturn(thumbnail);

        service.delete(7L, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Path>> files = ArgumentCaptor.forClass(
                Collection.class
        );
        verify(fileCompensation).registerCommitCleanup(files.capture());
        verify(vectorDeletionGateway)
                .deleteKnowledgeChunks(List.of(101L, 102L));
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(original, page, thumbnail),
                files.getValue().stream().toList()
        );
        verify(managementEvents).documentDeleted(7L);
    }

    @Test
    void requiresExplicitConfirmation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(7L, false)
        );

        verifyNoInteractions(documentRepository, deletionStore);
    }

    @Test
    void rejectsMissingDocument() {
        when(documentRepository.existsById(7L)).thenReturn(false);

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.delete(7L, true)
        );

        verifyNoInteractions(deletionStore);
    }

    @Test
    void rejectsDocumentWithActiveJobs() {
        when(documentRepository.existsById(7L)).thenReturn(true);
        when(deletionStore.hasActiveJobs(7L)).thenReturn(true);

        assertThrows(
                DocumentDependencyConflictException.class,
                () -> service.delete(7L, true)
        );

        verify(deletionStore, never()).delete(anyLong());
    }

    @Test
    void mapsForeignKeyConflictToStableDocumentConflict() {
        when(documentRepository.existsById(7L)).thenReturn(true);
        when(deletionStore.hasActiveJobs(7L)).thenReturn(false);
        when(deletionStore.delete(7L)).thenThrow(
                new DataIntegrityViolationException("foreign key")
        );

        assertThrows(
                DocumentDependencyConflictException.class,
                () -> service.delete(7L, true)
        );

        verifyNoInteractions(fileCompensation, managementEvents);
    }

    @Test
    void mapsExplicitCrossDocumentConflictToStableDocumentConflict() {
        when(documentRepository.existsById(7L)).thenReturn(true);
        when(deletionStore.hasActiveJobs(7L)).thenReturn(false);
        SQLException sql = new SQLException(
                "cross-document dependency",
                "S0001",
                51001
        );
        when(deletionStore.delete(7L)).thenThrow(
                new UncategorizedSQLException("delete", "batch", sql)
        );

        assertThrows(
                DocumentDependencyConflictException.class,
                () -> service.delete(7L, true)
        );
    }

    @Test
    void stopsCleanupWhenVectorDeletionFails() {
        when(documentRepository.existsById(7L)).thenReturn(true);
        when(deletionStore.hasActiveJobs(7L)).thenReturn(false);
        when(deletionStore.findIndexedKnowledgeChunkIds(7L))
                .thenReturn(List.of(101L));
        when(deletionStore.delete(7L)).thenReturn(List.of());
        doThrow(new RetrievalUnavailableException("unavailable"))
                .when(vectorDeletionGateway)
                .deleteKnowledgeChunks(List.of(101L));

        assertThrows(
                RetrievalUnavailableException.class,
                () -> service.delete(7L, true)
        );

        verifyNoInteractions(fileCompensation, managementEvents);
    }
}

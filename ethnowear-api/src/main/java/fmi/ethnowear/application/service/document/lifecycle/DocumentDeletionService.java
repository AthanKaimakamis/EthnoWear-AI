package fmi.ethnowear.application.service.document.lifecycle;

import fmi.ethnowear.application.exception.DocumentDependencyConflictException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.archive.media.storage.MediaFileCompensation;
import fmi.ethnowear.application.service.archive.media.storage.MediaPathResolver;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.application.port.retrieval.VectorDeletionGateway;
import fmi.ethnowear.persistence.jdbc.document.DocumentDeletionStore;
import fmi.ethnowear.persistence.jdbc.document.DocumentDeletionStore.DeletedMediaStorageKeys;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
public class DocumentDeletionService {

    private final DocumentRepository documentRepository;
    private final DocumentDeletionStore deletionStore;
    private final MediaPathResolver paths;
    private final MediaFileCompensation fileCompensation;
    private final VectorDeletionGateway vectorDeletionGateway;
    private final ManagementEventPublisher managementEvents;

    @Transactional
    public void delete(Long documentId, boolean confirmed) {
        if(documentId == null || documentId <= 0)
            throw new IllegalArgumentException("Document id must be positive");

        if(!confirmed)
            throw new IllegalArgumentException(
                    "Document deletion must be explicitly confirmed"
            );

        if(!documentRepository.existsById(documentId))
            throw new ResourceNotFoundException("Document", documentId);

        if(deletionStore.hasActiveJobs(documentId))
            throw new DocumentDependencyConflictException(
                    "Document has active processing jobs"
            );

        try {
            List<Long> indexedChunkIds = deletionStore
                    .findIndexedKnowledgeChunkIds(documentId);
            List<Path> deletedFiles = deletionStore.delete(documentId)
                    .stream()
                    .flatMap(this::storageKeys)
                    .distinct()
                    .toList();

            vectorDeletionGateway.deleteKnowledgeChunks(indexedChunkIds);
            fileCompensation.registerCommitCleanup(deletedFiles);
            managementEvents.documentDeleted(documentId);
        } catch (DataAccessException ex) {
            if(isDependencyConflict(ex))
                throw new DocumentDependencyConflictException(
                        "Document is referenced by data outside its deletion scope"
                );

            throw ex;
        }
    }

    private Stream<Path> storageKeys(DeletedMediaStorageKeys keys) {
        return Stream.of(keys.filePath(), keys.thumbnailPath())
                .filter(value -> !isBlank(value))
                .map(this::resolve);
    }

    private Path resolve(String storageKey) {
        try {
            return paths.resolveExisting(storageKey);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not resolve document media for deletion",
                    ex
            );
        }
    }

    private boolean isDependencyConflict(DataAccessException exception) {
        if(exception instanceof DataIntegrityViolationException)
            return true;

        Throwable cause = exception;

        while(cause != null) {
            if(cause instanceof SQLException sql && sql.getErrorCode() == 51001)
                return true;

            cause = cause.getCause();
        }

        return false;
    }
}

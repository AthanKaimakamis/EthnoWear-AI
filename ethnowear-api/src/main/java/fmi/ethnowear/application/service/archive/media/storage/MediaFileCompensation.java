package fmi.ethnowear.application.service.archive.media.storage;

import org.jspecify.annotations.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Component
@Slf4j
public class MediaFileCompensation {

    public void registerCommitCleanup(Collection<Path> paths) {
        if(!TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException(
                    "Commit cleanup requires an active transaction"
            );

        List<Path> committedPaths = paths.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if(committedPaths.isEmpty())
            return;

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        deleteQuietly(committedPaths);
                    }
                }
        );
    }

    public void registerRollbackCleanup(Collection<Path> paths) {
        if(!TransactionSynchronizationManager.isSynchronizationActive())
            return;

        List<Path> createdPaths = paths.stream()
                .filter(Objects::nonNull)
                .toList();

        if(createdPaths.isEmpty())
            return;

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCompletion(int status) {
                        if(status != STATUS_COMMITTED)
                            deleteQuietly(createdPaths);
                    }
                }
        );
    }

    public void deleteQuietly(@NonNull Collection<Path> paths) {
        paths.stream()
                .filter(Objects::nonNull)
                .forEach(this::deleteQuietly);
    }

    public void deleteQuietly(Path path) {
        if (path == null)
            return;

        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.error("Could not delete managed media file: {}", path, ex);
        }
    }
}

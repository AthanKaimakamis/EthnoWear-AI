package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.application.service.archive.media.storage.MediaFileCompensation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaFileCompensationTest {

    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void clearSynchronization() {
        if(TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void deletesRegisteredFilesOnlyAfterCommit() throws Exception {
        Path file = Files.createFile(temporaryDirectory.resolve("media.bin"));
        TransactionSynchronizationManager.initSynchronization();

        new MediaFileCompensation().registerCommitCleanup(List.of(file));

        assertTrue(Files.exists(file));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());
        assertFalse(Files.exists(file));
    }
}

package fmi.ethnowear.application.service.worker.health;

import fmi.ethnowear.application.dto.worker.health.WorkerReadinessDetails;
import fmi.ethnowear.application.exception.WorkerNotReadyException;
import fmi.ethnowear.application.service.archive.media.storage.MediaPathResolver;
import fmi.ethnowear.config.MediaStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;

import static fmi.ethnowear.support.WorkerTestFixtures.properties;
import static org.junit.jupiter.api.Assertions.*;

class WorkerReadinessServiceTest {

    @TempDir
    Path mediaRoot;

    @Test
    void reportsReadyWhenDatabaseAndPermanentStorageAreAvailable() {
        WorkerReadinessDetails result = service(jdbc(1, null), mediaRoot).check();

        assertEquals("READY", result.status());
        assertTrue(result.workerApiEnabled());
    }

    @Test
    void rejectsUnavailableDatabase() {
        assertThrows(
                WorkerNotReadyException.class,
                () -> service(
                        jdbc(null, new DataAccessResourceFailureException("unavailable")),
                        mediaRoot
                ).check()
        );
    }

    @Test
    void rejectsUnavailablePermanentStorage() {
        assertThrows(
                WorkerNotReadyException.class,
                () -> service(jdbc(1, null), mediaRoot.resolve("missing")).check()
        );
    }

    private JdbcTemplate jdbc(Integer result, RuntimeException failure) {
        return new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType) {
                if(failure != null)
                    throw failure;

                return requiredType.cast(result);
            }
        };
    }

    private WorkerReadinessService service(JdbcTemplate jdbcTemplate, Path root) {
        MediaStorageProperties mediaProperties = new MediaStorageProperties();
        mediaProperties.setStorageRoot(root);

        return new WorkerReadinessService(
                properties(),
                jdbcTemplate,
                new MediaPathResolver(mediaProperties)
        );
    }
}

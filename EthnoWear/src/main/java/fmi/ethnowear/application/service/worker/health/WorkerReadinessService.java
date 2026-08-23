package fmi.ethnowear.application.service.worker.health;

import fmi.ethnowear.application.dto.worker.health.WorkerReadinessDetails;
import fmi.ethnowear.application.exception.WorkerNotReadyException;
import fmi.ethnowear.application.service.archive.media.storage.MediaPathResolver;
import fmi.ethnowear.config.WorkerApiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class WorkerReadinessService {

    private final WorkerApiProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final MediaPathResolver mediaPathResolver;

    public WorkerReadinessDetails check() {
        if(!properties.enabled())
            throw new WorkerNotReadyException("The internal worker API is disabled");

        checkDatabase();
        checkMediaStorage();

        return new WorkerReadinessDetails("READY", true);
    }

    private void checkDatabase() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            if(result == null || result != 1)
                throw new WorkerNotReadyException("Database readiness check failed");
        } catch(DataAccessException ex) {
            throw new WorkerNotReadyException("Database is unavailable", ex);
        }
    }

    private void checkMediaStorage() {
        Path root = mediaPathResolver.root();

        try {
            Path realRoot = root.toRealPath();

            if(!Files.isDirectory(realRoot)
                    || !Files.isReadable(realRoot)
                    || !Files.isWritable(realRoot))
                throw new WorkerNotReadyException("Permanent media storage is unavailable");
        } catch(IOException ex) {
            throw new WorkerNotReadyException("Permanent media storage is unavailable", ex);
        }
    }
}

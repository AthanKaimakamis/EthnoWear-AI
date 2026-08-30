package fmi.ethnowear.application.service.document.chunk;

import fmi.ethnowear.config.DocumentChunkingProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledFuture;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ethnowear.document.chunking",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ChunkGenerationJobExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ChunkGenerationJobExecutor.class
    );

    private final ChunkGenerationJobStateService stateService;
    private final DocumentChunkGenerationService generationService;
    private final DocumentChunkingProperties properties;
    private final TaskScheduler taskScheduler;

    @Scheduled(
            fixedDelayString = "${ethnowear.document.chunking.execution-interval:5s}",
            initialDelayString = "${ethnowear.document.chunking.execution-interval:5s}"
    )
    public void executeNextDueJob() {
        var job = stateService.claimNextDue();

        if(job == null)
            return;

        ScheduledFuture<?> heartbeat = scheduleHeartbeat(job.jobId());
        try {
            if(!stateService.heartbeat(job.jobId())) {
                stateService.complete(job.jobId(), 0);
                return;
            }

            var result = generationService.generate(
                    job.documentId(),
                    job.generationInputHash()
            );
            stateService.complete(job.jobId(), result.chunks().size());
        } catch (RuntimeException ex) {
            LOGGER.error(
                    "Chunk-generation job {} failed",
                    job.jobId(),
                    ex
            );
            stateService.fail(job.jobId(), ex);
        } finally {
            heartbeat.cancel(false);
        }
    }

    private ScheduledFuture<?> scheduleHeartbeat(Long jobId) {
        return taskScheduler.scheduleAtFixedRate(
                () -> renewLease(jobId),
                properties.getHeartbeatInterval()
        );
    }

    private void renewLease(Long jobId) {
        try {
            stateService.heartbeat(jobId);
        } catch (RuntimeException ex) {
            LOGGER.warn(
                    "Could not renew lease for chunk-generation job {}",
                    jobId,
                    ex
            );
        }
    }
}

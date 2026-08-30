package fmi.ethnowear.application.service.document.retention;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ethnowear.media.retention",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MediaCleanupJobExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            MediaCleanupJobExecutor.class
    );

    private final MediaCleanupJobStateService stateService;
    private final MediaCleanupExecutionService executionService;

    @Scheduled(
            fixedDelayString = "${ethnowear.media.retention.execution-interval:1m}",
            initialDelayString = "${ethnowear.media.retention.execution-interval:1m}"
    )
    public void executeNextDueJob() {
        Long jobId = stateService.claimNextDue();

        if (jobId == null)
            return;

        try {
            int purged = executionService.execute(jobId);
            stateService.complete(jobId, purged);
        } catch (RuntimeException ex) {
            LOGGER.error("Media-cleanup job {} failed", jobId, ex);
            stateService.fail(jobId, ex);
        }
    }
}

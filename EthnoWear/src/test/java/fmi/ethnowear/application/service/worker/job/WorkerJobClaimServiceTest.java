package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.job.WorkerJobClaimCommand;
import fmi.ethnowear.application.dto.worker.job.WorkerJobClaimDetails;
import fmi.ethnowear.application.dto.worker.job.WorkerJobType;
import fmi.ethnowear.application.model.worker.ClaimedWorkerJob;
import fmi.ethnowear.application.port.worker.WorkerJobClaimStore;
import fmi.ethnowear.application.service.worker.security.WorkerClaimTokenService;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.util.ContentHashUtils;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static fmi.ethnowear.support.WorkerTestFixtures.NOW;
import static fmi.ethnowear.support.WorkerTestFixtures.properties;
import static org.junit.jupiter.api.Assertions.*;

class WorkerJobClaimServiceTest {

    @Test
    void returnsOpaqueAttemptTokenAndConfiguredLimits() {
        AtomicReference<String> storedHash = new AtomicReference<>();
        WorkerJobClaimStore store = (
                types,
                workerId,
                claimedAt,
                leaseExpiresAt,
                timeoutAt,
                claimTokenHash
        ) -> {
            assertEquals(Set.of(JobType.PAGE_EXTRACTION), types);
            assertEquals("page-extractor-1", workerId);
            assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), claimedAt);
            assertEquals(claimedAt.plusSeconds(120), leaseExpiresAt);
            assertEquals(claimedAt.plusMinutes(30), timeoutAt);
            storedHash.set(claimTokenHash);
            return Optional.of(new ClaimedWorkerJob(
                    11L,
                    JobType.PAGE_EXTRACTION,
                    7L,
                    null,
                    null,
                    true,
                    2,
                    claimedAt,
                    leaseExpiresAt
            ));
        };
        WorkerJobClaimService service = new WorkerJobClaimService(
                store,
                new WorkerClaimTokenService(),
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        WorkerJobClaimDetails result = service.claim(command()).orElseThrow();

        assertFalse(result.claimToken().isBlank());
        assertEquals(ContentHashUtils.sha256(result.claimToken()), storedHash.get());
        assertEquals(2, result.attempt());
        assertEquals(250L * 1024 * 1024, result.limits().maximumInputBytes());
        assertEquals(300, result.limits().renderDpi());
        assertEquals(100000000, result.limits().maximumPagePixels());
    }

    @Test
    void returnsEmptyWhenAtomicStoreFindsNoJobAndRejectsInvalidLease() {
        WorkerJobClaimStore emptyStore = (
                types,
                workerId,
                claimedAt,
                leaseExpiresAt,
                timeoutAt,
                claimTokenHash
        ) -> Optional.empty();
        WorkerJobClaimService service = new WorkerJobClaimService(
                emptyStore,
                new WorkerClaimTokenService(),
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertTrue(service.claim(command()).isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.claim(new WorkerJobClaimCommand(
                        "page-extractor-1",
                        Set.of(WorkerJobType.PAGE_EXTRACTION),
                        10
                ))
        );
    }

    private WorkerJobClaimCommand command() {
        return new WorkerJobClaimCommand(
                "page-extractor-1",
                Set.of(WorkerJobType.PAGE_EXTRACTION),
                120
        );
    }
}

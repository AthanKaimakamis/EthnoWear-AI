package fmi.ethnowear.persistence.jdbc.document;

import fmi.ethnowear.application.model.worker.ClaimedWorkerJob;
import fmi.ethnowear.domain.model.document.processing.JobType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@JdbcTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({
        SqlServerWorkerJobClaimStore.class,
        SqlServerWorkerJobRecoveryStore.class
})
@EnabledIfEnvironmentVariable(named = "ETHNOWEAR_LIVE_DB_TESTS", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SqlServerWorkerJobConcurrencyTest {

    private static final String TEST_KEY_PREFIX = "TEST:WORKER:";
    private static final String CLAIM_TOKEN_HASH = "a".repeat(64);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SqlServerWorkerJobClaimStore claimStore;

    @Autowired
    private SqlServerWorkerJobRecoveryStore recoveryStore;

    private UUID correlationId;

    @AfterEach
    void removeTestJobs() {
        if(correlationId != null)
            jdbc.update(
                    "DELETE FROM ethnowear.DocumentProcessingJobs WHERE CorrelationId = ?",
                    correlationId.toString()
            );
    }

    @Test
    void onlyOneWorkerClaimsTheSameEligibleJob() throws Exception {
        JobType jobType = unusedEligibleJobType();
        assumeTrue(jobType != null, "Live database has eligible jobs for every job type");

        correlationId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        long jobId = insertQueuedJob(jobType, now.minusMinutes(1));
        CountDownLatch start = new CountDownLatch(1);

        try(var executor = Executors.newFixedThreadPool(2)) {
            Future<Optional<ClaimedWorkerJob>> first = executor.submit(() -> {
                start.await();
                return claim(jobType, "worker-one", now);
            });
            Future<Optional<ClaimedWorkerJob>> second = executor.submit(() -> {
                start.await();
                return claim(jobType, "worker-two", now);
            });

            start.countDown();

            assertThat(java.util.stream.Stream.of(first.get(), second.get())
                    .flatMap(Optional::stream)
                    .map(ClaimedWorkerJob::jobId)
                    .filter(id -> id == jobId))
                    .containsExactly(jobId);
        }

        assertThat(jdbc.queryForObject(
                "SELECT Status FROM ethnowear.DocumentProcessingJobs WHERE Id = ?",
                String.class,
                jobId
        )).isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject(
                "SELECT AttemptCount FROM ethnowear.DocumentProcessingJobs WHERE Id = ?",
                Integer.class,
                jobId
        )).isEqualTo(1);
    }

    @Test
    void onlyOneRecoveryCallerRecoversAnExpiredClaim() throws Exception {
        Integer expiredJobs = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM ethnowear.DocumentProcessingJobs
                WHERE Status IN (N'RUNNING', N'CANCEL_REQUESTED')
                  AND (ClaimExpiresAt <= SYSUTCDATETIME() OR TimeoutAt <= SYSUTCDATETIME())
                """, Integer.class);
        assumeTrue(expiredJobs != null && expiredJobs == 0, "Live database already has expired jobs");

        correlationId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        long jobId = insertExpiredRunningJob(now);
        CountDownLatch start = new CountDownLatch(1);

        try(var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> {
                start.await();
                return recoveryStore.recoverExpired(now, 100);
            });
            Future<Integer> second = executor.submit(() -> {
                start.await();
                return recoveryStore.recoverExpired(now, 100);
            });

            start.countDown();
            assertThat(first.get() + second.get()).isEqualTo(1);
        }

        var recovered = jdbc.queryForMap(
                """
                SELECT Status, ActiveJobKey, ClaimedBy, ClaimTokenHash, ErrorCode
                FROM ethnowear.DocumentProcessingJobs
                WHERE Id = ?
                """,
                jobId
        );
        assertThat(recovered)
                .containsEntry("Status", "RETRY_WAIT")
                .containsEntry("ErrorCode", "WORKER_LEASE_EXPIRED");
        assertThat(recovered.get("ActiveJobKey")).isNotNull();
        assertThat(recovered.get("ClaimedBy")).isNull();
        assertThat(recovered.get("ClaimTokenHash")).isNull();
    }

    @Test
    void heartbeatAndRecoveryProduceOneCoherentWinner() throws Exception {
        assumeNoExpiredJobs();

        correlationId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        long jobId = insertRunningJob(now, now.plusSeconds(1));
        CountDownLatch start = new CountDownLatch(1);

        try(var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> heartbeat = executor.submit(() -> {
                start.await();
                return jdbc.update("""
                        UPDATE ethnowear.DocumentProcessingJobs WITH (ROWLOCK)
                        SET ClaimExpiresAt = ?, UpdatedAt = ?
                        WHERE Id = ?
                          AND Status = N'RUNNING'
                          AND ClaimedBy = N'test-worker'
                          AND ClaimTokenHash = ?
                          AND ClaimExpiresAt > ?
                        """,
                        now.plusMinutes(2),
                        now,
                        jobId,
                        CLAIM_TOKEN_HASH,
                        now
                );
            });
            Future<Integer> recovery = executor.submit(() -> {
                start.await();
                return recoveryStore.recoverExpired(now.plusSeconds(2), 100);
            });

            start.countDown();
            assertThat(heartbeat.get() + recovery.get()).isEqualTo(1);
        }

        var result = jdbc.queryForMap(
                "SELECT Status, ClaimedBy, ClaimTokenHash FROM ethnowear.DocumentProcessingJobs WHERE Id = ?",
                jobId
        );
        assertThat(result.get("Status")).isIn("RUNNING", "RETRY_WAIT");

        if("RUNNING".equals(result.get("Status"))) {
            assertThat(result.get("ClaimedBy")).isEqualTo("test-worker");
            assertThat(result.get("ClaimTokenHash")).isEqualTo(CLAIM_TOKEN_HASH);
        } else {
            assertThat(result.get("ClaimedBy")).isNull();
            assertThat(result.get("ClaimTokenHash")).isNull();
        }
    }

    @Test
    void completionAndCancellationRequestProduceOneTerminalDecision() throws Exception {
        correlationId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        long jobId = insertRunningJob(now, now.plusMinutes(2));
        CountDownLatch start = new CountDownLatch(1);

        try(var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> completion = executor.submit(() -> {
                start.await();
                return jdbc.update("""
                        UPDATE ethnowear.DocumentProcessingJobs WITH (ROWLOCK)
                        SET Status = N'SUCCEEDED', ActiveJobKey = NULL,
                            ClaimedBy = NULL, ClaimedAt = NULL, ClaimExpiresAt = NULL,
                            ClaimTokenHash = NULL, TimeoutAt = NULL,
                            FinishedAt = ?, UpdatedAt = ?
                        WHERE Id = ? AND Status = N'RUNNING'
                        """, now, now, jobId);
            });
            Future<Integer> cancellation = executor.submit(() -> {
                start.await();
                return jdbc.update("""
                        UPDATE ethnowear.DocumentProcessingJobs WITH (ROWLOCK)
                        SET Status = N'CANCEL_REQUESTED', CancellationReason = N'Test cancellation', UpdatedAt = ?
                        WHERE Id = ? AND Status = N'RUNNING'
                        """, now, jobId);
            });

            start.countDown();
            assertThat(completion.get() + cancellation.get()).isEqualTo(1);
        }

        String status = jdbc.queryForObject(
                "SELECT Status FROM ethnowear.DocumentProcessingJobs WHERE Id = ?",
                String.class,
                jobId
        );
        assertThat(status).isIn("SUCCEEDED", "CANCEL_REQUESTED");
    }

    private Optional<ClaimedWorkerJob> claim(
            JobType jobType,
            String workerId,
            LocalDateTime now
    ) {
        return claimStore.claimNext(
                Set.of(jobType),
                workerId,
                now,
                now.plusMinutes(2),
                now.plusMinutes(30),
                CLAIM_TOKEN_HASH
        );
    }

    private JobType unusedEligibleJobType() {
        return Arrays.stream(JobType.values())
                .filter(type -> {
                    Integer count = jdbc.queryForObject("""
                            SELECT COUNT(*)
                            FROM ethnowear.DocumentProcessingJobs
                            WHERE Status IN (N'QUEUED', N'RETRY_WAIT')
                              AND AvailableAt <= SYSUTCDATETIME()
                              AND AttemptCount < MaxAttempts
                              AND JobType = ?
                              AND ActiveJobKey IS NOT NULL
                            """, Integer.class, type.name());
                    return count != null && count == 0;
                })
                .findFirst()
                .orElse(null);
    }

    private long insertQueuedJob(JobType jobType, LocalDateTime availableAt) {
        Long documentId = firstDocumentId();
        return jdbc.queryForObject("""
                INSERT INTO ethnowear.DocumentProcessingJobs (
                    JobType, Status, ActiveJobKey, DocumentId,
                    Priority, AttemptCount, MaxAttempts, AvailableAt, CorrelationId
                )
                OUTPUT INSERTED.Id
                VALUES (?, N'QUEUED', ?, ?, 2147483647, 0, 3, ?, ?)
                """, Long.class,
                jobType.name(),
                TEST_KEY_PREFIX + correlationId,
                documentId,
                availableAt,
                correlationId.toString()
        );
    }

    private long insertExpiredRunningJob(LocalDateTime now) {
        return insertRunningJob(now, now.minusSeconds(1));
    }

    private long insertRunningJob(
            LocalDateTime now,
            LocalDateTime claimExpiresAt
    ) {
        Long documentId = firstDocumentId();
        return jdbc.queryForObject("""
                INSERT INTO ethnowear.DocumentProcessingJobs (
                    JobType, Status, ActiveJobKey, DocumentId,
                    Priority, AttemptCount, MaxAttempts, AvailableAt,
                    ClaimedBy, ClaimedAt, ClaimExpiresAt, ClaimTokenHash,
                    StartedAt, TimeoutAt, CorrelationId
                )
                OUTPUT INSERTED.Id
                VALUES (
                    N'INDEX_CHUNK', N'RUNNING', ?, ?,
                    0, 1, 3, ?,
                    N'test-worker', ?, ?, ?,
                    ?, ?, ?
                )
                """, Long.class,
                TEST_KEY_PREFIX + correlationId,
                documentId,
                now.minusMinutes(10),
                now.minusMinutes(5),
                claimExpiresAt,
                CLAIM_TOKEN_HASH,
                now.minusMinutes(5),
                now.plusMinutes(20),
                correlationId.toString()
        );
    }

    private void assumeNoExpiredJobs() {
        Integer expiredJobs = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM ethnowear.DocumentProcessingJobs
                WHERE Status IN (N'RUNNING', N'CANCEL_REQUESTED')
                  AND (ClaimExpiresAt <= SYSUTCDATETIME() OR TimeoutAt <= SYSUTCDATETIME())
                """, Integer.class);
        assumeTrue(expiredJobs != null && expiredJobs == 0, "Live database already has expired jobs");
    }

    private Long firstDocumentId() {
        var ids = jdbc.queryForList(
                "SELECT TOP (1) Id FROM ethnowear.Documents ORDER BY Id",
                Long.class
        );
        assumeTrue(!ids.isEmpty(), "Live database has no document for a disposable target");
        return ids.getFirst();
    }
}

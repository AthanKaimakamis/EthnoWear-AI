package fmi.ethnowear.persistence.jdbc.document;

import fmi.ethnowear.application.port.worker.WorkerJobRecoveryStore;
import fmi.ethnowear.application.service.worker.job.WorkerRetryPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class SqlServerWorkerJobRecoveryStore implements WorkerJobRecoveryStore {

    private static final String RECOVER_SQL = """
            WITH [ExpiredJobs] AS (
                SELECT TOP (:batchSize) [job].[Id]
                FROM [ethnowear].[DocumentProcessingJobs] [job]
                    WITH (UPDLOCK, READPAST, ROWLOCK)
                WHERE [job].[Status] IN (N'RUNNING', N'CANCEL_REQUESTED')
                  AND (
                    [job].[ClaimExpiresAt] <= :now
                    OR [job].[TimeoutAt] <= :now
                  )
                ORDER BY [job].[ClaimExpiresAt], [job].[Id]
            )
            UPDATE [job]
            SET
                [Status] = CASE
                    WHEN [job].[Status] = N'CANCEL_REQUESTED'
                        THEN N'CANCELLED'
                    WHEN [job].[AttemptCount] >= [job].[MaxAttempts]
                        THEN N'DEAD'
                    ELSE N'RETRY_WAIT'
                END,
                [ActiveJobKey] = CASE
                    WHEN [job].[Status] = N'CANCEL_REQUESTED'
                        OR [job].[AttemptCount] >= [job].[MaxAttempts]
                        THEN NULL
                    ELSE [job].[ActiveJobKey]
                END,
                [AvailableAt] = CASE
                    WHEN [job].[Status] <> N'CANCEL_REQUESTED'
                        AND [job].[AttemptCount] < [job].[MaxAttempts]
                        THEN DATEADD(
                            SECOND,
                            CASE
                                WHEN [job].[AttemptCount] * :backoffSeconds
                                    > :maximumBackoffSeconds
                                    THEN :maximumBackoffSeconds
                                ELSE [job].[AttemptCount] * :backoffSeconds
                            END,
                            :now
                        )
                    ELSE [job].[AvailableAt]
                END,
                [ClaimedBy] = NULL,
                [ClaimedAt] = NULL,
                [ClaimExpiresAt] = NULL,
                [ClaimTokenHash] = NULL,
                [TimeoutAt] = NULL,
                [FinishedAt] = CASE
                    WHEN [job].[Status] = N'CANCEL_REQUESTED'
                        OR [job].[AttemptCount] >= [job].[MaxAttempts]
                        THEN :now
                    ELSE NULL
                END,
                [ErrorCode] = CASE
                    WHEN [job].[Status] = N'CANCEL_REQUESTED'
                        THEN [job].[ErrorCode]
                    ELSE N'WORKER_LEASE_EXPIRED'
                END,
                [SafeErrorMessage] = CASE
                    WHEN [job].[Status] = N'CANCEL_REQUESTED'
                        THEN [job].[SafeErrorMessage]
                    ELSE N'The processing worker lease expired.'
                END,
                [UpdatedAt] = :now
            FROM [ethnowear].[DocumentProcessingJobs] [job]
            INNER JOIN [ExpiredJobs] [expired]
                ON [expired].[Id] = [job].[Id]
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public int recoverExpired(LocalDateTime now, int batchSize) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("now", now)
                .addValue("batchSize", batchSize)
                .addValue("backoffSeconds", WorkerRetryPolicy.INITIAL_BACKOFF_SECONDS)
                .addValue("maximumBackoffSeconds", WorkerRetryPolicy.MAXIMUM_BACKOFF_SECONDS);

        return jdbcTemplate.update(RECOVER_SQL, parameters);
    }
}
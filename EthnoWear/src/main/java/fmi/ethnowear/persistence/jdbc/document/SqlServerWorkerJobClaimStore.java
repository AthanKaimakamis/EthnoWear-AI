package fmi.ethnowear.persistence.jdbc.document;

import fmi.ethnowear.application.model.worker.ClaimedWorkerJob;
import fmi.ethnowear.application.port.worker.WorkerJobClaimStore;
import fmi.ethnowear.domain.model.document.processing.JobType;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

@Repository
public class SqlServerWorkerJobClaimStore implements WorkerJobClaimStore {

    private static final String CLAIM_SQL = """
            ;WITH [Candidate] AS
            (
                SELECT TOP (1)
                    [job].[Id]
                FROM [ethnowear].[DocumentProcessingJobs] [job]
                    WITH (UPDLOCK, READPAST, ROWLOCK)
                WHERE [job].[Status] IN (N'QUEUED', N'RETRY_WAIT')
                  AND [job].[AvailableAt] <= :claimedAt
                  AND [job].[AttemptCount] < [job].[MaxAttempts]
                  AND [job].[JobType] IN (:jobTypes)
                  AND [job].[ActiveJobKey] IS NOT NULL
                ORDER BY
                    [job].[Priority] DESC,
                    [job].[AvailableAt] ASC,
                    [job].[CreatedAt] ASC,
                    [job].[Id] ASC
            )
            UPDATE [job] WITH (ROWLOCK)
            SET
                [job].[Status] = N'RUNNING',
                [job].[AttemptCount] = [job].[AttemptCount] + 1,
                [job].[ClaimedBy] = :workerId,
                [job].[ClaimedAt] = :claimedAt,
                [job].[ClaimExpiresAt] = :leaseExpiresAt,
                [job].[ClaimTokenHash] = :claimTokenHash,
                [job].[StartedAt] = COALESCE(
                    [job].[StartedAt],
                    :claimedAt
                ),
                [job].[FinishedAt] = NULL,
                [job].[TimeoutAt] = :timeoutAt,
                [job].[ErrorCode] = NULL,
                [job].[SafeErrorMessage] = NULL,
                [job].[ErrorDetailsJson] = NULL,
                [job].[CancellationReason] = NULL,
                [job].[UpdatedAt] = :claimedAt
            OUTPUT
                INSERTED.[Id] AS [JobId],
                INSERTED.[JobType] AS [JobType],
                INSERTED.[DocumentId] AS [DocumentId],
                INSERTED.[DocumentPageId] AS [DocumentPageId],
                INSERTED.[KnowledgeChunkId] AS [KnowledgeChunkId],
                CASE
                    WHEN INSERTED.[InputMediaAssetId] IS NULL
                        THEN CAST(0 AS BIT)
                    ELSE CAST(1 AS BIT)
                END AS [InputAvailable],
                INSERTED.[AttemptCount] AS [Attempt],
                INSERTED.[ClaimedAt] AS [ClaimedAt],
                INSERTED.[ClaimExpiresAt] AS [LeaseExpiresAt]
            FROM [ethnowear].[DocumentProcessingJobs] [job]
            INNER JOIN [Candidate] [candidate]
                ON [candidate].[Id] = [job].[Id];
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SqlServerWorkerJobClaimStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ClaimedWorkerJob> claimNext(
            @NonNull Set<JobType> supportedJobTypes,
            String workerId,
            LocalDateTime claimedAt,
            LocalDateTime leaseExpiresAt,
            LocalDateTime timeoutAt,
            String claimTokenHash
    ) {
        if(supportedJobTypes.isEmpty())
            return Optional.empty();

        var parameters = new MapSqlParameterSource()
                .addValue(
                        "jobTypes",
                        supportedJobTypes.stream()
                                .map(Enum::name)
                                .toList()
                )
                .addValue("workerId", workerId)
                .addValue("claimedAt", claimedAt)
                .addValue("leaseExpiresAt", leaseExpiresAt)
                .addValue("timeoutAt", timeoutAt)
                .addValue("claimTokenHash", claimTokenHash);

        return jdbc.query(
                CLAIM_SQL,
                parameters,
                this::mapClaim
        ).stream().findFirst();
    }

    @Contract("_, _ -> new")
    private @NonNull ClaimedWorkerJob mapClaim(
            @NonNull ResultSet result,
            int rowNumber
    ) throws SQLException {
        return new ClaimedWorkerJob(
                result.getLong("JobId"),
                JobType.valueOf(result.getString("JobType")),
                nullableLong(result, "DocumentId"),
                nullableLong(result, "DocumentPageId"),
                nullableLong(result, "KnowledgeChunkId"),
                result.getBoolean("InputAvailable"),
                result.getInt("Attempt"),
                result.getTimestamp("ClaimedAt").toLocalDateTime(),
                result.getTimestamp("LeaseExpiresAt").toLocalDateTime()
        );
    }

    private @Nullable Long nullableLong(
            @NonNull ResultSet result,
            String column
    ) throws SQLException {
        long value = result.getLong(column);

        return result.wasNull() ? null : value;
    }
}

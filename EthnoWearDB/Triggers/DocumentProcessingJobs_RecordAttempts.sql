CREATE TRIGGER [ethnowear].[TR_DocumentProcessingJobs_RecordAttempts]
ON [ethnowear].[DocumentProcessingJobs]
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    INSERT INTO [ethnowear].[DocumentProcessingJobAttempts]
    (
        [ProcessingJobId], [ExecutionNumber], [AttemptNumber], [Status],
        [ClaimedBy], [ClaimedAt], [StartedAt], [FinishedAt],
        [ProcessorName], [ProcessorVersion], [ToolName], [ToolVersion],
        [ErrorCode], [SafeErrorMessage], [CancellationReason], [UpdatedAt]
    )
    SELECT
        [current].[Id],
        ISNULL((
            SELECT MAX([history].[ExecutionNumber])
            FROM [ethnowear].[DocumentProcessingJobAttempts] [history]
            WHERE [history].[ProcessingJobId] = [current].[Id]
        ), 0) + 1,
        [current].[AttemptCount],
        [current].[Status],
        [current].[ClaimedBy],
        [current].[ClaimedAt],
        [current].[StartedAt],
        [current].[FinishedAt],
        [current].[ProcessorName],
        [current].[ProcessorVersion],
        [current].[ToolName],
        [current].[ToolVersion],
        [current].[ErrorCode],
        [current].[SafeErrorMessage],
        [current].[CancellationReason],
        [current].[UpdatedAt]
    FROM [inserted] [current]
    INNER JOIN [deleted] [previous] ON [previous].[Id] = [current].[Id]
    WHERE [current].[AttemptCount] > [previous].[AttemptCount]
      AND [current].[ClaimedAt] IS NOT NULL;

    UPDATE [history]
    SET
        [Status] = [current].[Status],
        [ClaimedBy] = [current].[ClaimedBy],
        [StartedAt] = COALESCE([current].[StartedAt], [history].[StartedAt]),
        [FinishedAt] = [current].[FinishedAt],
        [ProcessorName] = [current].[ProcessorName],
        [ProcessorVersion] = [current].[ProcessorVersion],
        [ToolName] = [current].[ToolName],
        [ToolVersion] = [current].[ToolVersion],
        [ErrorCode] = [current].[ErrorCode],
        [SafeErrorMessage] = [current].[SafeErrorMessage],
        [CancellationReason] = [current].[CancellationReason],
        [UpdatedAt] = [current].[UpdatedAt]
    FROM [ethnowear].[DocumentProcessingJobAttempts] [history]
    INNER JOIN [inserted] [current]
        ON [current].[Id] = [history].[ProcessingJobId]
    INNER JOIN [deleted] [previous]
        ON [previous].[Id] = [current].[Id]
    WHERE [current].[AttemptCount] = [previous].[AttemptCount]
      AND [current].[AttemptCount] > 0
      AND [history].[ExecutionNumber] = (
          SELECT MAX([latest].[ExecutionNumber])
          FROM [ethnowear].[DocumentProcessingJobAttempts] [latest]
          WHERE [latest].[ProcessingJobId] = [current].[Id]
      );
END;

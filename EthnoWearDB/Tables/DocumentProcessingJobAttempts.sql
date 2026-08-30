CREATE TABLE [ethnowear].[DocumentProcessingJobAttempts]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [ProcessingJobId] BIGINT NOT NULL,
    [ExecutionNumber] INT NOT NULL,
    [AttemptNumber] INT NOT NULL,
    [Status] NVARCHAR(50) NOT NULL,
    [ClaimedBy] NVARCHAR(150) NULL,
    [ClaimedAt] DATETIME2(7) NOT NULL,
    [StartedAt] DATETIME2(7) NULL,
    [FinishedAt] DATETIME2(7) NULL,
    [ProcessorName] NVARCHAR(100) NULL,
    [ProcessorVersion] NVARCHAR(100) NULL,
    [ToolName] NVARCHAR(100) NULL,
    [ToolVersion] NVARCHAR(100) NULL,
    [ErrorCode] NVARCHAR(100) NULL,
    [SafeErrorMessage] NVARCHAR(1000) NULL,
    [CancellationReason] NVARCHAR(500) NULL,
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentProcessingJobAttempts_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentProcessingJobAttempts_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_DocumentProcessingJobAttempts] PRIMARY KEY CLUSTERED ([Id]),
    CONSTRAINT [FK_DocumentProcessingJobAttempts_Job]
        FOREIGN KEY ([ProcessingJobId])
        REFERENCES [ethnowear].[DocumentProcessingJobs] ([Id]),
    CONSTRAINT [UQ_DocumentProcessingJobAttempts_Job_Execution]
        UNIQUE ([ProcessingJobId], [ExecutionNumber]),
    CONSTRAINT [CK_DocumentProcessingJobAttempts_Numbers]
        CHECK ([ExecutionNumber] > 0 AND [AttemptNumber] > 0),
    CONSTRAINT [CK_DocumentProcessingJobAttempts_Status]
        CHECK ([Status] IN (
            N'CLAIMED', N'RUNNING', N'SUCCEEDED', N'FAILED', N'RETRY_WAIT',
            N'CANCEL_REQUESTED', N'CANCELLED', N'TIMED_OUT', N'DEAD'
        ))
);

GO

CREATE INDEX [IX_DocumentProcessingJobAttempts_Job]
ON [ethnowear].[DocumentProcessingJobAttempts] ([ProcessingJobId], [ExecutionNumber] DESC);

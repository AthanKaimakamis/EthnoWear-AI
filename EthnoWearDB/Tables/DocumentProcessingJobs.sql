CREATE TABLE [ethnowear].[DocumentProcessingJobs]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,

    [JobType] NVARCHAR(50) NOT NULL,
    [Status] NVARCHAR(50) NOT NULL,
    [ActiveJobKey] NVARCHAR(500) NULL,

    [DocumentId] BIGINT NULL,
    [DocumentPageId] BIGINT NULL,
    [InputMediaAssetId] BIGINT NULL,
    [KnowledgeChunkId] BIGINT NULL,

    [Priority] INT NOT NULL,
    [AttemptCount] INT NOT NULL,
    [MaxAttempts] INT NOT NULL,
    [AvailableAt] DATETIME2(7) NOT NULL,

    [ClaimedBy] NVARCHAR(150) NULL,
    [ClaimedAt] DATETIME2(7) NULL,
    [ClaimExpiresAt] DATETIME2(7) NULL,
    [ClaimTokenHash] CHAR(64) NULL,
    [StartedAt] DATETIME2(7) NULL,
    [FinishedAt] DATETIME2(7) NULL,
    [TimeoutAt] DATETIME2(7) NULL,

    [ProcessorName] NVARCHAR(100) NULL,
    [ProcessorVersion] NVARCHAR(100) NULL,
    [ToolName] NVARCHAR(100) NULL,
    [ToolVersion] NVARCHAR(100) NULL,
    [ParametersJson] NVARCHAR(MAX) NULL,

    [ErrorCode] NVARCHAR(100) NULL,
    [SafeErrorMessage] NVARCHAR(1000) NULL,
    [ErrorDetailsJson] NVARCHAR(MAX) NULL,
    [CancellationReason] NVARCHAR(500) NULL,
    [CorrelationId] UNIQUEIDENTIFIER NULL,
    [RowVersion] ROWVERSION NOT NULL,

    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentProcessingJobs_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentProcessingJobs_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_DocumentProcessingJobs] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_DocumentProcessingJobs_Documents]
        FOREIGN KEY ([DocumentId])
        REFERENCES [ethnowear].[Documents] ([Id]),

    CONSTRAINT [FK_DocumentProcessingJobs_DocumentPages]
        FOREIGN KEY ([DocumentPageId])
        REFERENCES [ethnowear].[DocumentPages] ([Id]),

    CONSTRAINT [FK_DocumentProcessingJobs_InputMediaAssets]
        FOREIGN KEY ([InputMediaAssetId])
        REFERENCES [ethnowear].[MediaAssets] ([Id]),

    CONSTRAINT [FK_DocumentProcessingJobs_KnowledgeChunks]
        FOREIGN KEY ([KnowledgeChunkId])
        REFERENCES [ethnowear].[KnowledgeChunks] ([Id]),

    CONSTRAINT [CK_DocumentProcessingJobs_JobType]
        CHECK ([JobType] IN (
            N'PAGE_EXTRACTION',
            N'OCR',
            N'OCR_QUALITY_ASSESSMENT',
            N'CHUNK_GENERATION',
            N'INDEX_CHUNK',
            N'REINDEX_DOCUMENT',
            N'REMOVE_VECTOR',
            N'GENERATE_THUMBNAIL'
        )),

    CONSTRAINT [CK_DocumentProcessingJobs_Status]
        CHECK ([Status] IN (
            N'QUEUED',
            N'CLAIMED',
            N'RUNNING',
            N'SUCCEEDED',
            N'FAILED',
            N'RETRY_WAIT',
            N'CANCEL_REQUESTED',
            N'CANCELLED',
            N'TIMED_OUT',
            N'DEAD'
        )),

    CONSTRAINT [CK_DocumentProcessingJobs_ActiveJobKeyState]
        CHECK (
            (
                [Status] IN (
                    N'QUEUED',
                    N'CLAIMED',
                    N'RUNNING',
                    N'RETRY_WAIT',
                    N'CANCEL_REQUESTED'
                )
                AND [ActiveJobKey] IS NOT NULL
                AND LEN(LTRIM(RTRIM([ActiveJobKey]))) > 0
            )
            OR (
                [Status] IN (
                    N'SUCCEEDED',
                    N'FAILED',
                    N'CANCELLED',
                    N'TIMED_OUT',
                    N'DEAD'
                )
                AND [ActiveJobKey] IS NULL
            )
        ),

    CONSTRAINT [CK_DocumentProcessingJobs_Target]
        CHECK (
            [DocumentId] IS NOT NULL
            OR [DocumentPageId] IS NOT NULL
            OR [InputMediaAssetId] IS NOT NULL
            OR [KnowledgeChunkId] IS NOT NULL
        ),

    CONSTRAINT [CK_DocumentProcessingJobs_Attempts]
        CHECK (
            [AttemptCount] >= 0
            AND [MaxAttempts] > 0
            AND [AttemptCount] <= [MaxAttempts]
        ),

    CONSTRAINT [CK_DocumentProcessingJobs_Priority]
        CHECK ([Priority] >= 0),

    CONSTRAINT [CK_DocumentProcessingJobs_ClaimTokenState]
        CHECK (
            (
                [Status] IN (
                    N'CLAIMED',
                    N'RUNNING',
                    N'CANCEL_REQUESTED'
                )
                AND [ClaimTokenHash] IS NOT NULL
                AND LEN([ClaimTokenHash]) = 64
                AND [ClaimTokenHash] NOT LIKE '%[^0-9A-Fa-f]%'
            )
            OR (
                [Status] NOT IN (
                    N'CLAIMED',
                    N'RUNNING',
                    N'CANCEL_REQUESTED'
                )
                AND [ClaimTokenHash] IS NULL
            )
        ),

    CONSTRAINT [CK_DocumentProcessingJobs_ParametersJson]
        CHECK ([ParametersJson] IS NULL OR ISJSON([ParametersJson]) = 1),

    CONSTRAINT [CK_DocumentProcessingJobs_ErrorDetailsJson]
        CHECK ([ErrorDetailsJson] IS NULL OR ISJSON([ErrorDetailsJson]) = 1)
);

GO

CREATE INDEX [IX_DocumentProcessingJobs_Claim]
ON [ethnowear].[DocumentProcessingJobs] ([Status], [AvailableAt], [Priority]);

GO

CREATE UNIQUE INDEX [UQ_DocumentProcessingJobs_ActiveJobKey]
ON [ethnowear].[DocumentProcessingJobs] ([ActiveJobKey])
WHERE [ActiveJobKey] IS NOT NULL;

GO

CREATE INDEX [IX_DocumentProcessingJobs_ClaimExpiresAt]
ON [ethnowear].[DocumentProcessingJobs] ([ClaimExpiresAt])
WHERE [ClaimExpiresAt] IS NOT NULL;

GO

CREATE INDEX [IX_DocumentProcessingJobs_DocumentId]
ON [ethnowear].[DocumentProcessingJobs] ([DocumentId]);

GO

CREATE INDEX [IX_DocumentProcessingJobs_DocumentPageId]
ON [ethnowear].[DocumentProcessingJobs] ([DocumentPageId]);

GO

CREATE INDEX [IX_DocumentProcessingJobs_InputMediaAssetId]
ON [ethnowear].[DocumentProcessingJobs] ([InputMediaAssetId]);

GO

CREATE INDEX [IX_DocumentProcessingJobs_KnowledgeChunkId]
ON [ethnowear].[DocumentProcessingJobs] ([KnowledgeChunkId]);

GO

CREATE INDEX [IX_DocumentProcessingJobs_CorrelationId]
ON [ethnowear].[DocumentProcessingJobs] ([CorrelationId])
WHERE [CorrelationId] IS NOT NULL;

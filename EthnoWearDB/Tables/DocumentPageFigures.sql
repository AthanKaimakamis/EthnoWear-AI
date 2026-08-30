CREATE TABLE [ethnowear].[DocumentPageFigures]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [DocumentPageId] BIGINT NOT NULL,
    [DocumentPageMediaId] BIGINT NOT NULL,
    [MediaAssetId] BIGINT NOT NULL,
    [SourceReferenceId] BIGINT NULL,
    [FigureCandidateId] BIGINT NOT NULL,
    [FigureOrdinal] INT NOT NULL,
    [PrintedFigureNumber] NVARCHAR(100) NULL,
    [NormalizedX] DECIMAL(8,7) NOT NULL,
    [NormalizedY] DECIMAL(8,7) NOT NULL,
    [NormalizedWidth] DECIMAL(8,7) NOT NULL,
    [NormalizedHeight] DECIMAL(8,7) NOT NULL,
    [RawCaptionText] NVARCHAR(2000) NULL,
    [CorrectedCaptionText] NVARCHAR(2000) NULL,
    [ReviewState] NVARCHAR(50) NOT NULL,
    [ReviewedBy] NVARCHAR(150) NULL,
    [ReviewedAt] DATETIME2(7) NULL,
    [ReviewReason] NVARCHAR(500) NULL,
    [DetectionConfidence] DECIMAL(5,4) NULL,
    [ProcessingJobId] BIGINT NOT NULL,
    [ProducingAttempt] INT NOT NULL,
    [CreatedAt] DATETIME2(7) NOT NULL
        CONSTRAINT [DF_DocumentPageFigures_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL
        CONSTRAINT [DF_DocumentPageFigures_UpdatedAt] DEFAULT SYSUTCDATETIME(),
    [RowVersion] ROWVERSION NOT NULL,

    CONSTRAINT [PK_DocumentPageFigures] PRIMARY KEY CLUSTERED ([Id]),
    CONSTRAINT [FK_DocumentPageFigures_Page]
        FOREIGN KEY ([DocumentPageId]) REFERENCES [ethnowear].[DocumentPages] ([Id]),
    CONSTRAINT [FK_DocumentPageFigures_PageMedia]
        FOREIGN KEY ([DocumentPageMediaId]) REFERENCES [ethnowear].[DocumentPageMedia] ([Id]),
    CONSTRAINT [FK_DocumentPageFigures_MediaAsset]
        FOREIGN KEY ([MediaAssetId]) REFERENCES [ethnowear].[MediaAssets] ([Id]),
    CONSTRAINT [FK_DocumentPageFigures_SourceReference]
        FOREIGN KEY ([SourceReferenceId]) REFERENCES [ethnowear].[SourceReference] ([Id]),
    CONSTRAINT [FK_DocumentPageFigures_Candidate]
        FOREIGN KEY ([FigureCandidateId]) REFERENCES [ethnowear].[DocumentPageFigureCandidates] ([Id]),
    CONSTRAINT [FK_DocumentPageFigures_ProcessingJob]
        FOREIGN KEY ([ProcessingJobId]) REFERENCES [ethnowear].[DocumentProcessingJobs] ([Id]),

    CONSTRAINT [UQ_DocumentPageFigures_Page_Media]
        UNIQUE ([DocumentPageId], [MediaAssetId]),
    CONSTRAINT [UQ_DocumentPageFigures_Job_Attempt_Ordinal]
        UNIQUE ([ProcessingJobId], [ProducingAttempt], [FigureOrdinal]),
    CONSTRAINT [UQ_DocumentPageFigures_Job_Attempt_Candidate]
        UNIQUE ([ProcessingJobId], [ProducingAttempt], [FigureCandidateId]),

    CONSTRAINT [CK_DocumentPageFigures_Ordinal]
        CHECK ([FigureOrdinal] > 0 AND [ProducingAttempt] > 0),
    CONSTRAINT [CK_DocumentPageFigures_Coordinates]
        CHECK (
            [NormalizedX] BETWEEN 0 AND 1
            AND [NormalizedY] BETWEEN 0 AND 1
            AND [NormalizedWidth] > 0
            AND [NormalizedWidth] <= 1
            AND [NormalizedHeight] > 0
            AND [NormalizedHeight] <= 1
            AND [NormalizedX] + [NormalizedWidth] <= 1
            AND [NormalizedY] + [NormalizedHeight] <= 1
        ),
    CONSTRAINT [CK_DocumentPageFigures_ReviewState]
        CHECK ([ReviewState] IN (N'PENDING', N'APPROVED', N'REJECTED', N'OUTDATED')),
    CONSTRAINT [CK_DocumentPageFigures_ReviewMetadata]
        CHECK (
            ([ReviewState] IN (N'PENDING', N'OUTDATED')
                AND [ReviewedBy] IS NULL AND [ReviewedAt] IS NULL AND [ReviewReason] IS NULL)
            OR
            ([ReviewState] IN (N'APPROVED', N'REJECTED')
                AND [ReviewedBy] IS NOT NULL
                AND LEN(LTRIM(RTRIM([ReviewedBy]))) BETWEEN 1 AND 150
                AND [ReviewedAt] IS NOT NULL
                AND [ReviewReason] IS NOT NULL
                AND LEN(LTRIM(RTRIM([ReviewReason]))) BETWEEN 1 AND 500)
        ),
    CONSTRAINT [CK_DocumentPageFigures_Confidence]
        CHECK ([DetectionConfidence] IS NULL OR [DetectionConfidence] BETWEEN 0 AND 1),
    CONSTRAINT [CK_DocumentPageFigures_Captions]
        CHECK (
            ([RawCaptionText] IS NULL OR LEN(LTRIM(RTRIM([RawCaptionText]))) BETWEEN 1 AND 2000)
            AND ([CorrectedCaptionText] IS NULL OR LEN(LTRIM(RTRIM([CorrectedCaptionText]))) BETWEEN 1 AND 2000)
        )
);

GO

CREATE INDEX [IX_DocumentPageFigures_Page_ReviewState]
ON [ethnowear].[DocumentPageFigures] ([DocumentPageId], [ReviewState], [FigureOrdinal]);

GO

CREATE INDEX [IX_DocumentPageFigures_SourceReference]
ON [ethnowear].[DocumentPageFigures] ([SourceReferenceId])
WHERE [SourceReferenceId] IS NOT NULL;

GO

CREATE INDEX [IX_DocumentPageFigures_PageMedia]
ON [ethnowear].[DocumentPageFigures] ([DocumentPageMediaId]);

GO

CREATE INDEX [IX_DocumentPageFigures_MediaAsset_ReviewState]
ON [ethnowear].[DocumentPageFigures] ([MediaAssetId], [ReviewState])
INCLUDE ([DocumentPageId], [SourceReferenceId], [FigureOrdinal]);

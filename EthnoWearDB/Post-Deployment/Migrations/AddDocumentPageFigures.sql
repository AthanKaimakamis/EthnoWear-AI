SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRANSACTION;

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'ethnowear.DocumentPageOcrResults')
      AND name = N'FigureExtractionState'
)
BEGIN
    ALTER TABLE [ethnowear].[DocumentPageOcrResults]
    ADD [FigureExtractionState] NVARCHAR(50) NOT NULL
        CONSTRAINT [DF_DocumentPageOcrResults_FigureExtractionState]
        DEFAULT N'NOT_REQUESTED';
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'ethnowear.DocumentPageOcrResults')
      AND name = N'FigureExtractionMessage'
)
BEGIN
    ALTER TABLE [ethnowear].[DocumentPageOcrResults]
    ADD [FigureExtractionMessage] NVARCHAR(500) NULL;
END;

IF OBJECT_ID(N'ethnowear.CK_DocumentPageOcrResults_FigureExtractionState', N'C') IS NULL
BEGIN
    EXEC(N'ALTER TABLE [ethnowear].[DocumentPageOcrResults]
    ADD CONSTRAINT [CK_DocumentPageOcrResults_FigureExtractionState]
        CHECK ([FigureExtractionState] IN (
            N''NOT_REQUESTED'', N''PENDING'', N''COMPLETED'', N''FAILED'',
            N''SCHEDULING_FAILED'', N''OUTDATED''
        ));');
END;

IF OBJECT_ID(N'ethnowear.CK_DocumentPageOcrResults_FigureExtractionMessage', N'C') IS NULL
BEGIN
    EXEC(N'ALTER TABLE [ethnowear].[DocumentPageOcrResults]
    ADD CONSTRAINT [CK_DocumentPageOcrResults_FigureExtractionMessage]
        CHECK ([FigureExtractionMessage] IS NULL
            OR LEN([FigureExtractionMessage]) BETWEEN 1 AND 500);');
END;

IF OBJECT_ID(N'ethnowear.DocumentPageFigureCandidates', N'U') IS NULL
BEGIN
    CREATE TABLE [ethnowear].[DocumentPageFigureCandidates]
    (
        [Id] BIGINT IDENTITY(1,1) NOT NULL,
        [DocumentPageOcrResultId] BIGINT NOT NULL,
        [DocumentPageMediaId] BIGINT NOT NULL,
        [CandidateOrdinal] INT NOT NULL,
        [NormalizedX] DECIMAL(8,7) NOT NULL,
        [NormalizedY] DECIMAL(8,7) NOT NULL,
        [NormalizedWidth] DECIMAL(8,7) NOT NULL,
        [NormalizedHeight] DECIMAL(8,7) NOT NULL,
        [RawCaptionText] NVARCHAR(2000) NULL,
        [DetectionConfidence] DECIMAL(5,4) NULL,
        [CreatedAt] DATETIME2(7) NOT NULL
            CONSTRAINT [DF_DocumentPageFigureCandidates_CreatedAt] DEFAULT SYSUTCDATETIME(),
        CONSTRAINT [PK_DocumentPageFigureCandidates] PRIMARY KEY CLUSTERED ([Id]),
        CONSTRAINT [FK_DocumentPageFigureCandidates_OcrResult]
            FOREIGN KEY ([DocumentPageOcrResultId]) REFERENCES [ethnowear].[DocumentPageOcrResults] ([Id]),
        CONSTRAINT [FK_DocumentPageFigureCandidates_PageMedia]
            FOREIGN KEY ([DocumentPageMediaId]) REFERENCES [ethnowear].[DocumentPageMedia] ([Id]),
        CONSTRAINT [UQ_DocumentPageFigureCandidates_Result_Ordinal]
            UNIQUE ([DocumentPageOcrResultId], [CandidateOrdinal]),
        CONSTRAINT [CK_DocumentPageFigureCandidates_Ordinal] CHECK ([CandidateOrdinal] > 0),
        CONSTRAINT [CK_DocumentPageFigureCandidates_Coordinates] CHECK (
            [NormalizedX] BETWEEN 0 AND 1 AND [NormalizedY] BETWEEN 0 AND 1
            AND [NormalizedWidth] > 0 AND [NormalizedWidth] <= 1
            AND [NormalizedHeight] > 0 AND [NormalizedHeight] <= 1
            AND [NormalizedX] + [NormalizedWidth] <= 1
            AND [NormalizedY] + [NormalizedHeight] <= 1
        ),
        CONSTRAINT [CK_DocumentPageFigureCandidates_Caption] CHECK (
            [RawCaptionText] IS NULL OR LEN(LTRIM(RTRIM([RawCaptionText]))) BETWEEN 1 AND 2000
        ),
        CONSTRAINT [CK_DocumentPageFigureCandidates_Confidence] CHECK (
            [DetectionConfidence] IS NULL OR [DetectionConfidence] BETWEEN 0 AND 1
        )
    );

    CREATE INDEX [IX_DocumentPageFigureCandidates_PageMedia]
    ON [ethnowear].[DocumentPageFigureCandidates] ([DocumentPageMediaId]);
END;

IF OBJECT_ID(N'ethnowear.DocumentPageFigures', N'U') IS NULL
BEGIN
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
        [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentPageFigures_CreatedAt] DEFAULT SYSUTCDATETIME(),
        [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentPageFigures_UpdatedAt] DEFAULT SYSUTCDATETIME(),
        [RowVersion] ROWVERSION NOT NULL,
        CONSTRAINT [PK_DocumentPageFigures] PRIMARY KEY CLUSTERED ([Id]),
        CONSTRAINT [FK_DocumentPageFigures_Page] FOREIGN KEY ([DocumentPageId]) REFERENCES [ethnowear].[DocumentPages] ([Id]),
        CONSTRAINT [FK_DocumentPageFigures_PageMedia] FOREIGN KEY ([DocumentPageMediaId]) REFERENCES [ethnowear].[DocumentPageMedia] ([Id]),
        CONSTRAINT [FK_DocumentPageFigures_MediaAsset] FOREIGN KEY ([MediaAssetId]) REFERENCES [ethnowear].[MediaAssets] ([Id]),
        CONSTRAINT [FK_DocumentPageFigures_SourceReference] FOREIGN KEY ([SourceReferenceId]) REFERENCES [ethnowear].[SourceReference] ([Id]),
        CONSTRAINT [FK_DocumentPageFigures_Candidate] FOREIGN KEY ([FigureCandidateId]) REFERENCES [ethnowear].[DocumentPageFigureCandidates] ([Id]),
        CONSTRAINT [FK_DocumentPageFigures_ProcessingJob] FOREIGN KEY ([ProcessingJobId]) REFERENCES [ethnowear].[DocumentProcessingJobs] ([Id]),
        CONSTRAINT [UQ_DocumentPageFigures_Page_Media] UNIQUE ([DocumentPageId], [MediaAssetId]),
        CONSTRAINT [UQ_DocumentPageFigures_Job_Attempt_Ordinal] UNIQUE ([ProcessingJobId], [ProducingAttempt], [FigureOrdinal]),
        CONSTRAINT [UQ_DocumentPageFigures_Job_Attempt_Candidate] UNIQUE ([ProcessingJobId], [ProducingAttempt], [FigureCandidateId]),
        CONSTRAINT [CK_DocumentPageFigures_Ordinal] CHECK ([FigureOrdinal] > 0 AND [ProducingAttempt] > 0),
        CONSTRAINT [CK_DocumentPageFigures_Coordinates] CHECK (
            [NormalizedX] BETWEEN 0 AND 1 AND [NormalizedY] BETWEEN 0 AND 1
            AND [NormalizedWidth] > 0 AND [NormalizedWidth] <= 1
            AND [NormalizedHeight] > 0 AND [NormalizedHeight] <= 1
            AND [NormalizedX] + [NormalizedWidth] <= 1
            AND [NormalizedY] + [NormalizedHeight] <= 1
        ),
        CONSTRAINT [CK_DocumentPageFigures_ReviewState] CHECK ([ReviewState] IN (N'PENDING', N'APPROVED', N'REJECTED', N'OUTDATED')),
        CONSTRAINT [CK_DocumentPageFigures_ReviewMetadata] CHECK (
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
        CONSTRAINT [CK_DocumentPageFigures_Confidence] CHECK ([DetectionConfidence] IS NULL OR [DetectionConfidence] BETWEEN 0 AND 1),
        CONSTRAINT [CK_DocumentPageFigures_Captions] CHECK (
            ([RawCaptionText] IS NULL OR LEN(LTRIM(RTRIM([RawCaptionText]))) BETWEEN 1 AND 2000)
            AND ([CorrectedCaptionText] IS NULL OR LEN(LTRIM(RTRIM([CorrectedCaptionText]))) BETWEEN 1 AND 2000)
        )
    );

    CREATE INDEX [IX_DocumentPageFigures_Page_ReviewState]
    ON [ethnowear].[DocumentPageFigures] ([DocumentPageId], [ReviewState], [FigureOrdinal]);
    CREATE INDEX [IX_DocumentPageFigures_SourceReference]
    ON [ethnowear].[DocumentPageFigures] ([SourceReferenceId]) WHERE [SourceReferenceId] IS NOT NULL;
    CREATE INDEX [IX_DocumentPageFigures_PageMedia]
    ON [ethnowear].[DocumentPageFigures] ([DocumentPageMediaId]);
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'ethnowear.DocumentPageFigures')
      AND name = N'IX_DocumentPageFigures_MediaAsset_ReviewState'
)
BEGIN
    CREATE INDEX [IX_DocumentPageFigures_MediaAsset_ReviewState]
    ON [ethnowear].[DocumentPageFigures] ([MediaAssetId], [ReviewState])
    INCLUDE ([DocumentPageId], [SourceReferenceId], [FigureOrdinal]);
END;

DECLARE @JobConstraintName SYSNAME;
SELECT @JobConstraintName = cc.name
FROM sys.check_constraints cc
WHERE cc.parent_object_id = OBJECT_ID(N'ethnowear.DocumentProcessingJobs')
  AND cc.name = N'CK_DocumentProcessingJobs_JobType';

IF @JobConstraintName IS NOT NULL
BEGIN
    ALTER TABLE [ethnowear].[DocumentProcessingJobs]
    DROP CONSTRAINT [CK_DocumentProcessingJobs_JobType];
END;

ALTER TABLE [ethnowear].[DocumentProcessingJobs]
ADD CONSTRAINT [CK_DocumentProcessingJobs_JobType]
CHECK ([JobType] IN (
    N'PAGE_EXTRACTION', N'OCR', N'EXTRACT_PAGE_FIGURES',
    N'OCR_QUALITY_ASSESSMENT', N'VISION_OCR_ASSESSMENT',
    N'CHUNK_GENERATION', N'INDEX_CHUNK', N'REINDEX_DOCUMENT',
    N'REMOVE_VECTOR', N'GENERATE_THUMBNAIL', N'MEDIA_CLEANUP'
));

COMMIT TRANSACTION;

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

IF EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE [name] = N'CK_DocumentProcessingJobs_JobType'
      AND [parent_object_id] = OBJECT_ID(N'ethnowear.DocumentProcessingJobs')
)
BEGIN
    ALTER TABLE [ethnowear].[DocumentProcessingJobs]
    DROP CONSTRAINT [CK_DocumentProcessingJobs_JobType];
END;

ALTER TABLE [ethnowear].[DocumentProcessingJobs]
ADD CONSTRAINT [CK_DocumentProcessingJobs_JobType]
CHECK ([JobType] IN (
    N'PAGE_EXTRACTION',
    N'OCR',
    N'EXTRACT_PAGE_FIGURES',
    N'OCR_QUALITY_ASSESSMENT',
    N'VISION_OCR_ASSESSMENT',
    N'CHUNK_GENERATION',
    N'INDEX_CHUNK',
    N'REINDEX_DOCUMENT',
    N'REMOVE_VECTOR',
    N'GENERATE_THUMBNAIL',
    N'MEDIA_CLEANUP'
));

IF OBJECT_ID(N'ethnowear.DocumentPageTextSuggestions', N'U') IS NULL
BEGIN
    CREATE TABLE [ethnowear].[DocumentPageTextSuggestions]
    (
        [Id] BIGINT IDENTITY(1,1) NOT NULL,
        [DocumentPageId] BIGINT NOT NULL,
        [DocumentPageMediaId] BIGINT NOT NULL,
        [DocumentPageOcrResultId] BIGINT NOT NULL,
        [ProcessingJobId] BIGINT NOT NULL,
        [SuggestedText] NVARCHAR(MAX) NOT NULL,
        [SuggestedTextHash] NVARCHAR(64) NOT NULL,
        [ModelName] NVARCHAR(100) NOT NULL,
        [ModelVersion] NVARCHAR(100) NOT NULL,
        [PromptVersion] NVARCHAR(100) NOT NULL,
        [RequiresReview] BIT NOT NULL
            CONSTRAINT [DF_DocumentPageTextSuggestions_RequiresReview]
            DEFAULT (1),
        [IssuesJson] NVARCHAR(4000) NOT NULL,
        [UncertainPassagesJson] NVARCHAR(4000) NOT NULL
            CONSTRAINT [DF_DocumentPageTextSuggestions_UncertainPassagesJson]
            DEFAULT (N'[]'),
        [CreatedAt] DATETIME2(7) NOT NULL
            CONSTRAINT [DF_DocumentPageTextSuggestions_CreatedAt]
            DEFAULT SYSUTCDATETIME(),
        CONSTRAINT [PK_DocumentPageTextSuggestions]
            PRIMARY KEY CLUSTERED ([Id]),
        CONSTRAINT [FK_DocumentPageTextSuggestions_DocumentPages]
            FOREIGN KEY ([DocumentPageId])
            REFERENCES [ethnowear].[DocumentPages] ([Id]),
        CONSTRAINT [FK_DocumentPageTextSuggestions_DocumentPageMedia]
            FOREIGN KEY ([DocumentPageMediaId])
            REFERENCES [ethnowear].[DocumentPageMedia] ([Id]),
        CONSTRAINT [FK_DocumentPageTextSuggestions_OcrResult]
            FOREIGN KEY ([DocumentPageOcrResultId])
            REFERENCES [ethnowear].[DocumentPageOcrResults] ([Id]),
        CONSTRAINT [FK_DocumentPageTextSuggestions_ProcessingJob]
            FOREIGN KEY ([ProcessingJobId])
            REFERENCES [ethnowear].[DocumentProcessingJobs] ([Id]),
        CONSTRAINT [CK_DocumentPageTextSuggestions_SuggestedText]
            CHECK (LEN(LTRIM(RTRIM([SuggestedText]))) > 0),
        CONSTRAINT [CK_DocumentPageTextSuggestions_SuggestedTextHash]
            CHECK (
                LEN([SuggestedTextHash]) = 64
                AND [SuggestedTextHash] COLLATE Latin1_General_100_BIN2
                    NOT LIKE N'%[^0-9a-f]%'
            ),
        CONSTRAINT [CK_DocumentPageTextSuggestions_ModelName]
            CHECK (LEN(LTRIM(RTRIM([ModelName]))) > 0),
        CONSTRAINT [CK_DocumentPageTextSuggestions_ModelVersion]
            CHECK (LEN(LTRIM(RTRIM([ModelVersion]))) > 0),
        CONSTRAINT [CK_DocumentPageTextSuggestions_PromptVersion]
            CHECK (LEN(LTRIM(RTRIM([PromptVersion]))) > 0),
        CONSTRAINT [CK_DocumentPageTextSuggestions_IssuesJson]
            CHECK (ISJSON([IssuesJson]) = 1),
        CONSTRAINT [CK_DocumentPageTextSuggestions_UncertainPassagesJson]
            CHECK (ISJSON([UncertainPassagesJson]) = 1)
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE [name] = N'UQ_DocumentPageTextSuggestions_ProcessingJobId'
      AND [object_id] = OBJECT_ID(N'ethnowear.DocumentPageTextSuggestions')
)
    CREATE UNIQUE INDEX [UQ_DocumentPageTextSuggestions_ProcessingJobId]
    ON [ethnowear].[DocumentPageTextSuggestions] ([ProcessingJobId]);

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE [name] = N'IX_DocumentPageTextSuggestions_Page_CreatedAt'
      AND [object_id] = OBJECT_ID(N'ethnowear.DocumentPageTextSuggestions')
)
    CREATE INDEX [IX_DocumentPageTextSuggestions_Page_CreatedAt]
    ON [ethnowear].[DocumentPageTextSuggestions]
        ([DocumentPageId], [CreatedAt] DESC, [Id] DESC);

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE [name] = N'IX_DocumentPageTextSuggestions_OcrResult_CreatedAt'
      AND [object_id] = OBJECT_ID(N'ethnowear.DocumentPageTextSuggestions')
)
    CREATE INDEX [IX_DocumentPageTextSuggestions_OcrResult_CreatedAt]
    ON [ethnowear].[DocumentPageTextSuggestions]
        ([DocumentPageOcrResultId], [CreatedAt] DESC, [Id] DESC);

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE [name] = N'IX_DocumentPageTextSuggestions_DocumentPageMediaId'
      AND [object_id] = OBJECT_ID(N'ethnowear.DocumentPageTextSuggestions')
)
    CREATE INDEX [IX_DocumentPageTextSuggestions_DocumentPageMediaId]
    ON [ethnowear].[DocumentPageTextSuggestions] ([DocumentPageMediaId]);

IF COL_LENGTH(N'ethnowear.DocumentPageReviews', N'SourceTextSuggestionId') IS NULL
BEGIN
    ALTER TABLE [ethnowear].[DocumentPageReviews]
    ADD [SourceTextSuggestionId] BIGINT NULL;
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE [name] = N'FK_DocumentPageReviews_SourceTextSuggestions'
)
BEGIN
    ALTER TABLE [ethnowear].[DocumentPageReviews]
    ADD CONSTRAINT [FK_DocumentPageReviews_SourceTextSuggestions]
        FOREIGN KEY ([SourceTextSuggestionId])
        REFERENCES [ethnowear].[DocumentPageTextSuggestions] ([Id]);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE [name] = N'IX_DocumentPageReviews_SourceTextSuggestionId'
      AND [object_id] = OBJECT_ID(N'ethnowear.DocumentPageReviews')
)
    EXEC(N'
        CREATE UNIQUE INDEX [IX_DocumentPageReviews_SourceTextSuggestionId]
        ON [ethnowear].[DocumentPageReviews] ([SourceTextSuggestionId])
        WHERE [SourceTextSuggestionId] IS NOT NULL;
    ');

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE [name] = N'UQ_DocumentPageQualityAssessments_ProcessingJobId'
      AND [object_id] = OBJECT_ID(N'ethnowear.DocumentPageQualityAssessments')
)
    CREATE UNIQUE INDEX [UQ_DocumentPageQualityAssessments_ProcessingJobId]
    ON [ethnowear].[DocumentPageQualityAssessments] ([ProcessingJobId])
    WHERE [ProcessingJobId] IS NOT NULL;

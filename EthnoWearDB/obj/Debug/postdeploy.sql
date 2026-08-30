SET NOCOUNT ON;

MERGE [ethnowear].[Roles] AS [target]
USING (VALUES
    (N'ADMINISTRATOR', N'Full administration, user management, and archive management'),
    (N'REVIEWER', N'Reviews and approves archive, OCR, and knowledge content'),
    (N'EDITOR', N'Creates and edits archive and document content')
) AS [source] ([Name], [Description])
ON [target].[Name] = [source].[Name]
WHEN MATCHED AND ISNULL([target].[Description], N'') <> [source].[Description]
    THEN UPDATE SET
        [Description] = [source].[Description],
        [UpdatedAt] = SYSUTCDATETIME()
WHEN NOT MATCHED BY TARGET
    THEN INSERT ([Name], [Description])
         VALUES ([source].[Name], [source].[Description]);

DECLARE @AdminUserId BIGINT;

SELECT @AdminUserId = [Id]
FROM [ethnowear].[Users]
WHERE [NormalizedUsername] = N'ADMIN';

IF @AdminUserId IS NULL
BEGIN
    INSERT INTO [ethnowear].[Users]
    (
        [Username],
        [PasswordHash],
        [MustChangePassword],
        [TemporaryPasswordExpiresAt],
        [Enabled]
    )
    VALUES
    (
        N'admin',
        N'{bcrypt}$2a$12$R0omxgE7ImNkEdnNCuNR8eM.W/ETuotDQbJAUTeAPkKqsfvcDX4eS',
        0,
        NULL,
        1
    );

    SET @AdminUserId = SCOPE_IDENTITY();

    INSERT INTO [ethnowear].[UserInfo]
    (
        [UserId],
        [FirstName],
        [LastName]
    )
    VALUES
    (
        @AdminUserId,
        N'Demo',
        N'Administrator'
    );
END;

DECLARE @AdministratorRoleId BIGINT;

SELECT @AdministratorRoleId = [Id]
FROM [ethnowear].[Roles]
WHERE [Name] = N'ADMINISTRATOR';

IF NOT EXISTS
(
    SELECT 1
    FROM [ethnowear].[UserRoles]
    WHERE [UserId] = @AdminUserId
      AND [RoleId] = @AdministratorRoleId
)
BEGIN
    INSERT INTO [ethnowear].[UserRoles]
    (
        [UserId],
        [RoleId]
    )
    VALUES
    (
        @AdminUserId,
        @AdministratorRoleId
    );
END;

SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
SET NOCOUNT ON;

UPDATE media
SET
    [Origin] = N'GENERATED',
    [RetentionPolicy] = N'KEEP_ORIGINAL_ONLY',
    [UpdatedAt] = SYSUTCDATETIME()
FROM [ethnowear].[MediaAssets] AS media
WHERE
    (
        media.[Origin] <> N'GENERATED'
        OR media.[RetentionPolicy] <> N'KEEP_ORIGINAL_ONLY'
    )
    AND EXISTS (
        SELECT 1
        FROM [ethnowear].[DocumentPageMedia] AS rendition
        WHERE rendition.[MediaAssetId] = media.[Id]
          AND rendition.[ProducingJobId] IS NOT NULL
          AND rendition.[RenditionType] IN (
              N'PDF_PAGE_RENDER',
              N'PREPROCESSED_OCR_INPUT',
              N'CROPPED',
              N'DESKEWED',
              N'BINARIZED',
              N'SEARCHABLE_PDF_PAGE',
              N'THUMBNAIL'
          )
    )
    AND NOT EXISTS (
        SELECT 1
        FROM [ethnowear].[DocumentPageMedia] AS preserved
        WHERE preserved.[MediaAssetId] = media.[Id]
          AND preserved.[RenditionType] IN (
              N'ORIGINAL_UPLOAD',
              N'PHONE_PHOTO',
              N'REPLACEMENT_SCAN'
          )
    );

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

IF COL_LENGTH(N'ethnowear.Documents', N'DefaultSourceReferenceId') IS NULL
BEGIN
    ALTER TABLE [ethnowear].[Documents]
    ADD [DefaultSourceReferenceId] BIGINT NULL;
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE [name] = N'FK_Documents_DefaultSourceReference'
)
BEGIN
    ALTER TABLE [ethnowear].[Documents]
    ADD CONSTRAINT [FK_Documents_DefaultSourceReference]
        FOREIGN KEY ([DefaultSourceReferenceId])
        REFERENCES [ethnowear].[SourceReference] ([Id]);
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE [name] = N'IX_Documents_DefaultSourceReferenceId'
      AND [object_id] = OBJECT_ID(N'ethnowear.Documents')
)
BEGIN
    CREATE INDEX [IX_Documents_DefaultSourceReferenceId]
    ON [ethnowear].[Documents] ([DefaultSourceReferenceId]);
END;

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

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

IF COL_LENGTH(N'ethnowear.DocumentPageTextSuggestions', N'RequiresReview') IS NULL
BEGIN
    ALTER TABLE [ethnowear].[DocumentPageTextSuggestions]
    ADD [RequiresReview] BIT NOT NULL
        CONSTRAINT [DF_DocumentPageTextSuggestions_RequiresReview]
        DEFAULT (1) WITH VALUES;
END;

IF COL_LENGTH(N'ethnowear.DocumentPageTextSuggestions', N'UncertainPassagesJson') IS NULL
BEGIN
    ALTER TABLE [ethnowear].[DocumentPageTextSuggestions]
    ADD [UncertainPassagesJson] NVARCHAR(4000) NOT NULL
        CONSTRAINT [DF_DocumentPageTextSuggestions_UncertainPassagesJson]
        DEFAULT (N'[]') WITH VALUES;
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE [name] = N'CK_DocumentPageTextSuggestions_UncertainPassagesJson'
      AND [parent_object_id] = OBJECT_ID(N'ethnowear.DocumentPageTextSuggestions')
)
BEGIN
    EXEC(N'
        ALTER TABLE [ethnowear].[DocumentPageTextSuggestions]
        ADD CONSTRAINT [CK_DocumentPageTextSuggestions_UncertainPassagesJson]
            CHECK (ISJSON([UncertainPassagesJson]) = 1);
    ');
END;

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

IF COL_LENGTH(
        N'ethnowear.DocumentPageReviews',
        N'SourceTextSuggestionIssueOrdinal'
   ) IS NULL
BEGIN
    ALTER TABLE [ethnowear].[DocumentPageReviews]
    ADD [SourceTextSuggestionIssueOrdinal] INT NULL;
END;

GO

IF EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE [name] = N'IX_DocumentPageReviews_SourceTextSuggestionId'
      AND [object_id] = OBJECT_ID(N'ethnowear.DocumentPageReviews')
)
BEGIN
    DROP INDEX [IX_DocumentPageReviews_SourceTextSuggestionId]
    ON [ethnowear].[DocumentPageReviews];
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE [name] = N'CK_DocumentPageReviews_SourceTextSuggestionIssueOrdinal'
      AND [parent_object_id] = OBJECT_ID(N'ethnowear.DocumentPageReviews')
)
BEGIN
    ALTER TABLE [ethnowear].[DocumentPageReviews]
    ADD CONSTRAINT [CK_DocumentPageReviews_SourceTextSuggestionIssueOrdinal]
        CHECK (
            [SourceTextSuggestionIssueOrdinal] IS NULL
            OR (
                [SourceTextSuggestionId] IS NOT NULL
                AND [SourceTextSuggestionIssueOrdinal] >= 0
            )
        );
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE [name] = N'UQ_DocumentPageReviews_WholeTextSuggestion'
      AND [object_id] = OBJECT_ID(N'ethnowear.DocumentPageReviews')
)
BEGIN
    CREATE UNIQUE INDEX [UQ_DocumentPageReviews_WholeTextSuggestion]
    ON [ethnowear].[DocumentPageReviews] ([SourceTextSuggestionId])
    WHERE [SourceTextSuggestionId] IS NOT NULL
      AND [SourceTextSuggestionIssueOrdinal] IS NULL;
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE [name] = N'UQ_DocumentPageReviews_TextSuggestionIssue'
      AND [object_id] = OBJECT_ID(N'ethnowear.DocumentPageReviews')
)
BEGIN
    CREATE UNIQUE INDEX [UQ_DocumentPageReviews_TextSuggestionIssue]
    ON [ethnowear].[DocumentPageReviews]
        ([SourceTextSuggestionId], [SourceTextSuggestionIssueOrdinal])
    WHERE [SourceTextSuggestionId] IS NOT NULL
      AND [SourceTextSuggestionIssueOrdinal] IS NOT NULL;
END;

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

    ALTER TABLE [ethnowear].[DocumentPageOcrResults]
    ADD [FigureExtractionMessage] NVARCHAR(500) NULL;

    ALTER TABLE [ethnowear].[DocumentPageOcrResults]
    ADD CONSTRAINT [CK_DocumentPageOcrResults_FigureExtractionState]
        CHECK ([FigureExtractionState] IN (
            N'NOT_REQUESTED', N'PENDING', N'COMPLETED', N'FAILED',
            N'SCHEDULING_FAILED', N'OUTDATED'
        ));

    ALTER TABLE [ethnowear].[DocumentPageOcrResults]
    ADD CONSTRAINT [CK_DocumentPageOcrResults_FigureExtractionMessage]
        CHECK ([FigureExtractionMessage] IS NULL
            OR LEN([FigureExtractionMessage]) BETWEEN 1 AND 500);
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

GO

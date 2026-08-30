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

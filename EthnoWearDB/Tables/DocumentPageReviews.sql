CREATE TABLE [ethnowear].[DocumentPageReviews]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [DocumentPageId] BIGINT NOT NULL,
    [SourceTextSuggestionId] BIGINT NULL,
    [SourceTextSuggestionIssueOrdinal] INT NULL,

    [ReviewAction] NVARCHAR(50) NOT NULL,
    [Reviewer] NVARCHAR(150) NOT NULL,
    [CorrectedTextSnapshot] NVARCHAR(MAX) NULL,
    [CorrectedTextHash] NVARCHAR(64) NULL,
    [PreviousReviewState] NVARCHAR(50) NULL,
    [NewReviewState] NVARCHAR(50) NOT NULL,
    [PreviousApprovalState] NVARCHAR(50) NULL,
    [NewApprovalState] NVARCHAR(50) NOT NULL,
    [Reason] NVARCHAR(1000) NULL,
    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentPageReviews_CreatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_DocumentPageReviews] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_DocumentPageReviews_DocumentPages]
        FOREIGN KEY ([DocumentPageId])
        REFERENCES [ethnowear].[DocumentPages] ([Id]),

    CONSTRAINT [FK_DocumentPageReviews_SourceTextSuggestions]
        FOREIGN KEY ([SourceTextSuggestionId])
        REFERENCES [ethnowear].[DocumentPageTextSuggestions] ([Id]),

    CONSTRAINT [CK_DocumentPageReviews_ReviewAction]
        CHECK ([ReviewAction] IN (
            N'REVIEW_STARTED',
            N'CORRECTED_TEXT_SAVED',
            N'RESET_FROM_CURRENT_OCR',
            N'APPROVED',
            N'REJECTED',
            N'APPROVAL_REVOKED'
        )),

    CONSTRAINT [CK_DocumentPageReviews_Reviewer]
        CHECK (LEN(LTRIM(RTRIM([Reviewer]))) > 0),

    CONSTRAINT [CK_DocumentPageReviews_SourceTextSuggestionIssueOrdinal]
        CHECK (
            [SourceTextSuggestionIssueOrdinal] IS NULL
            OR (
                [SourceTextSuggestionId] IS NOT NULL
                AND [SourceTextSuggestionIssueOrdinal] >= 0
            )
        ),

    CONSTRAINT [CK_DocumentPageReviews_CorrectedTextHash]
        CHECK (
            [CorrectedTextHash] IS NULL
            OR (
                LEN([CorrectedTextHash]) = 64
                AND [CorrectedTextHash] COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[^0-9a-f]%'
            )
        ),

    CONSTRAINT [CK_DocumentPageReviews_PreviousReviewState]
        CHECK (
            [PreviousReviewState] IS NULL
            OR [PreviousReviewState] IN (
                N'NOT_READY',
                N'REVIEW_REQUIRED',
                N'IN_REVIEW',
                N'APPROVED',
                N'REJECTED'
            )
        ),

    CONSTRAINT [CK_DocumentPageReviews_NewReviewState]
        CHECK ([NewReviewState] IN (
            N'NOT_READY',
            N'REVIEW_REQUIRED',
            N'IN_REVIEW',
            N'APPROVED',
            N'REJECTED'
        )),

    CONSTRAINT [CK_DocumentPageReviews_PreviousApprovalState]
        CHECK (
            [PreviousApprovalState] IS NULL
            OR [PreviousApprovalState] IN (
                N'NOT_REQUIRED',
                N'PENDING',
                N'APPROVED',
                N'REJECTED'
            )
        ),

    CONSTRAINT [CK_DocumentPageReviews_NewApprovalState]
        CHECK ([NewApprovalState] IN (
            N'NOT_REQUIRED',
            N'PENDING',
            N'APPROVED',
            N'REJECTED'
        ))
);

GO

CREATE INDEX [IX_DocumentPageReviews_DocumentPageId_CreatedAt]
ON [ethnowear].[DocumentPageReviews] ([DocumentPageId], [CreatedAt] DESC);

GO

CREATE INDEX [IX_DocumentPageReviews_Reviewer_CreatedAt]
ON [ethnowear].[DocumentPageReviews] ([Reviewer], [CreatedAt] DESC);

GO

CREATE UNIQUE INDEX [UQ_DocumentPageReviews_WholeTextSuggestion]
ON [ethnowear].[DocumentPageReviews] ([SourceTextSuggestionId])
WHERE [SourceTextSuggestionId] IS NOT NULL
  AND [SourceTextSuggestionIssueOrdinal] IS NULL;

GO

CREATE UNIQUE INDEX [UQ_DocumentPageReviews_TextSuggestionIssue]
ON [ethnowear].[DocumentPageReviews]
    ([SourceTextSuggestionId], [SourceTextSuggestionIssueOrdinal])
WHERE [SourceTextSuggestionId] IS NOT NULL
  AND [SourceTextSuggestionIssueOrdinal] IS NOT NULL;

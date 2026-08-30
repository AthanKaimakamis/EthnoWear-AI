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

GO

CREATE UNIQUE INDEX [UQ_DocumentPageTextSuggestions_ProcessingJobId]
ON [ethnowear].[DocumentPageTextSuggestions] ([ProcessingJobId]);

GO

CREATE INDEX [IX_DocumentPageTextSuggestions_Page_CreatedAt]
ON [ethnowear].[DocumentPageTextSuggestions]
    ([DocumentPageId], [CreatedAt] DESC, [Id] DESC);

GO

CREATE INDEX [IX_DocumentPageTextSuggestions_OcrResult_CreatedAt]
ON [ethnowear].[DocumentPageTextSuggestions]
    ([DocumentPageOcrResultId], [CreatedAt] DESC, [Id] DESC);

GO

CREATE INDEX [IX_DocumentPageTextSuggestions_DocumentPageMediaId]
ON [ethnowear].[DocumentPageTextSuggestions] ([DocumentPageMediaId]);

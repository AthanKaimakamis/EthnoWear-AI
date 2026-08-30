CREATE TABLE [ethnowear].[DocumentPageOcrResults]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [DocumentPageId] BIGINT NOT NULL,
    [DocumentPageMediaId] BIGINT NULL,
    [ProcessingJobId] BIGINT NULL,

    [RawText] NVARCHAR(MAX) NOT NULL,
    [OcrEngine] NVARCHAR(100) NOT NULL,
    [OcrEngineVersion] NVARCHAR(100) NULL,
    [OcrLanguage] NVARCHAR(20) NULL,
    [OcrConfidence] DECIMAL(5,4) NULL,
    [ParametersJson] NVARCHAR(MAX) NULL,
    [StructuredOutputJson] NVARCHAR(MAX) NULL,
    [IsCurrent] BIT NOT NULL,
    [FigureExtractionState] NVARCHAR(50) NOT NULL
        CONSTRAINT [DF_DocumentPageOcrResults_FigureExtractionState]
        DEFAULT N'NOT_REQUESTED',
    [FigureExtractionMessage] NVARCHAR(500) NULL,

    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentPageOcrResults_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentPageOcrResults_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_DocumentPageOcrResults] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_DocumentPageOcrResults_DocumentPages]
        FOREIGN KEY ([DocumentPageId])
        REFERENCES [ethnowear].[DocumentPages] ([Id]),

    CONSTRAINT [FK_DocumentPageOcrResults_DocumentPageMedia]
        FOREIGN KEY ([DocumentPageMediaId])
        REFERENCES [ethnowear].[DocumentPageMedia] ([Id]),

    CONSTRAINT [FK_DocumentPageOcrResults_ProcessingJob]
        FOREIGN KEY ([ProcessingJobId])
        REFERENCES [ethnowear].[DocumentProcessingJobs] ([Id]),

    CONSTRAINT [CK_DocumentPageOcrResults_OcrEngine]
        CHECK (LEN(LTRIM(RTRIM([OcrEngine]))) > 0),

    CONSTRAINT [CK_DocumentPageOcrResults_OcrConfidence]
        CHECK ([OcrConfidence] IS NULL OR [OcrConfidence] BETWEEN 0 AND 1),

    CONSTRAINT [CK_DocumentPageOcrResults_ParametersJson]
        CHECK ([ParametersJson] IS NULL OR ISJSON([ParametersJson]) = 1),

    CONSTRAINT [CK_DocumentPageOcrResults_StructuredOutputJson]
        CHECK ([StructuredOutputJson] IS NULL OR ISJSON([StructuredOutputJson]) = 1),

    CONSTRAINT [CK_DocumentPageOcrResults_FigureExtractionState]
        CHECK ([FigureExtractionState] IN (
            N'NOT_REQUESTED',
            N'PENDING',
            N'COMPLETED',
            N'FAILED',
            N'SCHEDULING_FAILED',
            N'OUTDATED'
        )),

    CONSTRAINT [CK_DocumentPageOcrResults_FigureExtractionMessage]
        CHECK (
            [FigureExtractionMessage] IS NULL
            OR LEN([FigureExtractionMessage]) BETWEEN 1 AND 500
        )
);

GO

CREATE UNIQUE INDEX [UQ_DocumentPageOcrResults_Current]
ON [ethnowear].[DocumentPageOcrResults] ([DocumentPageId])
WHERE [IsCurrent] = 1;

GO

CREATE INDEX [IX_DocumentPageOcrResults_DocumentPageId_CreatedAt]
ON [ethnowear].[DocumentPageOcrResults] ([DocumentPageId], [CreatedAt] DESC);

GO

CREATE INDEX [IX_DocumentPageOcrResults_DocumentPageMediaId]
ON [ethnowear].[DocumentPageOcrResults] ([DocumentPageMediaId])
WHERE [DocumentPageMediaId] IS NOT NULL;

GO

CREATE INDEX [IX_DocumentPageOcrResults_ProcessingJobId]
ON [ethnowear].[DocumentPageOcrResults] ([ProcessingJobId])
WHERE [ProcessingJobId] IS NOT NULL;

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
        CONSTRAINT [DF_DocumentPageFigureCandidates_CreatedAt]
        DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_DocumentPageFigureCandidates]
        PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_DocumentPageFigureCandidates_OcrResult]
        FOREIGN KEY ([DocumentPageOcrResultId])
        REFERENCES [ethnowear].[DocumentPageOcrResults] ([Id]),

    CONSTRAINT [FK_DocumentPageFigureCandidates_PageMedia]
        FOREIGN KEY ([DocumentPageMediaId])
        REFERENCES [ethnowear].[DocumentPageMedia] ([Id]),

    CONSTRAINT [UQ_DocumentPageFigureCandidates_Result_Ordinal]
        UNIQUE ([DocumentPageOcrResultId], [CandidateOrdinal]),

    CONSTRAINT [CK_DocumentPageFigureCandidates_Ordinal]
        CHECK ([CandidateOrdinal] > 0),

    CONSTRAINT [CK_DocumentPageFigureCandidates_Coordinates]
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

    CONSTRAINT [CK_DocumentPageFigureCandidates_Caption]
        CHECK (
            [RawCaptionText] IS NULL
            OR LEN(LTRIM(RTRIM([RawCaptionText]))) BETWEEN 1 AND 2000
        ),

    CONSTRAINT [CK_DocumentPageFigureCandidates_Confidence]
        CHECK (
            [DetectionConfidence] IS NULL
            OR [DetectionConfidence] BETWEEN 0 AND 1
        )
);

GO

CREATE INDEX [IX_DocumentPageFigureCandidates_PageMedia]
ON [ethnowear].[DocumentPageFigureCandidates] ([DocumentPageMediaId]);

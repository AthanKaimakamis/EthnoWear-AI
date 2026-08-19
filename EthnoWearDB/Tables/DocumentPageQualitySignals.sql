CREATE TABLE [ethnowear].[DocumentPageQualitySignals]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [AssessmentId] BIGINT NOT NULL,

    [SignalType] NVARCHAR(100) NOT NULL,
    [SignalOrdinal] INT NOT NULL,
    [SignalValueDecimal] DECIMAL(9,6) NULL,
    [SignalValueText] NVARCHAR(500) NULL,
    [SignalValueJson] NVARCHAR(MAX) NULL,
    [Severity] NVARCHAR(50) NOT NULL,
    [Weight] DECIMAL(5,4) NULL,
    [Message] NVARCHAR(1000) NULL,

    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentPageQualitySignals_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentPageQualitySignals_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_DocumentPageQualitySignals] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_DocumentPageQualitySignals_Assessment]
        FOREIGN KEY ([AssessmentId])
        REFERENCES [ethnowear].[DocumentPageQualityAssessments] ([Id]),

    CONSTRAINT [UQ_DocumentPageQualitySignals_Assessment_Type_Ordinal]
        UNIQUE ([AssessmentId], [SignalType], [SignalOrdinal]),

    CONSTRAINT [CK_DocumentPageQualitySignals_Severity]
        CHECK ([Severity] IN (
            N'INFO',
            N'WARNING',
            N'ERROR'
        )),

    CONSTRAINT [CK_DocumentPageQualitySignals_SignalOrdinal]
        CHECK ([SignalOrdinal] > 0),

    CONSTRAINT [CK_DocumentPageQualitySignals_ValuePresent]
        CHECK (
            [SignalValueDecimal] IS NOT NULL
            OR [SignalValueText] IS NOT NULL
            OR [SignalValueJson] IS NOT NULL
            OR [Message] IS NOT NULL
        ),

    CONSTRAINT [CK_DocumentPageQualitySignals_Weight]
        CHECK ([Weight] IS NULL OR [Weight] BETWEEN 0 AND 1),

    CONSTRAINT [CK_DocumentPageQualitySignals_ValueJson]
        CHECK ([SignalValueJson] IS NULL OR ISJSON([SignalValueJson]) = 1)
);

GO

CREATE INDEX [IX_DocumentPageQualitySignals_AssessmentId]
ON [ethnowear].[DocumentPageQualitySignals] ([AssessmentId]);

GO

CREATE INDEX [IX_DocumentPageQualitySignals_SignalType]
ON [ethnowear].[DocumentPageQualitySignals] ([SignalType]);

GO

CREATE INDEX [IX_DocumentPageQualitySignals_Severity]
ON [ethnowear].[DocumentPageQualitySignals] ([Severity]);

CREATE TABLE [ethnowear].[DocumentPageQualityAssessments]
(
    [Id] BIGINT IDENTITY(1,1) NOT NULL,
    [DocumentPageId] BIGINT NOT NULL,
    [DocumentPageMediaId] BIGINT NULL,
    [ProcessingJobId] BIGINT NULL,
    [DocumentPageOcrResultId] BIGINT NULL,

    [AssessmentType] NVARCHAR(50) NOT NULL,
    [AssessorType] NVARCHAR(50) NOT NULL,
    [AssessorName] NVARCHAR(100) NULL,
    [AssessorVersion] NVARCHAR(100) NULL,
    [ScoreVersion] NVARCHAR(50) NOT NULL,
    [QualityStatus] NVARCHAR(50) NOT NULL,
    [OverallScore] DECIMAL(5,4) NULL,
    [Summary] NVARCHAR(1000) NULL,
    [Limitations] NVARCHAR(1000) NULL,
    [IsCurrent] BIT NOT NULL,

    [CreatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentPageQualityAssessments_CreatedAt] DEFAULT SYSUTCDATETIME(),
    [UpdatedAt] DATETIME2(7) NOT NULL CONSTRAINT [DF_DocumentPageQualityAssessments_UpdatedAt] DEFAULT SYSUTCDATETIME(),

    CONSTRAINT [PK_DocumentPageQualityAssessments] PRIMARY KEY CLUSTERED ([Id]),

    CONSTRAINT [FK_DocumentPageQualityAssessments_DocumentPages]
        FOREIGN KEY ([DocumentPageId])
        REFERENCES [ethnowear].[DocumentPages] ([Id]),

    CONSTRAINT [FK_DocumentPageQualityAssessments_DocumentPageMedia]
        FOREIGN KEY ([DocumentPageMediaId])
        REFERENCES [ethnowear].[DocumentPageMedia] ([Id]),

    CONSTRAINT [FK_DocumentPageQualityAssessments_ProcessingJob]
        FOREIGN KEY ([ProcessingJobId])
        REFERENCES [ethnowear].[DocumentProcessingJobs] ([Id]),

    CONSTRAINT [FK_DocumentPageQualityAssessments_OcrResult]
        FOREIGN KEY ([DocumentPageOcrResultId])
        REFERENCES [ethnowear].[DocumentPageOcrResults] ([Id]),

    CONSTRAINT [CK_DocumentPageQualityAssessments_AssessmentType]
        CHECK ([AssessmentType] IN (
            N'OCR_READABILITY',
            N'IMAGE_QUALITY',
            N'SOURCE_MATCH',
            N'VISION_TEXT_COMPARISON',
            N'COMBINED_OCR_QUALITY'
        )),

    CONSTRAINT [CK_DocumentPageQualityAssessments_AssessorType]
        CHECK ([AssessorType] IN (
            N'DETERMINISTIC',
            N'OCR_ENGINE',
            N'VISION_MODEL',
            N'HUMAN'
        )),

    CONSTRAINT [CK_DocumentPageQualityAssessments_QualityStatus]
        CHECK ([QualityStatus] IN (
            N'HIGH_QUALITY',
            N'MINOR_REVIEW',
            N'REVIEW_REQUIRED',
            N'POOR_QUALITY',
            N'PROCESSING_FAILED',
            N'INCOMPLETE'
        )),

    CONSTRAINT [CK_DocumentPageQualityAssessments_OverallScore]
        CHECK ([OverallScore] IS NULL OR [OverallScore] BETWEEN 0 AND 1)
);

GO

CREATE UNIQUE INDEX [UQ_DocumentPageQualityAssessments_Current]
ON [ethnowear].[DocumentPageQualityAssessments] ([DocumentPageId], [DocumentPageMediaId], [AssessmentType])
WHERE [IsCurrent] = 1;

GO

CREATE INDEX [IX_DocumentPageQualityAssessments_DocumentPageId]
ON [ethnowear].[DocumentPageQualityAssessments] ([DocumentPageId]);

GO

CREATE INDEX [IX_DocumentPageQualityAssessments_DocumentPageMediaId]
ON [ethnowear].[DocumentPageQualityAssessments] ([DocumentPageMediaId])
WHERE [DocumentPageMediaId] IS NOT NULL;

GO

CREATE INDEX [IX_DocumentPageQualityAssessments_ProcessingJobId]
ON [ethnowear].[DocumentPageQualityAssessments] ([ProcessingJobId])
WHERE [ProcessingJobId] IS NOT NULL;

GO

CREATE UNIQUE INDEX [UQ_DocumentPageQualityAssessments_ProcessingJobId]
ON [ethnowear].[DocumentPageQualityAssessments] ([ProcessingJobId])
WHERE [ProcessingJobId] IS NOT NULL;

GO

CREATE INDEX [IX_DocumentPageQualityAssessments_OcrResultId]
ON [ethnowear].[DocumentPageQualityAssessments] ([DocumentPageOcrResultId])
WHERE [DocumentPageOcrResultId] IS NOT NULL;

GO

CREATE INDEX [IX_DocumentPageQualityAssessments_QualityStatus_IsCurrent]
ON [ethnowear].[DocumentPageQualityAssessments] ([QualityStatus], [IsCurrent]);
